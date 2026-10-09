package io.github.roman16.wayfarer.client;

import net.minecraft.client.Minecraft;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class JadeLink implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addBeforeRenderCallback((box, rect, graphics, accessor) -> Minecraft.getInstance().player == null
                || !Minecraft.getInstance().player.isShiftKeyDown());
    }
}
