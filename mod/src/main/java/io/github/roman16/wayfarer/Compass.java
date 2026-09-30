package io.github.roman16.wayfarer;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Compass {
    public static final ResourceLocation STRONGHOLD = ResourceLocation.withDefaultNamespace("stronghold");

    private static final ResourceLocation GRAVESTONE = ResourceLocation.fromNamespaceAndPath("gravestone", "gravestone");
    private static final int GRAVE_REACH = 2;
    private static final int SEARCH_RADIUS = 100;
    private static final Map<UUID, Bearing> BEARINGS = new HashMap<>();

    private Compass() {}

    public record Aim(ResourceLocation target, BlockPos pos) {
        static final Aim NONE = new Aim(CompassPayload.NOTHING, null);
    }

    private static final class Bearing {
        BlockPos searchedFrom;
        Aim aim = Aim.NONE;
        BlockPos trailStart;
        int heat;

        void aim(Aim next) {
            if (!next.equals(aim)) {
                heat = 0;
                trailStart = null;
            }
            aim = next;
        }
    }

    public static void forget(ServerPlayer player) {
        BEARINGS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BEARINGS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % Balance.INSTANCE.compass().refreshTicks() == 0) {
            PacketDistributor.sendToPlayer(player, reading(player));
        }
    }

    private static CompassPayload reading(ServerPlayer player) {
        List<ResourceLocation> claimed = Journey.claimed(player);
        Balance.Compass balance = Balance.INSTANCE.compass();
        Bearing bearing = BEARINGS.computeIfAbsent(player.getUUID(), key -> new Bearing());
        BlockPos pos = player.blockPosition();
        boolean shown = player.level().dimension() == Level.OVERWORLD && Journey.awake(player);
        if (shown && (bearing.searchedFrom == null || horizontal(bearing.searchedFrom, pos) > balance.researchAfterBlocks())) {
            bearing.searchedFrom = pos;
            bearing.aim(search(player.serverLevel(), pos, claimed));
        }
        CompassEvent event = NeoForge.EVENT_BUS.post(new CompassEvent(player, shown, bearing.aim, markers(player)));
        if (!event.shown()) {
            return CompassPayload.hidden(claimed);
        }
        bearing.aim(event.aim());
        List<CompassPayload.Marker> markers = List.copyOf(event.markers());
        if (Gaze.airborne(player)) {
            return new CompassPayload(CompassPayload.Mode.AIR, CompassPayload.NOTHING, 0, 0, 0, 0, claimed, markers);
        }
        Aim aim = bearing.aim;
        if (aim.pos() == null) {
            return new CompassPayload(CompassPayload.Mode.LOST, CompassPayload.NOTHING, 0, 0, 0, 0, claimed, markers);
        }
        int distance = (int) horizontal(pos, aim.pos());
        if (distance <= balance.exactBlocks()) {
            return new CompassPayload(CompassPayload.Mode.NEAR, aim.target(), 0, distance, aim.pos().getX(), aim.pos().getZ(), claimed, markers);
        }
        if (bearing.trailStart == null) {
            bearing.trailStart = pos;
        }
        double moved = horizontal(bearing.trailStart, pos);
        if (moved >= balance.trailBlocks()) {
            bearing.heat = heat(horizontal(bearing.trailStart, aim.pos()) - distance, moved);
            bearing.trailStart = pos;
        }
        int rounding = balance.farRoundingBlocks();
        int rounded = Math.max(rounding, Math.round((float) distance / rounding) * rounding);
        return new CompassPayload(CompassPayload.Mode.FAR, aim.target(), bearing.heat, rounded, 0, 0, claimed, markers);
    }

    private static List<CompassPayload.Marker> markers(ServerPlayer player) {
        List<CompassPayload.Marker> markers = new ArrayList<>();
        for (ServerPlayer member : Teams.onlineMembers(player)) {
            if (member != player && member.level() == player.level()) {
                markers.add(new CompassPayload.Marker(
                        CompassPayload.Kind.TEAMMATE, member.getGameProfile().getName(), member.getBlockX(), member.getBlockZ()));
            }
        }
        grave(player).ifPresent(grave -> markers.add(new CompassPayload.Marker(CompassPayload.Kind.GRAVE, "", grave.getX(), grave.getZ())));
        return markers;
    }

    private static Optional<BlockPos> grave(ServerPlayer player) {
        Optional<GlobalPos> death = player.getLastDeathLocation();
        if (death.isEmpty() || death.get().dimension() != player.level().dimension()) {
            return Optional.empty();
        }
        BlockPos pos = death.get().pos();
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) {
            return Optional.of(pos);
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-GRAVE_REACH, -GRAVE_REACH, -GRAVE_REACH), pos.offset(GRAVE_REACH, GRAVE_REACH, GRAVE_REACH))) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(near).getBlock()).equals(GRAVESTONE)) {
                return Optional.of(near.immutable());
            }
        }
        return Optional.empty();
    }

    public static int heat(double closerBy, double moved) {
        double ratio = closerBy / moved;
        if (ratio > 0.7) {
            return 2;
        }
        if (ratio >= 0) {
            return 1;
        }
        if (ratio > -0.7) {
            return -1;
        }
        return -2;
    }

    private static Aim search(ServerLevel level, BlockPos pos, List<ResourceLocation> claimed) {
        List<Holder<Structure>> open = unclaimed(level, Shrines.TIER_ONE, claimed);
        if (open.isEmpty()) {
            open = unclaimed(level, Shrines.TIER_TWO, claimed);
        }
        if (open.isEmpty()) {
            return new Aim(STRONGHOLD, level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, pos, SEARCH_RADIUS, false));
        }
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(open), pos, SEARCH_RADIUS, false);
        return found == null ? Aim.NONE : new Aim(Shrines.key(found.getSecond()), entrance(level, found.getFirst(), found.getSecond()));
    }

    private static BlockPos entrance(ServerLevel level, BlockPos located, Holder<Structure> structure) {
        if (!(structure.value() instanceof ShrineStructure shrine)) {
            return located;
        }
        StructureStart start = level.getChunk(SectionPos.blockToSectionCoord(located.getX()), SectionPos.blockToSectionCoord(located.getZ()), ChunkStatus.STRUCTURE_STARTS)
                .getStartForStructure(shrine);
        if (start == null || !start.isValid()) {
            return located;
        }
        return start.getPieces().stream()
                .filter(piece -> piece instanceof EntrancePiece)
                .findFirst()
                .map(piece -> piece.getBoundingBox().getCenter())
                .orElse(located);
    }

    private static List<Holder<Structure>> unclaimed(ServerLevel level, TagKey<Structure> tier, List<ResourceLocation> claimed) {
        return Shrines.tier(level, tier).stream().filter(shrine -> !claimed.contains(Shrines.key(shrine))).toList();
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
