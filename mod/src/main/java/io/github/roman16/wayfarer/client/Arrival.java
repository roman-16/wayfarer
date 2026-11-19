package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.story.Arrivals;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class Arrival {
    private static final String SODIUM = "sodium";
    private static boolean coming = true;

    private Arrival() {}

    static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        coming = true;
    }

    static void cloned(ClientPlayerNetworkEvent.Clone event) {
        coming = true;
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!coming || minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null || loading(minecraft.screen) || WakingScreen.asleep()
                || !drawn(minecraft)) {
            return;
        }
        coming = false;
        if (minecraft.getConnection().hasChannel(Arrivals.Arrived.TYPE)) {
            PacketDistributor.sendToServer(new Arrivals.Arrived());
        }
    }

    private static boolean loading(Screen screen) {
        return screen instanceof ReceivingLevelScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen
                || screen instanceof GenericMessageScreen;
    }

    private static boolean drawn(Minecraft minecraft) {
        if (ModList.get().isLoaded(SODIUM)) {
            SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
            return renderer != null && renderer.isTerrainRenderComplete();
        }
        return minecraft.levelRenderer.hasRenderedAllSections();
    }
}
