package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Narration;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.mixin.BookViewScreenAccessor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class Reading {
    private static final ResourceLocation OLD_SCRIPT = ResourceLocation.withDefaultNamespace("alt");
    private static final int BOOK_WIDTH = 192;
    private static final int PLAY_X = 36;
    private static final int PLAY_Y = 16;
    private static final Set<String> heard = new HashSet<>();
    @Nullable
    private static Screen reading;
    @Nullable
    private static Play shown;
    private static int page = -1;

    private Reading() {}

    public static boolean once(String key) {
        return heard.add(key);
    }

    static void leave() {
        heard.clear();
        reading = null;
        page = -1;
    }

    static void tick(Minecraft minecraft) {
        if (!(minecraft.screen instanceof BookViewScreen book)) {
            if (reading != null) {
                reading = null;
                page = -1;
                Speech.stopPages();
            }
            return;
        }
        int now = ((BookViewScreenAccessor) book).wayfarer$page();
        if (shown != null) {
            shown.visible = clip(book).isPresent();
        }
        if (book == reading && now == page) {
            return;
        }
        reading = book;
        page = now;
        Optional<String> clip = clip(book);
        if (clip.isPresent() && once(clip.get())) {
            Speech.pages(List.of(clip.get()));
        } else {
            Speech.stopPages();
        }
    }

    static void init(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof BookViewScreen book) {
            shown = new Play(book, (book.width - BOOK_WIDTH) / 2 + PLAY_X, PLAY_Y);
            shown.visible = clip(book).isPresent();
            event.addListener(shown);
        }
    }

    private static Optional<String> clip(BookViewScreen book) {
        BookViewScreen.BookAccess access = ((BookViewScreenAccessor) book).wayfarer$book();
        int at = ((BookViewScreenAccessor) book).wayfarer$page();
        if (at < 0 || at >= access.getPageCount()) {
            return Optional.empty();
        }
        FormattedText text = access.getPage(at);
        boolean unread = text.visit((style, part) -> OLD_SCRIPT.equals(style.getFont()) ? Optional.of(true) : Optional.empty(), Style.EMPTY).isPresent();
        return unread ? Optional.empty() : Narration.page(text.getString()).map(Narration.Clip::path);
    }

    private static final class Play extends AbstractButton {
        private final BookViewScreen book;

        private Play(BookViewScreen book, int x, int y) {
            super(x, y, Stamp.PLAY_SIZE, Stamp.PLAY_SIZE, Component.translatable("wayfarer.book.hear"));
            this.book = book;
        }

        @Override
        public void onPress() {
            clip(book).ifPresent(clip -> Speech.pages(List.of(clip)));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blitSprite(isHoveredOrFocused() ? Stamp.PLAY_HIGHLIGHTED : Stamp.PLAY, getX(), getY(), width, height);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
