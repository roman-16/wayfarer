package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import java.text.NumberFormat;
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
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] HEAT = {"much_colder", "colder", "listening", "warmer", "much_warmer"};
    private static final String[] NUMERALS = {"", "I", "II", "III"};
    private static final int BAR_CELLS = 8;
    private static final int BACKGROUND = 0x80000000;
    private static final int GAP = "    ".length();
    private static final int LINE = 12;
    private static final int TOP = 4;

    private Hud() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        int y = TOP;
        Component compass = compass(HudState.compass, player);
        if (compass != null) {
            line(graphics, minecraft.font, compass, y);
            y += LINE;
        }
        Component gaze = gaze(HudState.gaze);
        if (gaze != null) {
            line(graphics, minecraft.font, gaze, y);
        }
    }

    private static void line(GuiGraphics graphics, Font font, Component text, int y) {
        int center = graphics.guiWidth() / 2;
        int half = font.width(text) / 2 + 4;
        graphics.fill(center - half, y - 2, center + half, y + font.lineHeight + 1, BACKGROUND);
        graphics.drawCenteredString(font, text, center, y, 0xFFFFFF);
    }

    private static Component compass(CompassPayload reading, LocalPlayer player) {
        return switch (reading.mode()) {
            case SILENT -> null;
            case AIR -> Component.translatable("wayfarer.compass.air").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case FAR -> target(reading)
                    .append(spacing())
                    .append(Component.translatable("wayfarer.compass." + HEAT[reading.heat() + 2]).withStyle(heatColor(reading.heat())))
                    .append(spacing())
                    .append(Component.translatable("wayfarer.compass.about", blocks(reading.distance())));
            case NEAR -> target(reading)
                    .append(spacing())
                    .append(Component.literal(arrow(reading, player)).withStyle(ChatFormatting.GOLD))
                    .append(spacing())
                    .append(Component.translatable("wayfarer.compass.exact", blocks(reading.distance())));
        };
    }

    private static MutableComponent target(CompassPayload reading) {
        if (reading.target().equals(Compass.STRONGHOLD)) {
            return Component.literal("◆ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.translatable("wayfarer.compass.stronghold").withStyle(ChatFormatting.WHITE));
        }
        String path = reading.target().getPath();
        String eye = path.substring(path.lastIndexOf('/') + 1);
        return Component.literal("◇ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("item.endrem." + eye + "_eye").withStyle(ChatFormatting.WHITE));
    }

    private static Component spacing() {
        return Component.literal(" ".repeat(GAP));
    }

    private static ChatFormatting heatColor(int heat) {
        return heat > 0 ? ChatFormatting.GOLD : heat < 0 ? ChatFormatting.BLUE : ChatFormatting.GRAY;
    }

    private static String arrow(CompassPayload reading, LocalPlayer player) {
        double dx = reading.targetX() + 0.5 - player.getX();
        double dz = reading.targetZ() + 0.5 - player.getZ();
        float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float relative = Mth.wrapDegrees(bearing - player.getYRot());
        return ARROWS[Math.floorMod(Math.round(relative / 45), ARROWS.length)];
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
        return bar.append(spacing())
                .append(Component.translatable("wayfarer.gaze.veil", NUMERALS[gaze.veil()], gaze.capacitySeconds() / 60).withStyle(ChatFormatting.GRAY));
    }
}
