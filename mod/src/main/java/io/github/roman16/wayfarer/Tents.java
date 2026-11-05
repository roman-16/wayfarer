package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Talk;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

public final class Tents {
    private static final String LIT = "wayfarer_lit";
    private static final int LANTERN_IN = 2;
    private static final int LIT_REACH = 8;
    private static final int BED_FROM_DOOR = 2;
    private static final int WALL_WEIGHT = 16;
    private static final String NAMESPACE = "nomadictents";
    private static final String SPAWN_DIMENSION = "spawn_dim";
    private static final String SPAWN_POINT = "spawnpoint";
    private static final String SPAWN_ROTATION = "spawn_rot";
    private static final String SLEPT = "slept";
    private static final ResourceLocation NOWHERE = Wayfarer.id("nowhere");
    private static final int DOOR_SEARCH_CHUNKS = 1;

    private Tents() {}

    private static final class Lit extends SavedData {
        private boolean done;

        private Lit(boolean done) {
            this.done = done;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            return tag;
        }
    }

    public static boolean isTent(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(NAMESPACE);
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (isTent(event.getLevel().dimension()) && event.getBreaker() instanceof ServerPlayer player) {
            event.getDrops().forEach(drop -> Handover.give(player, drop.getItem()));
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isTent(player.level().dimension())) {
            AdvancementHolder advancement = player.server.getAdvancements().get(NOWHERE);
            if (advancement != null) {
                player.getAdvancements().award(advancement, SLEPT);
            }
            Firsts.first(player, "tent");
        }
    }

    public static List<ServerPlayer> sleepers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().filter(player -> player.level().dimension() == Level.OVERWORLD || isTent(player.level().dimension()))
                .toList();
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel tent) || !isTent(tent.dimension())) {
            return;
        }
        MinecraftServer server = tent.getServer();
        ServerLevel overworld = server.overworld();
        long next = overworld.getDayTime() + Clock.DAY;
        long morning = next - next % Clock.DAY;
        overworld.setDayTime(morning);
        event.setTimeAddition(morning);
        for (ServerLevel level : server.getAllLevels()) {
            if (level != tent && (level == overworld || isTent(level.dimension()))) {
                if (level != overworld) {
                    level.setDayTime(morning);
                }
                level.players().stream().filter(ServerPlayer::isSleeping).forEach(player -> player.stopSleepInBed(false, false));
                level.updateSleepingPlayerList();
            }
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!isTent(event.getDimension()) || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }
        if (Nights.tentsShut(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("wayfarer.night.tent").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onArrive(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isTent(event.getTo())) {
            light(player.serverLevel(), player, player.blockPosition(), player.getDirection());
        }
    }

    private static void light(ServerLevel level, ServerPlayer player, BlockPos door, Direction inward) {
        Lit lit = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> new Lit(false), (tag, registries) -> new Lit(true), null), LIT);
        if (lit.done) {
            return;
        }
        lit.done = true;
        lit.setDirty();
        furnish(level, player, door);
        if (BlockPos.betweenClosedStream(door.offset(-LIT_REACH, -1, -LIT_REACH), door.offset(LIT_REACH, LIT_REACH, LIT_REACH))
                .anyMatch(pos -> level.getBlockState(pos).getLightEmission(level, pos) > 0)) {
            return;
        }
        BlockPos under = door.relative(inward, LANTERN_IN);
        for (int up = 1; up <= LIT_REACH; up++) {
            BlockPos hang = under.above(up);
            if (!level.getBlockState(hang.above()).isAir() && level.getBlockState(hang).isAir()) {
                level.setBlock(hang, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Block.UPDATE_ALL);
                return;
            }
        }
    }

    private static void furnish(ServerLevel level, ServerPlayer player, BlockPos door) {
        int slot = IntStream.range(0, player.getInventory().items.size()).filter(index -> player.getInventory().items.get(index).is(ItemTags.BEDS)).findFirst().orElse(-1);
        if (slot < 0 || BlockPos.betweenClosedStream(door.offset(-LIT_REACH, -1, -LIT_REACH), door.offset(LIT_REACH, LIT_REACH, LIT_REACH))
                .anyMatch(pos -> level.getBlockState(pos).is(BlockTags.BEDS))) {
            return;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof BedBlock bed)) {
            return;
        }
        BlockPos.betweenClosedStream(door.offset(-LIT_REACH, -1, -LIT_REACH), door.offset(LIT_REACH, 1, LIT_REACH)).map(BlockPos::immutable)
                .flatMap(foot -> Direction.Plane.HORIZONTAL.stream().map(way -> new Laid(foot, way)))
                .filter(laid -> fits(level, laid.foot()) && fits(level, laid.foot().relative(laid.way())) && !laid.foot().closerThan(door, BED_FROM_DOOR)
                        && !laid.foot().relative(laid.way()).closerThan(door, BED_FROM_DOOR))
                .max(Comparator.comparingDouble(laid -> walls(level, laid) * WALL_WEIGHT + laid.foot().distSqr(door)))
                .ifPresent(laid -> {
                    BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, laid.way());
                    level.setBlock(laid.foot(), foot, Block.UPDATE_ALL);
                    level.setBlock(laid.foot().relative(laid.way()), foot.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
                    stack.shrink(1);
                });
    }

    private record Laid(BlockPos foot, Direction way) {}

    private static boolean fits(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && Direction.Plane.HORIZONTAL.stream().noneMatch(side -> level.getBlockState(pos.relative(side)).is(BlockTags.CAMPFIRES));
    }

    private static long walls(ServerLevel level, Laid laid) {
        return Stream.of(laid.foot(), laid.foot().relative(laid.way())).flatMap(pos -> Direction.Plane.HORIZONTAL.stream().map(pos::relative))
                .filter(pos -> !level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(BlockTags.BEDS)).count();
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && event.getLevel() instanceof ServerLevel level && isTent(level.dimension())) {
            event.setCanceled(true);
        }
    }

    public static boolean pitchedNear(ServerLevel level, BlockPos fire, int blocks) {
        for (int x = (fire.getX() - blocks) >> 4; x <= (fire.getX() + blocks) >> 4; x++) {
            for (int z = (fire.getZ() - blocks) >> 4; z <= (fire.getZ() + blocks) >> 4; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null && chunk.getBlockEntities().values().stream()
                        .anyMatch(entity -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(NAMESPACE)
                                && entity.getBlockPos().closerThan(fire, blocks + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Outside(ServerLevel level, Vec3 at, float yaw) {}

    public static Optional<Outside> outside(ServerPlayer player) {
        ServerLevel tent = player.serverLevel();
        if (!isTent(tent.dimension())) {
            return Optional.empty();
        }
        return doors(tent, player.chunkPosition())
                .min(Comparator.comparingDouble(entity -> entity.getBlockPos().distSqr(player.blockPosition())))
                .map(entity -> entity.saveWithoutMetadata(tent.registryAccess()))
                .flatMap(door -> Optional.ofNullable(player.server.getLevel(
                                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(door.getString(SPAWN_DIMENSION)))))
                        .map(level -> {
                            CompoundTag point = door.getCompound(SPAWN_POINT);
                            return new Outside(level, new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")), door.getFloat(SPAWN_ROTATION));
                        }));
    }

    public static void eject(ServerPlayer player) {
        out(player);
        Talk.tell(player, Component.translatable("wayfarer.night.ejected"));
    }

    public static void out(ServerPlayer player) {
        Optional<Outside> outside = outside(player);
        if (outside.isPresent()) {
            Vec3 at = outside.get().at();
            player.teleportTo(outside.get().level(), at.x, at.y, at.z, outside.get().yaw(), player.getXRot());
        } else {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }

    private static Stream<BlockEntity> doors(ServerLevel tent, ChunkPos center) {
        Stream.Builder<BlockEntity> doors = Stream.builder();
        for (int dx = -DOOR_SEARCH_CHUNKS; dx <= DOOR_SEARCH_CHUNKS; dx++) {
            for (int dz = -DOOR_SEARCH_CHUNKS; dz <= DOOR_SEARCH_CHUNKS; dz++) {
                LevelChunk chunk = tent.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                chunk.getBlockEntities().values().stream()
                        .filter(entity -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals(NAMESPACE))
                        .filter(entity -> entity.saveWithoutMetadata(tent.registryAccess()).contains(SPAWN_DIMENSION))
                        .forEach(doors);
            }
        }
        return doors.build();
    }
}
