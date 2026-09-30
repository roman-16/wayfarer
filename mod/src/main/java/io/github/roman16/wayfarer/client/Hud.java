package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.NightPayload;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

public final class Hud {
    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] HEAT = {"guttering", "fading", "listening", "glowing", "flaring"};
    private static final String[] NUMERALS = {"", "I", "II", "III"};
    private static final int[] TEAMMATE_COLORS = {0x55FF55, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFAA00, 0xAAAAFF};
    private static final int BAR_CELLS = 8;
    private static final int BACKGROUND = 0x80000000;
    private static final int TICK_COLOR = 0xFF808080;
    private static final int CENTER_COLOR = 0xFFFFFFFF;
    private static final int DIRECTION_COLOR = 0xFFFFFF;
    private static final int CARDINAL_COLOR = 0xFFD700;
    private static final int EYE_COLOR = 0x55FFFF;
    private static final int STRONGHOLD_COLOR = 0xFF55FF;
    private static final int GRAVE_COLOR = 0xE0E0E0;
    private static final int LEAD_COLOR = 0xFFAA55;
    private static final String LEAD_MARK = "✦";
    private static final int GAP = "    ".length();
    private static final int HALF_WIDTH = 90;
    private static final float HALF_VIEW_DEGREES = 90;
    private static final float DIRECTION_STEP = 45;
    private static final int RIBBON_HEIGHT = 11;
    private static final int POINTER = 4;
    private static final int LINE = 12;
    private static final int TOP = 4;
    private static final int EDGE = 4;

    private Hud() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        CompassPayload reading = HudState.compass;
        int y = TOP;
        if (reading.mode() != CompassPayload.Mode.HIDDEN) {
            ribbon(graphics, font, reading, player, y);
            y += RIBBON_HEIGHT + POINTER;
            line(graphics, font, caption(reading).append(night(HudState.night)), y);
            y += LINE;
            for (Component lead : leads(reading, player)) {
                line(graphics, font, lead, y);
                y += LINE;
            }
        } else if (HudState.night.hard()) {
            line(graphics, font, night(HudState.night), y);
            y += LINE;
        }
        Component gaze = gaze(HudState.gaze);
        if (gaze != null) {
            line(graphics, font, gaze, y);
        }
    }

    private static void ribbon(GuiGraphics graphics, Font font, CompassPayload reading, LocalPlayer player, int y) {
        int center = graphics.guiWidth() / 2;
        int bottom = y + RIBBON_HEIGHT - 1;
        graphics.fill(center - HALF_WIDTH - EDGE, y - 1, center + HALF_WIDTH + EDGE, bottom, BACKGROUND);
        graphics.fill(center - 1, bottom, center + 2, bottom + 1, CENTER_COLOR);
        graphics.fill(center, bottom + 1, center + 1, bottom + 2, CENTER_COLOR);
        float yaw = player.getYRot();
        for (int step = 0; step < DIRECTIONS.length * 2; step++) {
            float relative = Mth.wrapDegrees(step * DIRECTION_STEP / 2 - yaw);
            if (Math.abs(relative) > HALF_VIEW_DEGREES - EDGE) {
                continue;
            }
            int x = center + offset(relative);
            if (step % 2 == 1) {
                graphics.fill(x, y + 3, x + 1, y + 5, TICK_COLOR);
                continue;
            }
            String label = DIRECTIONS[step / 2];
            int color = label.length() == 1 ? CARDINAL_COLOR : DIRECTION_COLOR;
            graphics.drawString(font, label, x - font.width(label) / 2 + 1, y + 1, color);
        }
        for (CompassPayload.Marker marker : reading.markers()) {
            String symbol = switch (marker.kind()) {
                case GRAVE -> "✝";
                case LEAD -> LEAD_MARK;
                case TEAMMATE -> "☻";
            };
            int color = switch (marker.kind()) {
                case GRAVE -> GRAVE_COLOR;
                case LEAD -> LEAD_COLOR;
                case TEAMMATE -> TEAMMATE_COLORS[Math.floorMod(marker.name().hashCode(), TEAMMATE_COLORS.length)];
            };
            mark(graphics, font, symbol, color, bearing(marker.x(), marker.z(), player), center, y);
        }
        if (reading.mode() == CompassPayload.Mode.NEAR) {
            boolean stronghold = reading.target().equals(Compass.STRONGHOLD);
            mark(graphics, font, stronghold ? "◆" : "◇", stronghold ? STRONGHOLD_COLOR : EYE_COLOR,
                    bearing(reading.targetX(), reading.targetZ(), player), center, y);
        }
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

    private static List<Component> leads(CompassPayload reading, LocalPlayer player) {
        int exact = Balance.INSTANCE.compass().exactBlocks();
        List<Component> lines = new ArrayList<>();
        for (CompassPayload.Marker marker : reading.markers()) {
            int distance = (int) Math.hypot(marker.x() + 0.5 - player.getX(), marker.z() + 0.5 - player.getZ());
            if (marker.kind() == CompassPayload.Kind.LEAD && distance <= exact) {
                lines.add(Component.literal(LEAD_MARK + " ").withStyle(style -> style.withColor(LEAD_COLOR))
                        .append(Component.literal(marker.name()).withStyle(ChatFormatting.WHITE))
                        .append(separator())
                        .append(Component.translatable("wayfarer.compass.exact", blocks(distance))));
            }
        }
        return lines;
    }

    private static MutableComponent caption(CompassPayload reading) {
        return switch (reading.mode()) {
            case AIR -> Component.translatable("wayfarer.compass.air").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case DIM -> Component.translatable("wayfarer.compass.dim").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case LOST, HIDDEN -> Component.translatable("wayfarer.compass.lost").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case FAR -> target(reading)
                    .append(separator())
                    .append(Component.translatable("wayfarer.compass." + HEAT[reading.heat() + 2]).withStyle(heatColor(reading.heat())))
                    .append(separator())
                    .append(Component.translatable("wayfarer.compass.about", blocks(reading.distance())));
            case NEAR -> target(reading)
                    .append(separator())
                    .append(Component.translatable("wayfarer.compass.exact", blocks(reading.distance())));
        };
    }

    private static MutableComponent target(CompassPayload reading) {
        if (reading.target().equals(Compass.STRONGHOLD)) {
            return Component.literal("◆ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.translatable("wayfarer.compass.stronghold").withStyle(ChatFormatting.WHITE));
        }
        return Component.literal("◇ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("wayfarer.compass.shard").withStyle(ChatFormatting.WHITE));
    }

    private static Component night(NightPayload night) {
        MutableComponent icons = Component.empty();
        if (!night.hard()) {
            return icons;
        }
        icons.append(Component.literal(" ".repeat(GAP)));
        if (night.bloodMoon()) {
            icons.append(Component.literal("☾").withStyle(ChatFormatting.RED));
        }
        if (night.horde()) {
            icons.append(Component.literal(night.bloodMoon() ? " ✕" : "✕").withStyle(ChatFormatting.RED));
        }
        return icons;
    }

    private static void line(GuiGraphics graphics, Font font, Component text, int y) {
        int center = graphics.guiWidth() / 2;
        int half = font.width(text) / 2 + 4;
        graphics.fill(center - half, y - 2, center + half, y + font.lineHeight + 1, BACKGROUND);
        graphics.drawCenteredString(font, text, center, y, 0xFFFFFF);
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
