package io.github.roman16.wayfarer.client.stamp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class Stamp {
    public static final ResourceLocation DISPLAY_FONT = Wayfarer.id("display");
    public static final ResourceLocation BAND = sprite("band");
    public static final ResourceLocation BOOK = sprite("book");
    public static final ResourceLocation DIVIDER = sprite("divider");
    public static final ResourceLocation DIVIDER_INK = sprite("divider_ink");
    public static final ResourceLocation DISC = sprite("disc");
    public static final ResourceLocation KEYCAP = sprite("keycap");
    public static final ResourceLocation MARK = sprite("mark");
    public static final ResourceLocation MARK_INK = sprite("mark_ink");
    public static final ResourceLocation PAGE_LEFT = sprite("page_left");
    public static final ResourceLocation PAGE_RIGHT = sprite("page_right");
    public static final ResourceLocation PAGE_SCROLLER = sprite("page_scroller");
    public static final ResourceLocation PAGE_SCROLLER_TRACK = sprite("page_scroller_track");
    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation PANEL_CLEAR = sprite("panel_clear");
    public static final ResourceLocation PLATE = sprite("plate");
    public static final ResourceLocation PLAY = sprite("play");
    public static final ResourceLocation PLAY_HIGHLIGHTED = sprite("play_highlighted");
    public static final int PLAY_SIZE = 9;
    public static final ResourceLocation RIBBON = sprite("ribbon");
    public static final ResourceLocation RIBBON_ACTIVE = sprite("ribbon_active");
    public static final ResourceLocation RING = sprite("ring");
    public static final ResourceLocation RING_DONE = sprite("ring_done");
    public static final ResourceLocation SLOT = sprite("slot");
    public static final ResourceLocation SPINE = sprite("spine");
    public static final ResourceLocation STRIP = sprite("strip");
    public static final ResourceLocation TOOLTIP = sprite("tooltip");
    public static final ResourceLocation TOOLTIP_STORY = sprite("tooltip_story");
    public static final int MARK_WIDTH = 5;
    public static final int MARK_HEIGHT = 7;
    public static final int RING_SIZE = 5;
    public static final int KEYCAP_HEIGHT = 12;
    public static final int DIVIDER_HEIGHT = 5;
    public static final int ICON_SIZE = 9;
    public static final int FAINTEST_TEXT = 4;
    public static final int DISPLAY_HEIGHT = 15;
    private static final int DISPLAY_RISE = 5;
    private static final int KEYCAP_PADDING = 3;
    private static final int TRACKING = 1;
    private static final int OPAQUE = 0xFF000000;
    private static final int RGB = 0xFFFFFF;
    private static final JsonObject TOKENS = load();
    private static final Map<String, Integer> TEXT = colors(TOKENS.getAsJsonObject("text"));
    public static final int BONE = text("bone");
    public static final int DANGER = text("danger");
    public static final int DIM = text("dim");
    public static final int GAZE = text("gaze");
    public static final int GOLD = text("gold");
    public static final int INK = text("ink");
    public static final int INK_QUIET = text("ink_quiet");
    public static final int INK_RED = text("ink_red");
    public static final int LABEL = text("label");
    public static final int QUIET = text("quiet");
    public static final int CLEAR = alpha("clear");
    public static final int FAINT = alpha("faint");
    public static final int KINDLE_MILLIS = TOKENS.getAsJsonObject("motion").get("kindle").getAsInt();
    public static final int BURN_OUT_MILLIS = TOKENS.getAsJsonObject("motion").get("burn_out").getAsInt();
    public static final int LINGER_MILLIS = TOKENS.getAsJsonObject("motion").get("linger").getAsInt();
    public static final int READ_MILLIS = TOKENS.getAsJsonObject("motion").get("read").getAsInt();
    public static final int RECAP_HOLD_MILLIS = TOKENS.getAsJsonObject("motion").get("recap_hold").getAsInt();
    public static final int VOICE_FADE_MILLIS = TOKENS.getAsJsonObject("motion").get("voice_fade").getAsInt();

    private Stamp() {
    }

    public static int withAlpha(int color, int alpha) {
        return alpha << 24 | rgb(color);
    }

    public static int rgb(int color) {
        return color & RGB;
    }

    public static ResourceLocation compass(String name) {
        return sprite("compass/" + name);
    }

    public static void tinted(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        graphics.flush();
        graphics.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, (color >>> 24) / 255f);
        graphics.blitSprite(sprite, x, y, width, height);
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
    }

    public static int titleWidth(Font font, String text) {
        return font.width(display(text));
    }

    public static int title(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        return graphics.drawString(font, display(text), x, y + DISPLAY_RISE, color, shadow);
    }

    public static int title(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return graphics.drawString(font, text, x, y + DISPLAY_RISE, color, shadow);
    }

    public static ResourceLocation icon(String name, boolean ink) {
        return sprite("icon/" + name + (ink ? "_ink" : ""));
    }

    public static int ramp(String ramp, int shade) {
        return OPAQUE | Integer.parseInt(TOKENS.getAsJsonObject("ramps").getAsJsonArray(ramp).get(shade).getAsString().substring(1), 16);
    }

    public static int speaker(String tone) {
        JsonElement token = TOKENS.getAsJsonObject("speakers").get(tone);
        if (token == null) {
            return GOLD;
        }
        String value = token.getAsString();
        if (value.startsWith("#")) {
            return OPAQUE | Integer.parseInt(value.substring(1), 16);
        }
        int dot = value.indexOf('.');
        return dot < 0 ? text(value) : ramp(value.substring(0, dot), Integer.parseInt(value.substring(dot + 1)));
    }

    public static JsonObject tokens() {
        return TOKENS;
    }

    public static Component display(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(DISPLAY_FONT));
    }

    public static int labelWidth(Font font, String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return font.width(upper) + TRACKING * Math.max(0, upper.length() - 1);
    }

    public static int label(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        int cursor = x;
        for (int index = 0; index < text.length(); index++) {
            String letter = String.valueOf(Character.toUpperCase(text.charAt(index)));
            graphics.drawString(font, letter, cursor, y, color, shadow);
            cursor += font.width(letter) + TRACKING;
        }
        return cursor - TRACKING;
    }

    public static int keycapWidth(Font font, String key) {
        return font.width(key) + KEYCAP_PADDING * 2;
    }

    public static int keycap(GuiGraphics graphics, Font font, String key, int x, int y) {
        int width = keycapWidth(font, key);
        graphics.blitSprite(KEYCAP, x, y - 2, width, KEYCAP_HEIGHT);
        graphics.drawString(font, key, x + KEYCAP_PADDING, y, BONE, false);
        return x + width;
    }

    public static void mark(GuiGraphics graphics, int x, int y, boolean ink) {
        graphics.blitSprite(ink ? MARK_INK : MARK, x, y, ink ? MARK_WIDTH : MARK_WIDTH + 1, ink ? MARK_HEIGHT : MARK_HEIGHT + 1);
    }

    public static void divider(GuiGraphics graphics, int x, int y, int width, boolean ink) {
        graphics.blitSprite(ink ? DIVIDER_INK : DIVIDER, x, y, width, DIVIDER_HEIGHT);
    }

    public static void ring(GuiGraphics graphics, int x, int y, boolean done, int color) {
        tinted(graphics, done ? RING_DONE : RING, x, y, RING_SIZE, RING_SIZE, color);
    }

    public static int lines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y, int color,
            boolean shadow) {
        int cursor = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, cursor, color, shadow);
            cursor += font.lineHeight;
        }
        return cursor;
    }

    private static ResourceLocation sprite(String name) {
        return Wayfarer.id("stamp/" + name);
    }

    private static int text(String name) {
        return TEXT.get(name);
    }

    private static int alpha(String name) {
        return TOKENS.getAsJsonObject("alpha").get(name).getAsInt();
    }

    private static Map<String, Integer> colors(JsonObject section) {
        Map<String, Integer> found = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            found.put(entry.getKey(), OPAQUE | Integer.parseInt(entry.getValue().getAsString().substring(1), 16));
        }
        return found;
    }

    private static JsonObject load() {
        try (var stream = Stamp.class.getResourceAsStream("/wayfarer/style.json")) {
            if (stream == null) {
                throw new IllegalStateException("/wayfarer/style.json is missing from the mod jar; run just generate");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
