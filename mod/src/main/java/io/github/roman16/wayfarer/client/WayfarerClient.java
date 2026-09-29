package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Wayfarer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Wayfarer.MODID, dist = Dist.CLIENT)
public final class WayfarerClient {
    public WayfarerClient(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(Wayfarer.id("hud"), Hud::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HudState.compass = CompassPayload.silent(0);
            HudState.gaze = GazePayload.CLOSED;
        });
    }
}
