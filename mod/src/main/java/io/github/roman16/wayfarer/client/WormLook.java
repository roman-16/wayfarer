package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.worm.GreatWorms;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

final class WormLook {
    private WormLook() {}

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GreatWorms.GREAT_WORM.get(), context -> new GeoEntityRenderer<>(context, new DefaultedEntityGeoModel<>(Wayfarer.id("great_worm"))));
    }
}
