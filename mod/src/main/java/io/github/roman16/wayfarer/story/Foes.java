package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Shine;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Foes {
    private static final int CHECK_TICKS = 20;
    private static final Bundled<Map<ResourceLocation, Integer>> ACTS = Bundled.json("/wayfarer/foes.json", json -> json.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(entry -> ResourceLocation.parse(entry.getKey()), entry -> entry.getValue().getAsInt())));
    private static final Set<MobSpawnType> WILD = Set.of(MobSpawnType.CHUNK_GENERATION, MobSpawnType.JOCKEY, MobSpawnType.NATURAL, MobSpawnType.PATROL,
            MobSpawnType.REINFORCEMENT, MobSpawnType.SPAWNER, MobSpawnType.STRUCTURE, MobSpawnType.TRIAL_SPAWNER);
    private static volatile int act = Integer.MAX_VALUE;

    private Foes() {}

    public static boolean arrived(ResourceLocation creature, int now) {
        return now >= ACTS.get().getOrDefault(creature, 0);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS == 0) {
            act = Engine.running(event.getServer()) ? Shine.act(event.getServer()) : Integer.MAX_VALUE;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        act = Integer.MAX_VALUE;
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (WILD.contains(event.getSpawnType()) && (!arrived(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()), act) || owned(event.getLevel().getLevel(),
                event.getEntity().blockPosition()))) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        boolean wild = WILD.contains(event.getSpawnType())
                || event.getSpawnType() == MobSpawnType.EVENT && event.getEntity() instanceof Raider raider && raider.getCurrentRaid() != null;
        if (wild && (!arrived(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()), act) || owned(event.getEntity().level(),
                event.getEntity().blockPosition()))) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean owned(Level level, BlockPos pos) {
        return Memories.dream(level) || Hold.ground(level, pos);
    }
}
