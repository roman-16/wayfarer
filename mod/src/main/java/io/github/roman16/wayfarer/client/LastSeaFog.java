package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.HudState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

final class LastSeaFog {
    private LastSeaFog() {}

    static void render(ViewportEvent.RenderFog event) {
        int past = HudState.lastSea;
        if (past <= 0 || event.getType() != FogType.NONE) {
            return;
        }
        Balance.LastSea sea = Balance.get().lastSea();
        float closing = Math.min(1f, (float) past / sea.freezingBlocks());
        event.setFarPlaneDistance(Mth.lerp(closing, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), sea.fogBlocks())));
        event.setNearPlaneDistance(Mth.lerp(closing, event.getNearPlaneDistance(), 0));
        event.setCanceled(true);
    }
}
