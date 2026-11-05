package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Ground;
import io.github.roman16.wayfarer.story.Hold;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.smileycorp.hordes.common.event.HordeEndEvent;
import net.smileycorp.hordes.common.event.HordeFindSpawnPosEvent;
import net.smileycorp.hordes.common.event.HordeSpawnEntityEvent;
import net.smileycorp.hordes.common.event.HordeStartEvent;
import net.smileycorp.hordes.common.event.HordeStartWaveEvent;
import net.smileycorp.hordes.hordeevent.capability.HordeEvent;
import net.smileycorp.hordes.hordeevent.capability.HordeSavedData;

public final class Hordes {
    private static final Bundled<Map<EntityType<?>, EntityType<?>>> OVERWORLD = Bundled.json("/wayfarer/creature_swaps.json",
            json -> json.entrySet().stream().collect(Collectors.toUnmodifiableMap(entry -> BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entry.getKey())),
                    entry -> BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entry.getValue().getAsString())))));

    private static final int GONE_BLOCKS = 128;
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, Integer> WAVES = new HashMap<>();

    private Hordes() {}

    static void stop(ServerPlayer player) {
        HordeEvent horde = HordeSavedData.getData(player.serverLevel()).getEvent(player);
        if (horde != null && horde.isActive()) {
            horde.stopEvent(player, true);
        }
    }

    @SubscribeEvent
    public static void onSpawn(HordeSpawnEntityEvent event) {
        EntityType<?> swap = OVERWORLD.get().get(event.getEntity().getType());
        if (swap != null && swap.create(event.getEntity().level()) instanceof Mob overworld) {
            event.setEntity(overworld);
        }
        event.getEntity().addTag(Nights.HORDE);
        Hold.wave(event.getEntity());
        if (event.getEntity().level() instanceof ServerLevel level) {
            Hold.underground(level).ifPresent(event::setPos);
        }
    }

    @SubscribeEvent
    public static void onStart(HordeStartEvent event) {
        WAVES.remove(event.getPlayer().getUUID());
        LAST.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onWave(HordeStartWaveEvent event) {
        ServerPlayer player = event.getPlayer();
        if (Halls.inside(player)) {
            event.setCanceled(true);
            return;
        }
        if (!event.getHorde().isActive()) {
            return;
        }
        Balance.Nights nights = Balance.get().nights();
        long now = player.serverLevel().getGameTime();
        int wave = WAVES.getOrDefault(player.getUUID(), 0);
        Long last = LAST.get(player.getUUID());
        if (wave >= nights.waves() || last != null && now - last < (long) nights.waveSeconds() * TICKS_PER_SECOND) {
            event.setCanceled(true);
            return;
        }
        WAVES.put(player.getUUID(), wave + 1);
        LAST.put(player.getUUID(), now);
        int count = Waves.size(player.server, wave, 1);
        event.setCount(count);
        Record.event(player.server, "wave", player, line -> {
            line.addProperty("wave", wave + 1);
            line.addProperty("count", count);
        });
    }

    @SubscribeEvent
    public static void onFindSpawn(HordeFindSpawnPosEvent event) {
        ServerLevel level = event.getPlayer().serverLevel();
        if (Hold.underground(level).isEmpty()) {
            BlockPos centre = Hold.active(level.getServer()) ? event.getPos() : event.getPlayer().blockPosition();
            event.setPos(Waves.round(level, centre).map(BlockPos::containing).orElseGet(() -> Ground.above(level, event.getPos())));
        }
    }

    @SubscribeEvent
    public static void onEnd(HordeEndEvent event) {
        ServerPlayer player = event.getPlayer();
        Leaving.sendAll(player.serverLevel(), Nights.HORDE, player.position(), GONE_BLOCKS);
    }
}
