package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Record;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class Screens {
    private Screens() {}

    static void opening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof ChatScreen || event.getCurrentScreen() != null && event.getCurrentScreen().getClass() == screen.getClass()) {
            return;
        }
        opened(switch (screen) {
            case JournalScreen journal -> "journal";
            case InventoryScreen inventory -> "inventory";
            case PauseScreen pause -> "pause";
            case DeathScreen death -> "death";
            case WakingScreen waking -> "waking";
            case AbstractContainerScreen<?> container -> "container " + container.getTitle().getString();
            default -> MapLink.shown(screen) ? "map" : screen.getClass().getSimpleName();
        });
    }

    static void opened(String screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && minecraft.player != null && minecraft.getConnection().hasChannel(Record.Opened.TYPE)) {
            PacketDistributor.sendToServer(new Record.Opened(screen));
        }
    }
}
