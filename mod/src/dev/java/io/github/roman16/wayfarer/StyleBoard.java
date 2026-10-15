package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class StyleBoard extends Screen {
    static final List<String> PAGES = List.of("book", "hud", "type", "widgets");
    private static final List<String> ICONS = List.of("quests", "story", "people", "words", "pages", "conversations", "stones", "map", "gear");
    private static final List<String> MARKS = List.of("fire", "grave", "hall", "left", "pointer", "right", "tent");
    private static final Map<String, Integer> TEXT = Map.of("bone", Stamp.BONE, "label", Stamp.LABEL, "quiet", Stamp.QUIET, "dim", Stamp.DIM, "gold",
            Stamp.GOLD, "gaze", Stamp.GAZE, "danger", Stamp.DANGER);
    private static final int SWATCH = 7;
    private static final int GAP = 4;
    private static final int LINE = 10;
    private final String page;

    StyleBoard(String page) {
        super(Component.literal("Style board: " + page));
        this.page = page;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - 312) / 2;
        int top = (height - 172) / 2;
        graphics.blitSprite(Stamp.PANEL, left, top, 312, 172);
        switch (page) {
            case "book" -> book(graphics, left, top);
            case "hud" -> hud(graphics, left, top);
            case "type" -> type(graphics, left, top);
            default -> widgets(graphics, left, top);
        }
    }

    private void type(GuiGraphics graphics, int left, int top) {
        int x = left + 10;
        int y = top + 10;
        for (Map.Entry<String, JsonElement> ramp : Stamp.tokens().getAsJsonObject("ramps").entrySet()) {
            Stamp.label(graphics, font, ramp.getKey(), x, y, Stamp.LABEL, true);
            JsonArray shades = ramp.getValue().getAsJsonArray();
            for (int shade = 0; shade < shades.size(); shade++) {
                int sx = x + 46 + shade * (SWATCH + 1);
                graphics.fill(sx, y, sx + SWATCH, y + SWATCH, Stamp.ramp(ramp.getKey(), shade));
            }
            y += SWATCH + 2;
        }
        int tx = left + 100;
        int ty = top + 10;
        for (String name : List.of("bone", "label", "quiet", "dim", "gold", "gaze", "danger")) {
            graphics.drawString(font, name, tx, ty, TEXT.get(name), true);
            ty += LINE;
        }
        int bx = left + 150;
        int by = top + 10;
        Stamp.title(graphics, font, "The First Day", bx, by, Stamp.GOLD, true);
        by += Stamp.DISPLAY_HEIGHT + GAP;
        Stamp.title(graphics, font, "ABCDEFGHIJKLM", bx, by, Stamp.BONE, true);
        by += Stamp.DISPLAY_HEIGHT;
        Stamp.title(graphics, font, "NOPQRSTUVWXYZ", bx, by, Stamp.BONE, true);
        by += Stamp.DISPLAY_HEIGHT + GAP;
        for (String line : List.of("Rest by your fire.", "abcdefghijklmnopqrstuvwxyz", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789 .,:;!?'\"()-",
                "Äpfel, Öl, Übermut, Straße")) {
            graphics.drawString(font, line, bx, by, Stamp.BONE, true);
            by += LINE;
        }
        Stamp.label(graphics, font, "Main road", bx, by + 2, Stamp.LABEL, true);
        int kx = Stamp.label(graphics, font, "A new road", bx, by + 14, Stamp.LABEL, true) + GAP;
        for (String key : List.of("J", "M", "Z", "1")) {
            kx = Stamp.keycap(graphics, font, key, kx, by + 14) + 2;
        }
    }

    private void hud(GuiGraphics graphics, int left, int top) {
        int center = left + 156;
        graphics.blitSprite(Stamp.BAND, center - 102, top + 8, 204, 15);
        graphics.drawString(font, "W", center - 60, top + 12, Stamp.BONE, true);
        graphics.drawString(font, "NW", center - 30, top + 12, Stamp.QUIET, true);
        Stamp.mark(graphics, center - 3, top + 12, false);
        graphics.drawString(font, "N", center + 30, top + 12, Stamp.BONE, true);
        Stamp.tinted(graphics, Stamp.compass("tent"), center + 50, top + 14, 7, 5, Stamp.BONE);
        Stamp.tinted(graphics, Stamp.compass("grave"), center + 70, top + 13, 5, 6, Stamp.BONE);
        Stamp.tinted(graphics, Stamp.compass("pointer"), center - 2, top + 22, 5, 3, Stamp.GOLD);
        graphics.blitSprite(Stamp.PLATE, center - 12, top + 26, 24, 13);
        graphics.drawString(font, "512", center - 7, top + 29, Stamp.GOLD, true);
        graphics.blitSprite(Stamp.PLATE, center - 54, top + 41, 108, 13);
        int ex = Stamp.label(graphics, font, "The ember", center - 50, top + 44, Stamp.LABEL, true);
        Stamp.label(graphics, font, " flaring", ex, top + 44, Stamp.GOLD, true);
        int px = left + 10;
        int py = top + 60;
        graphics.blitSprite(Stamp.PANEL_CLEAR, px, py, 150, 62);
        Stamp.label(graphics, font, "Main road", px + 8, py + 8, Stamp.LABEL, true);
        Stamp.mark(graphics, px + 8, py + 19, false);
        graphics.drawString(font, "The First Day", px + 17, py + 19, Stamp.GOLD, true);
        graphics.drawString(font, "Rest by your fire.", px + 8, py + 30, Stamp.BONE, true);
        Stamp.ring(graphics, px + 8, py + 42, false, Stamp.QUIET);
        int kx = graphics.drawString(font, "Open your journal", px + 16, py + 41, Stamp.BONE, true);
        Stamp.keycap(graphics, font, "J", kx + 2, py + 41);
        Stamp.ring(graphics, px + 8, py + 53, true, Stamp.DIM);
        graphics.drawString(font, "Open your inventory", px + 16, py + 52, Stamp.DIM, true);
        int ax = left + 168;
        graphics.blitSprite(Stamp.PANEL_CLEAR, ax, py, 134, 48);
        List<String> answers = List.of("Who are you?", "Where am I?", "What happened to me?");
        for (int index = 0; index < answers.size(); index++) {
            int ay = py + 9 + index * 12;
            Stamp.keycap(graphics, font, String.valueOf(index + 1), ax + 7, ay);
            graphics.drawString(font, answers.get(index), ax + 22, ay, Stamp.BONE, true);
        }
        graphics.blitSprite(Stamp.STRIP, left + 10, top + 128, 150, 34);
        Stamp.mark(graphics, left + 50, top + 133, false);
        Stamp.label(graphics, font, "A new road", left + 58, top + 134, Stamp.LABEL, true);
        Stamp.title(graphics, font, "The Pull", left + 64, top + 145, Stamp.GOLD, true);
        graphics.blitSprite(Stamp.PANEL_CLEAR, left + 168, top + 112, 134, 50);
        graphics.drawString(font, "Hob's blade is plain...", left + 176, top + 120, Stamp.withAlpha(Stamp.QUIET, Stamp.FAINT), true);
        int nx = graphics.drawString(font, "MAUD", left + 176, top + 131, Stamp.GOLD, true);
        graphics.drawString(font, "You walk ahead.", nx + 4, top + 131, Stamp.BONE, true);
        int meter = Stamp.label(graphics, font, "Dragon's gaze", left + 176, top + 144, Stamp.GAZE, true) + GAP;
        for (int cell = 0; cell < 8; cell++) {
            int cx = meter + cell * 5;
            graphics.fill(cx, top + 145, cx + 4, top + 151, cell < 5 ? Stamp.ramp("gaze", 2) : Stamp.ramp("ash", 0));
            graphics.fill(cx, top + 145, cx + 4, top + 146, cell < 5 ? Stamp.ramp("gaze", 3) : Stamp.ramp("ash", 3));
        }
    }

    private void widgets(GuiGraphics graphics, int left, int top) {
        int x = left + 10;
        int y = top + 10;
        for (String state : List.of("button", "button_highlighted", "button_disabled")) {
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/" + state), x, y, 90, 20);
            graphics.drawString(font, state.equals("button") ? "Track" : state.equals("button_highlighted") ? "How it's made" : "Not known yet",
                    x + 8, y + 6, state.equals("button_disabled") ? Stamp.DIM : Stamp.BONE, true);
            y += 24;
        }
        for (String state : List.of("text_field", "text_field_highlighted", "slider", "slider_highlighted")) {
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/" + state), x, y, 90, 16);
            y += 19;
        }
        graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/slider_handle"), x + 40, y - 19, 8, 16);
        int cx = left + 110;
        int cy = top + 10;
        for (String state : List.of("checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted")) {
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/" + state), cx, cy, 20, 20);
            cx += 23;
        }
        cx = left + 110;
        cy += 24;
        for (String state : List.of("tab", "tab_highlighted", "tab_selected", "tab_selected_highlighted")) {
            graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/" + state), cx, cy, 44, 20);
            cx += 47;
        }
        cx = left + 110;
        cy += 26;
        for (int slot = 0; slot < 5; slot++) {
            graphics.blitSprite(Stamp.SLOT, cx + slot * 18, cy, 18, 18);
        }
        graphics.renderItem(new ItemStack(Items.IRON_SWORD), cx + 1, cy + 1);
        graphics.renderItem(new ItemStack(Items.BREAD, 16), cx + 19, cy + 1);
        graphics.renderItemDecorations(font, new ItemStack(Items.BREAD, 16), cx + 19, cy + 1);
        graphics.blitSprite(ResourceLocation.withDefaultNamespace("hud/hotbar_selection"), cx + 93, cy - 3, 24, 23);
        cy += 26;
        graphics.blitSprite(Stamp.TOOLTIP, cx, cy, 90, 32);
        graphics.drawString(font, "Iron Sword", cx + 5, cy + 5, Stamp.BONE, true);
        graphics.drawString(font, "6 Attack Damage", cx + 5, cy + 17, Stamp.QUIET, true);
        graphics.blitSprite(Stamp.TOOLTIP_STORY, cx + 96, cy, 96, 32);
        graphics.drawString(font, "The ember", cx + 101, cy + 5, Stamp.GOLD, true);
        graphics.drawString(font, "Warm. 1 shard.", cx + 101, cy + 17, Stamp.BONE, true);
        cy += 38;
        int ix = left + 110;
        for (String icon : ICONS) {
            Stamp.tinted(graphics, Stamp.icon(icon, false), ix, cy, Stamp.ICON_SIZE, Stamp.ICON_SIZE, Stamp.BONE);
            ix += 13;
        }
        for (String mark : MARKS) {
            Stamp.tinted(graphics, Stamp.compass(mark), ix, cy + 1, 7, 7, Stamp.GOLD);
            ix += 11;
        }
        Stamp.mark(graphics, ix, cy, false);
        Stamp.divider(graphics, left + 110, cy + 14, 190, false);
    }

    private void book(GuiGraphics graphics, int left, int top) {
        int x = left + 8;
        int y = top + 8;
        graphics.blitSprite(Stamp.BOOK, x, y, 270, 156);
        int pageWidth = (270 - 24 - 10) / 2;
        graphics.blitSprite(Stamp.PAGE_LEFT, x + 12, y + 12, pageWidth, 132);
        graphics.blitSprite(Stamp.PAGE_RIGHT, x + 12 + pageWidth + 10, y + 12, pageWidth, 132);
        graphics.blitSprite(Stamp.SPINE, x + 12 + pageWidth, y + 12, 10, 132);
        Stamp.mark(graphics, x + 12 + pageWidth + 2, y + 3, false);
        int lx = x + 20;
        Stamp.title(graphics, font, "Quests", lx, y + 20, Stamp.INK_RED, false);
        Stamp.divider(graphics, lx, y + 39, pageWidth - 16, true);
        Stamp.label(graphics, font, "Main road", lx, y + 48, Stamp.INK_QUIET, false);
        graphics.fill(lx - 2, y + 59, lx + pageWidth - 14, y + 69, Stamp.withAlpha(Stamp.ramp("paper", 1), Stamp.FAINT));
        Stamp.mark(graphics, lx, y + 60, true);
        graphics.drawString(font, "The First Day", lx + 8, y + 60, Stamp.INK_RED, false);
        graphics.drawString(font, "The Last Fire", lx + 8, y + 71, Stamp.INK_QUIET, false);
        graphics.drawString(font, "+ Done (6)", lx, y + 84, Stamp.INK_QUIET, false);
        int rx = x + 12 + pageWidth + 18;
        Stamp.title(graphics, font, "The First Day", rx, y + 20, Stamp.INK_RED, false);
        Stamp.divider(graphics, rx, y + 39, pageWidth - 16, true);
        graphics.drawString(font, "I walked away from his", rx, y + 48, Stamp.INK, false);
        graphics.drawString(font, "fire, as he told me.", rx, y + 58, Stamp.INK, false);
        Stamp.mark(graphics, rx, y + 72, true);
        graphics.drawString(font, "Rest by your fire.", rx + 8, y + 72, Stamp.INK, false);
        Stamp.ring(graphics, rx + 8, y + 84, false, Stamp.INK);
        int kx = graphics.drawString(font, "Open journal", rx + 16, y + 83, Stamp.INK, false);
        Stamp.keycap(graphics, font, "J", kx + 2, y + 83);
        Stamp.ring(graphics, rx + 8, y + 96, true, Stamp.INK_QUIET);
        graphics.drawString(font, "Take His Pack", rx + 16, y + 95, Stamp.INK_QUIET, false);
        graphics.blitSprite(ResourceLocation.withDefaultNamespace("widget/button"), rx, y + 118, 40, 20);
        graphics.drawString(font, "Track", rx + 8, y + 124, Stamp.BONE, true);
        int ry = y + 16;
        for (int index = 0; index < ICONS.size(); index++) {
            boolean active = index == 0;
            graphics.blitSprite(active ? Stamp.RIBBON_ACTIVE : Stamp.RIBBON, x + 264, ry, active ? 30 : 24, 13);
            Stamp.tinted(graphics, Stamp.icon(ICONS.get(index), false), x + 271 + (active ? 6 : 0), ry + 2, Stamp.ICON_SIZE, Stamp.ICON_SIZE,
                    active ? Stamp.INK : Stamp.BONE);
            ry += 15;
        }
    }
}
