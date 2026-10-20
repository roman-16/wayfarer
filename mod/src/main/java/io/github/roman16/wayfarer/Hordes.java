package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Hold;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.smileycorp.hordes.common.event.HordeSpawnEntityEvent;
import net.smileycorp.hordes.common.event.HordeStartWaveEvent;
import net.smileycorp.hordes.hordeevent.capability.HordeEvent;
import net.smileycorp.hordes.hordeevent.capability.HordeSavedData;

public final class Hordes {
    private Hordes() {}

    static void stop(ServerPlayer player) {
        HordeEvent horde = HordeSavedData.getData(player.serverLevel()).getEvent(player);
        if (horde != null && horde.isActive()) {
            horde.stopEvent(player, true);
        }
    }

    @SubscribeEvent
    public static void onSpawn(HordeSpawnEntityEvent event) {
        Hold.wave(event.getEntity());
    }

    @SubscribeEvent
    public static void onWave(HordeStartWaveEvent event) {
        if (Halls.inside(event.getPlayer())) {
            event.setCanceled(true);
            return;
        }
        if (event.getHorde().isActive()) {
            event.setCount(wave(Shine.of(event.getPlayer().server)));
        }
    }

    public static int wave(int shine) {
        Balance.Nights nights = Balance.get().nights();
        return nights.wave() + shine / nights.shinePerCreature();
    }
}
