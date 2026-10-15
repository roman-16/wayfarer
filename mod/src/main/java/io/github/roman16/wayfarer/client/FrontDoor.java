package io.github.roman16.wayfarer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class FrontDoor {
    private static final String ADVANCEMENTS = "gui.advancements";
    private static final List<String> FEEDBACK = List.of("menu.feedback", "menu.sendFeedback");
    private static final int GAP = 8;
    private static final String OPTIONS = "menu.options";
    private static final String PLAYER_REPORTING = "menu.playerReporting";
    private static final String JOURNEYMAP = "journeymap";
    private static final String REALMS = "menu.online";
    private static final String STATISTICS = "gui.stats";
    private static final int FOOTER = 12;
    private static final ResourceLocation LOGO = Wayfarer.id("textures/gui/title/logo.png");
    private static final int LOGO_HEIGHT = 64;
    private static final int LOGO_RISE = 10;
    private static final int LOGO_WIDTH = 256;
    private static final int ROW = 24;

    private FrontDoor() {
    }

    static void init(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) {
            button(event, REALMS).ifPresent(realms -> {
                event.removeListener(realms);
                for (GuiEventListener listener : event.getListenersList()) {
                    if (listener instanceof AbstractWidget widget && widget.getY() > realms.getY() && widget.getBottom() < title.height - FOOTER) {
                        widget.setY(widget.getY() - ROW);
                    }
                }
            });
        }
        if (event.getScreen() instanceof PauseScreen) {
            button(event, ADVANCEMENTS).ifPresent(advancements -> replace(event, advancements, Component.translatable("wayfarer.journal"),
                    JournalScreen::open));
            button(event, STATISTICS).ifPresent(statistics -> {
                Button map = replace(event, statistics, Component.translatable("wayfarer.journal.map"), () -> MapLink.open(false));
                map.active = ModList.get().isLoaded(JOURNEYMAP);
            });
            FEEDBACK.stream().map(key -> button(event, key)).flatMap(Optional::stream).findFirst().ifPresent(row -> {
                for (GuiEventListener listener : List.copyOf(event.getListenersList())) {
                    if (listener instanceof AbstractWidget widget && widget.getY() == row.getY()) {
                        event.removeListener(widget);
                    } else if (listener instanceof AbstractWidget widget && widget.getY() > row.getY()) {
                        widget.setY(widget.getY() - ROW);
                    }
                }
            });
            button(event, PLAYER_REPORTING).ifPresent(reporting -> {
                event.removeListener(reporting);
                button(event, OPTIONS).ifPresent(options -> options.setWidth(options.getWidth() + reporting.getWidth() + GAP));
            });
        }
    }

    public static void logo(GuiGraphics graphics, int screenWidth, float transparency, int height) {
        graphics.setColor(1, 1, 1, transparency);
        RenderSystem.enableBlend();
        graphics.blit(LOGO, (screenWidth - LOGO_WIDTH) / 2, height - LOGO_RISE, 0, 0, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static Optional<Button> button(ScreenEvent.Init.Post event, String key) {
        return event.getListenersList().stream()
                .filter(listener -> listener instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key))
                .map(Button.class::cast)
                .findFirst();
    }

    private static Button replace(ScreenEvent.Init.Post event, Button old, Component label, Runnable action) {
        Button button = Button.builder(label, pressed -> action.run()).bounds(old.getX(), old.getY(), old.getWidth(), old.getHeight()).build();
        event.removeListener(old);
        event.addListener(button);
        return button;
    }
}
