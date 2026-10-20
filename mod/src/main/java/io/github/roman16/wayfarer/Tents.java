package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Talk;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

public final class Tents {
    private static final String NAMESPACE = "nomadictents";
    private static final String SPAWN_DIMENSION = "spawn_dim";
    private static final String SPAWN_POINT = "spawnpoint";
    private static final String SPAWN_ROTATION = "spawn_rot";
    private static final String SLEPT = "slept";
    private static final ResourceLocation NOWHERE = Wayfarer.id("nowhere");
    private static final int DOOR_SEARCH_CHUNKS = 1;

    private Tents() {}

    public static boolean isTent(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(NAMESPACE);
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
