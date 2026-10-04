package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.NightPayload;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Wayfarer.MODID, dist = Dist.CLIENT)
public final class WayfarerClient {
    public WayfarerClient(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(Wayfarer.id("hud"), Hud::render));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(Wayfarer.SHRINE_PEDESTAL_ENTITY.get(), ShrinePedestalRenderer::new));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Sight.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR) || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HudState.compass = CompassPayload.hidden(List.of());
            HudState.gaze = GazePayload.CLOSED;
            HudState.night = NightPayload.CALM;
        });
    }
}
