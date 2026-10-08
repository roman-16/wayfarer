package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.story.Tasks;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class Hud {
    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] HEAT = {"guttering", "fading", "listening", "glowing", "flaring"};
    private static final String[] NUMERALS = {"", "I", "II", "III"};
    private static final int BAR_CELLS = 8;
    private static final int TICK_COLOR = 0xFF808080;
    private static final int CENTER_COLOR = 0xFFFFFFFF;
    private static final int CARDINAL_COLOR = 0xFFFFFF;
    private static final int DIRECTION_COLOR = 0xA0A0A0;
    private static final int EMBER_COLOR = 0xFFD27A;
    private static final int MARK_COLOR = 0xE0E0E0;
    private static final int DISTANCE_COLOR = 0xFFFFFF;
    private static final String LEAD_MARK = "✦";
    private static final String FIRE_MARK = "♨";
    private static final String SHRINE_MARK = "⌂";
    private static final String GRAVE_MARK = "✝";
    private static final String TENT_MARK = "▲";
    private static final int GAP = "    ".length();
    private static final int HALF_WIDTH = 90;
    private static final float HALF_VIEW_DEGREES = 90;
    private static final float FACING_DEGREES = 8;
    private static final float DIRECTION_STEP = 45;
    private static final int RIBBON_HEIGHT = 11;
    private static final int POINTER = 4;
    private static final int LINE = 12;
    private static final int TOP = 4;
    private static final int EDGE = 4;
    private static final int BOSS_BAR = 6;
    private static final int EFFECT_ROW = 26;
    private static final int EFFECT_COLUMN = 25;
    private static final int PANEL_WIDTH = 168;
    private static final int PANEL_PAD = 4;
    private static final int PANEL_LINE = 10;
    private static final int SUBTITLE_ROOM = 96;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int TASK_COLOR = 0xD0D0D0;
    private static final int DONE_COLOR = 0x909090;
    private static final String OPEN_BOX = "☐ ";
    private static final String DONE_BOX = "☑ ";
    private static final ResourceLocation BANNER = Wayfarer.id("banner");
    private static final int BANNER_HEIGHT = 24;
    private static final int BANNER_PAD = 10;
    private static final int BANNER_TEXT = 8;
    private static final int BANNER_INK = 0xFF3F2A14;
    private static final long BANNER_MILLIS = 4000;
    private static final long BANNER_SLIDE_MILLIS = 250;
    private static final int BANNER_DROP = 8;
    private static int bossBottom;
    @Nullable
    private static Component banner;
    private static long bannerFrom;

    private Hud() {}

    public static void beforeLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) {
            bossBottom = 0;
        }
    }

    public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + BOSS_BAR);
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        int background = (int) (minecraft.options.textBackgroundOpacity().get() * 255) << 24;
        CompassPayload reading = HudState.compass;
        boolean compass = reading.mode() != CompassPayload.Mode.HIDDEN;
        int y = Math.max(TOP, bossBottom + EDGE);
        int half = 0;
        if (compass && graphics.guiWidth() - EFFECT_COLUMN * effectColumns(player) < graphics.guiWidth() / 2 + HALF_WIDTH + EDGE * 2 + LINE) {
            y = Math.max(y, TOP + effectRows(player) * EFFECT_ROW);
        }
        if (compass) {
            band(graphics, font, reading, player, y, background);
            y += RIBBON_HEIGHT + POINTER;
            if (distances(graphics, font, reading, player, y, background)) {
                y += LINE;
            }
            half = HALF_WIDTH + EDGE * 2 + LINE;
        }
        Component urgent = urgent(reading, compass);
        if (urgent != null) {
            half = Math.max(half, line(graphics, font, urgent, y, background));
            y += LINE;
        }
        if (!minecraft.getDebugOverlay().showDebugScreen() && (minecraft.screen == null || minecraft.screen instanceof ChatScreen)) {
            int left = graphics.guiWidth() - EDGE - PANEL_WIDTH;
            int below = half > 0 && left < graphics.guiWidth() / 2 + half ? y : TOP;
            int top = Math.max(below, TOP + effectRows(player) * EFFECT_ROW);
            int bottom = graphics.guiHeight() - (minecraft.options.showSubtitles().get() ? SUBTITLE_ROOM : EDGE);
            if (Conversation.answersRight > left) {
                bottom = Math.min(bottom, Conversation.answersTop - EDGE);
            }
            if (Conversation.bandRight > left) {
                bottom = Math.min(bottom, Conversation.bandTop - EDGE);
            }
            panel(graphics, font, HudState.panel, top, bottom, background);
        }
        banner(graphics, font, y);
    }

    @Nullable
    private static Component urgent(CompassPayload reading, boolean compass) {
        Component gaze = gaze(HudState.gaze);
        if (gaze != null) {
            return gaze;
        }
        if (HudState.night.sleepless()) {
            return night();
        }
        if (!compass) {
            return null;
        }
        MutableComponent caption = caption(reading);
        return caption.getString().isEmpty() ? null : caption;
    }

    public static void clearBanners() {
        HudState.banners.clear();
        banner = null;
    }

    private static void banner(GuiGraphics graphics, Font font, int y) {
        long now = Util.getMillis();
        if (banner == null || now - bannerFrom > BANNER_MILLIS) {
            banner = HudState.banners.poll();
            bannerFrom = now;
            if (banner == null) {
                return;
            }
        }
        long shown = now - bannerFrom;
        float slide = Math.min(1, Math.min(shown, BANNER_MILLIS - shown) / (float) BANNER_SLIDE_MILLIS);
        float eased = slide * slide * (3 - 2 * slide);
        int width = font.width(banner) + BANNER_PAD * 2;
        int x = graphics.guiWidth() / 2 - width / 2;
        int top = y + Math.round((eased - 1) * BANNER_DROP);
        graphics.flush();
        graphics.setColor(1, 1, 1, eased);
        graphics.blitSprite(BANNER, x, top, width, BANNER_HEIGHT);
        graphics.setColor(1, 1, 1, 1);
        int ink = (Math.max(4, Math.round(eased * 255)) << 24) | (BANNER_INK & 0xFFFFFF);
        graphics.drawString(font, banner, x + BANNER_PAD, top + BANNER_TEXT, ink, false);
    }

    private static void panel(GuiGraphics graphics, Font font, Tasks.Panel panel, int top, int bottom, int background) {
        List<Row> rows = new ArrayList<>();
        int width = PANEL_WIDTH - PANEL_PAD * 2;
        rows(rows, font, panel.heading(), width, EMBER_COLOR);
        rows(rows, font, panel.objective(), width, TEXT_COLOR);
        for (Component task : panel.open()) {
            task(rows, font, OPEN_BOX, task, width, TASK_COLOR);
        }
        for (Component task : panel.done()) {
            task(rows, font, DONE_BOX, task, width, DONE_COLOR);
        }
        int fits = Math.max(0, (bottom - top - PANEL_PAD * 2) / PANEL_LINE);
        List<Row> shown = rows.subList(0, Math.min(rows.size(), fits));
        if (shown.isEmpty()) {
            return;
        }
        int right = graphics.guiWidth() - EDGE;
        int left = right - PANEL_WIDTH;
        graphics.fill(left, top, right, top + shown.size() * PANEL_LINE + PANEL_PAD * 2, background);
        int y = top + PANEL_PAD;
        for (Row row : shown) {
            graphics.drawString(font, row.text(), left + PANEL_PAD + row.indent(), y, row.color());
            y += PANEL_LINE;
        }
    }

    private record Row(FormattedCharSequence text, int indent, int color) {}

    private static void rows(List<Row> rows, Font font, Component text, int width, int color) {
        if (text.getString().isEmpty()) {
            return;
        }
        font.split(text, width).forEach(line -> rows.add(new Row(line, 0, color)));
    }

    private static void task(List<Row> rows, Font font, String box, Component text, int width, int color) {
        int indent = font.width(box);
        List<FormattedCharSequence> lines = font.split(text, width - indent);
        rows.add(new Row(FormattedCharSequence.composite(Component.literal(box).getVisualOrderText(), lines.getFirst()), 0, color));
        lines.stream().skip(1).forEach(line -> rows.add(new Row(line, indent, color)));
    }

    private static int effectColumns(LocalPlayer player) {
        long good = player.getActiveEffects().stream().filter(effect -> effect.showIcon() && effect.getEffect().value().isBeneficial()).count();
        long bad = player.getActiveEffects().stream().filter(effect -> effect.showIcon() && !effect.getEffect().value().isBeneficial()).count();
        return (int) Math.max(good, bad);
    }

    private static int effectRows(LocalPlayer player) {
        boolean good = player.getActiveEffects().stream().anyMatch(effect -> effect.showIcon() && effect.getEffect().value().isBeneficial());
        boolean bad = player.getActiveEffects().stream().anyMatch(effect -> effect.showIcon() && !effect.getEffect().value().isBeneficial());
        return bad ? 2 : good ? 1 : 0;
    }

    private static void band(GuiGraphics graphics, Font font, CompassPayload reading, LocalPlayer player, int y, int background) {
        int center = graphics.guiWidth() / 2;
        int bottom = y + RIBBON_HEIGHT - 1;
        graphics.fill(center - HALF_WIDTH - EDGE, y - 1, center + HALF_WIDTH + EDGE, bottom, background);
        graphics.fill(center - 1, bottom, center + 2, bottom + 1, CENTER_COLOR);
        graphics.fill(center, bottom + 1, center + 1, bottom + 2, CENTER_COLOR);
        List<int[]> marked = new ArrayList<>();
        for (CompassPayload.Marker marker : reading.markers()) {
            float relative = bearing(marker.x(), marker.z(), player);
            if (Math.abs(relative) <= HALF_VIEW_DEGREES) {
                int x = center + offset(relative);
                int half = font.width(symbol(marker)) / 2 + 1;
                marked.add(new int[] {x - half, x + half});
            }
        }
        float yaw = player.getYRot();
        for (int step = 0; step < DIRECTIONS.length * 2; step++) {
            float relative = Mth.wrapDegrees(step * DIRECTION_STEP / 2 - yaw);
            if (Math.abs(relative) > HALF_VIEW_DEGREES - EDGE) {
                continue;
            }
            int x = center + offset(relative);
            String label = step % 2 == 1 ? "" : DIRECTIONS[step / 2];
            int half = Math.max(1, font.width(label) / 2);
            if (marked.stream().anyMatch(span -> x + half > span[0] && x - half < span[1])) {
                continue;
            }
            if (label.isEmpty()) {
                graphics.fill(x, y + 3, x + 1, y + 5, TICK_COLOR);
                continue;
            }
            int color = label.length() == 1 ? CARDINAL_COLOR : DIRECTION_COLOR;
            graphics.drawString(font, label, x - font.width(label) / 2 + 1, y + 1, color);
        }
        for (CompassPayload.Marker marker : reading.markers()) {
            int color = marker.kind() == CompassPayload.Kind.GOAL || marker.kind() == CompassPayload.Kind.LEAD ? EMBER_COLOR : MARK_COLOR;
            mark(graphics, font, symbol(marker), color, bearing(marker.x(), marker.z(), player), center, y);
        }
        String hallowed = switch (reading.shelter()) {
            case FIRE -> FIRE_MARK;
            case NONE -> null;
            case SHRINE -> SHRINE_MARK;
        };
        if (hallowed != null) {
            int left = center + HALF_WIDTH + EDGE + 2;
            graphics.fill(left, y - 1, left + font.width(hallowed) + EDGE, bottom, background);
            graphics.drawString(font, hallowed, left + 2, y + 1, reading.shelter() == CompassPayload.Shelter.FIRE ? EMBER_COLOR : MARK_COLOR);
        }
    }

    private static String symbol(CompassPayload.Marker marker) {
        return switch (marker.kind()) {
            case GOAL, LEAD -> LEAD_MARK;
            case GRAVE -> GRAVE_MARK;
            case TEAMMATE -> marker.name().isEmpty() ? "?" : marker.name().substring(0, 1).toUpperCase(Locale.ROOT);
            case TENT -> TENT_MARK;
        };
    }

    private static boolean distances(GuiGraphics graphics, Font font, CompassPayload reading, LocalPlayer player, int y, int background) {
        int center = graphics.guiWidth() / 2;
        List<int[]> taken = new ArrayList<>();
        for (CompassPayload.Marker marker : reading.markers()) {
            float relative = bearing(marker.x(), marker.z(), player);
            if (marker.kind() != CompassPayload.Kind.GOAL && Math.abs(relative) > FACING_DEGREES) {
                continue;
            }
            int distance = (int) Math.hypot(marker.x() + 0.5 - player.getX(), marker.z() + 0.5 - player.getZ());
            String text = blocks(distance);
            int x = center + (Math.abs(relative) <= HALF_VIEW_DEGREES ? offset(relative) : relative < 0 ? -HALF_WIDTH : HALF_WIDTH);
            int half = font.width(text) / 2 + 2;
            if (taken.stream().anyMatch(span -> x + half > span[0] && x - half < span[1])) {
                continue;
            }
            taken.add(new int[] {x - half, x + half});
            graphics.fill(x - half, y - 2, x + half, y + font.lineHeight, background);
            graphics.drawString(font, text, x - font.width(text) / 2 + 1, y, marker.kind() == CompassPayload.Kind.GOAL ? EMBER_COLOR : DISTANCE_COLOR);
        }
        return !taken.isEmpty();
    }

    private static void mark(GuiGraphics graphics, Font font, String symbol, int color, float relative, int center, int y) {
        boolean inView = Math.abs(relative) <= HALF_VIEW_DEGREES;
        String shown = inView ? symbol : relative < 0 ? "◀" : "▶";
        int x = inView ? center + offset(relative) : center + (relative < 0 ? -HALF_WIDTH : HALF_WIDTH);
        graphics.drawString(font, shown, x - font.width(shown) / 2 + 1, y + 1, color);
    }

    private static int offset(float relative) {
        return Math.round(relative / HALF_VIEW_DEGREES * HALF_WIDTH);
    }

    private static float bearing(int x, int z, LocalPlayer player) {
        double dx = x + 0.5 - player.getX();
        double dz = z + 0.5 - player.getZ();
        float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        return Mth.wrapDegrees(bearing - player.getYRot());
    }

    private static MutableComponent caption(CompassPayload reading) {
        return switch (reading.mode()) {
            case AIR -> Component.translatable("wayfarer.compass.air").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case BARE -> Component.empty();
            case COLD -> Component.translatable("wayfarer.compass.cold").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case DIM -> Component.translatable("wayfarer.compass.dim").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case LOST, HIDDEN -> Component.translatable("wayfarer.compass.lost").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case FAR -> Component.translatable("wayfarer.compass.ember").withStyle(ChatFormatting.WHITE)
                    .append(separator())
                    .append(Component.translatable("wayfarer.compass." + HEAT[reading.heat() + 2]).withStyle(heatColor(reading.heat())));
        };
    }

    private static Component night() {
        return Component.literal("✕ ").withStyle(ChatFormatting.RED).append(Component.translatable("wayfarer.night.title").withStyle(ChatFormatting.WHITE));
    }

    private static int line(GuiGraphics graphics, Font font, Component text, int y, int background) {
        int center = graphics.guiWidth() / 2;
        int half = font.width(text) / 2 + 4;
        graphics.fill(center - half, y - 2, center + half, y + font.lineHeight + 1, background);
        graphics.drawCenteredString(font, text, center, y, TEXT_COLOR);
        return half;
    }

    private static Component separator() {
        return Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static ChatFormatting heatColor(int heat) {
        return heat > 0 ? ChatFormatting.GOLD : heat < 0 ? ChatFormatting.BLUE : ChatFormatting.GRAY;
    }

    private static String blocks(int distance) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(distance);
    }

    @Nullable
    private static Component gaze(GazePayload gaze) {
        if (!gaze.watching()) {
            return null;
        }
        int filled = Mth.clamp(Math.round(gaze.fill() * BAR_CELLS), 0, BAR_CELLS);
        MutableComponent bar = Component.translatable("wayfarer.gaze.title").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal("  "))
                .append(Component.literal("▮".repeat(filled)).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("▯".repeat(BAR_CELLS - filled)).withStyle(ChatFormatting.DARK_GRAY));
        if (gaze.veil() == 0) {
            return bar;
        }
        return bar.append(Component.literal(" ".repeat(GAP)))
                .append(Component.translatable("wayfarer.gaze.veil", NUMERALS[gaze.veil()], gaze.capacitySeconds() / 60).withStyle(ChatFormatting.GRAY));
    }
}
