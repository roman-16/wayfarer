package io.github.roman16.wayfarer;

import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Compass {
    public static final ResourceLocation STRONGHOLD = ResourceLocation.withDefaultNamespace("stronghold");

    private static final int SEARCH_RADIUS = 100;
    private static final Map<UUID, Bearing> BEARINGS = new HashMap<>();

    private Compass() {}

    private static final class Bearing {
        BlockPos searchedFrom;
        ResourceLocation target = CompassPayload.NOTHING;
        BlockPos targetPos;
        BlockPos trailStart;
        int heat;
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
        Set<ResourceLocation> claimed = Journey.claimed(player);
        if (player.level().dimension() != Level.OVERWORLD) {
            return CompassPayload.silent(claimed.size());
        }
        if (Gaze.airborne(player)) {
            return new CompassPayload(CompassPayload.Mode.AIR, CompassPayload.NOTHING, 0, 0, 0, 0, claimed.size());
        }
        Balance.Compass balance = Balance.INSTANCE.compass();
        Bearing bearing = BEARINGS.computeIfAbsent(player.getUUID(), key -> new Bearing());
        BlockPos pos = player.blockPosition();
        if (bearing.searchedFrom == null || horizontal(bearing.searchedFrom, pos) > balance.researchAfterBlocks()) {
            search(player.serverLevel(), pos, claimed, bearing);
        }
        if (bearing.targetPos == null) {
            return CompassPayload.silent(claimed.size());
        }
        int distance = (int) horizontal(pos, bearing.targetPos);
        if (distance <= balance.exactBlocks()) {
            return new CompassPayload(CompassPayload.Mode.NEAR, bearing.target, 0, distance, bearing.targetPos.getX(), bearing.targetPos.getZ(), claimed.size());
        }
        if (bearing.trailStart == null) {
            bearing.trailStart = pos;
        }
        double moved = horizontal(bearing.trailStart, pos);
        if (moved >= balance.trailBlocks()) {
            bearing.heat = heat(horizontal(bearing.trailStart, bearing.targetPos) - distance, moved);
            bearing.trailStart = pos;
        }
        int rounding = balance.farRoundingBlocks();
        int rounded = Math.max(rounding, Math.round((float) distance / rounding) * rounding);
        return new CompassPayload(CompassPayload.Mode.FAR, bearing.target, bearing.heat, rounded, 0, 0, claimed.size());
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

    private static void search(ServerLevel level, BlockPos pos, Set<ResourceLocation> claimed, Bearing bearing) {
        List<Holder<Structure>> open = unclaimed(level, Shrines.TIER_ONE, claimed);
        if (open.isEmpty()) {
            open = unclaimed(level, Shrines.TIER_TWO, claimed);
        }
        ResourceLocation target;
        BlockPos targetPos;
        if (open.isEmpty()) {
            target = STRONGHOLD;
            targetPos = level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, pos, SEARCH_RADIUS, false);
        } else {
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(open), pos, SEARCH_RADIUS, false);
            target = found == null ? CompassPayload.NOTHING : Shrines.key(found.getSecond());
            targetPos = found == null ? null : found.getFirst();
        }
        if (!target.equals(bearing.target) || targetPos == null || !targetPos.equals(bearing.targetPos)) {
            bearing.heat = 0;
            bearing.trailStart = null;
        }
        bearing.searchedFrom = pos;
        bearing.target = target;
        bearing.targetPos = targetPos;
    }

    private static List<Holder<Structure>> unclaimed(ServerLevel level, net.minecraft.tags.TagKey<Structure> tier, Set<ResourceLocation> claimed) {
        return Shrines.tier(level, tier).stream().filter(shrine -> !claimed.contains(Shrines.key(shrine))).toList();
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
