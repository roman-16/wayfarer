package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Hold;
import net.neoforged.bus.api.SubscribeEvent;
import net.smileycorp.hordes.common.event.HordeSpawnEntityEvent;
import net.smileycorp.hordes.common.event.HordeStartWaveEvent;

public final class Hordes {
    private Hordes() {}

    @SubscribeEvent
    public static void onSpawn(HordeSpawnEntityEvent event) {
        Hold.wave(event.getEntity());
    }

    @SubscribeEvent
    public static void onWave(HordeStartWaveEvent event) {
        if (Shrines.inside(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
