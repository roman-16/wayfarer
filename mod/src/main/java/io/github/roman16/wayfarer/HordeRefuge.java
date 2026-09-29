package io.github.roman16.wayfarer;

import net.neoforged.bus.api.SubscribeEvent;
import net.smileycorp.hordes.common.event.HordeStartWaveEvent;

public final class HordeRefuge {
    private HordeRefuge() {}

    @SubscribeEvent
    public static void onWave(HordeStartWaveEvent event) {
        if (Shrines.inside(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
}
