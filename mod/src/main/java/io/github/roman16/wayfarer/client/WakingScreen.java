package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Waking;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WakingScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 128;
    private static final String COMMAND = "/";
    private static boolean asleep;
    private static List<Waking.Sleeper> sleepers = List.of();
    @Nullable
    private static WakingScreen underChat;
    private List<FormattedCharSequence> fire = List.of();
    private int room;

    private WakingScreen() {
        super(Component.translatable("wayfarer.waking.title"));
    }

    public static void page(Waking.Page page) {
        asleep = page.asleep();
        sleepers = page.sleepers();
        underChat = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WakingScreen shown) {
            if (asleep) {
                shown.rebuildWidgets();
            } else {
                minecraft.setScreen(null);
            }
        }
    }

    public static boolean asleep() {
        return asleep;
    }

    static void tick(Minecraft minecraft) {
        if (asleep && minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            minecraft.setScreen(new WakingScreen());
        }
    }

    static void leave() {
        asleep = false;
        sleepers = List.of();
        underChat = null;
    }

    static void behind(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (!asleep || screen instanceof WakingScreen || minecraft.level == null || Recap.status(screen) != null) {
            return;
        }
        if (!(screen instanceof ChatScreen)) {
            PANORAMA.render(event.getGuiGraphics(), screen.width, screen.height, 1, event.getPartialTick());
            return;
        }
        if (underChat == null || underChat.width != screen.width || underChat.height != screen.height) {
            underChat = new WakingScreen();
            underChat.init(minecraft, screen.width, screen.height);
        }
        underChat.render(event.getGuiGraphics(), -1, -1, event.getPartialTick());
    }

    private boolean awake() {
        String name = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        return sleepers.stream().anyMatch(sleeper -> sleeper.awake() && sleeper.name().equals(name));
    }

    private boolean team() {
        return sleepers.size() > 1;
    }

    @Override
    protected void init() {
        List<Component> named = sleepers.stream()
                .map(sleeper -> (Component) Component.translatable(sleeper.awake() ? "wayfarer.waking.awake" : "wayfarer.waking.asleep", sleeper.name()))
                .toList();
        fire = team() ? font.split(Component.translatable("wayfarer.waking.by_fire", ComponentUtils.formatList(named, Component.literal(", "))),
                width - Recap.MARGIN * 2) : List.of();
        int listed = team() ? fire.size() * font.lineHeight + Recap.STATUS_GAP : 0;
        int below = listed + BUTTON_HEIGHT + (team() ? Recap.STATUS_GAP + font.lineHeight : 0);
        room = height - Recap.MARGIN * 2 - Recap.STATUS_GAP - below;
        int top = Recap.firstBottom(font, width, room) + Recap.STATUS_GAP + listed;
        boolean awake = awake();
        Button button = addRenderableWidget(Button.builder(Component.translatable(awake ? "wayfarer.waking.lie_down" : "wayfarer.waking.wake"),
                pressed -> PacketDistributor.sendToServer(new Waking.Wake(!awake))).bounds((width - BUTTON_WIDTH) / 2, top, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        setInitialFocus(button);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanorama(graphics, partialTick);
        renderBlurredBackground(partialTick);
        renderMenuBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Recap.first(graphics, font, width, room);
        int y = Recap.firstBottom(font, width, room) + Recap.STATUS_GAP;
        for (FormattedCharSequence line : fire) {
            graphics.drawString(font, line, (width - font.width(line)) / 2, y, Stamp.BONE, true);
            y += font.lineHeight;
        }
        if (team()) {
            Component everyone = Component.translatable("wayfarer.waking.everyone");
            graphics.drawString(font, everyone, (width - font.width(everyone)) / 2, y + Recap.STATUS_GAP + BUTTON_HEIGHT + Recap.STATUS_GAP, Stamp.QUIET, true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (minecraft.options.keyCommand.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(COMMAND));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new PauseScreen(true));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
