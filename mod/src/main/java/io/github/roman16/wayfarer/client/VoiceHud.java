package io.github.roman16.wayfarer.client;

import de.maxhenkel.voicechat.api.internal.events.HudIconRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public final class VoiceHud {
    private VoiceHud() {}

    public static void render(HudIconRenderEvent event) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && !server.isPublished()) {
            event.cancel();
        }
    }
}
