package io.github.roman16.wayfarer.client;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.internal.events.HudIconRenderEvent;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

@ForgeVoicechatPlugin
public final class VoiceLink implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return Wayfarer.MODID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(HudIconRenderEvent.class, event -> {
            if (alone()) {
                event.cancel();
            }
        });
    }

    private static boolean alone() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && !server.isPublished();
    }
}
