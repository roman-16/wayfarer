package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.client.stamp.Inline;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Banners;
import io.github.roman16.wayfarer.story.Tasks;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class Hud {
    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] HEAT = {"guttering", "fading", "listening", "glowing", "flaring"};
    private static final String[] NUMERALS = {"", "I", "II", "III"};
    private static final int BAR_CELLS = 8;
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 6;
    private static final int HALF_WIDTH = 90;
    private static final float HALF_VIEW_DEGREES = 90;
    private static final float FACING_DEGREES = 8;
    private static final float DIRECTION_STEP = 45;
    private static final int BAND_SIDE = HALF_WIDTH + 12;
    private static final int BAND_HEIGHT = 15;
    private static final int BAND_TEXT = 4;
    private static final int POINTER_WIDTH = 5;
    private static final int POINTER_HEIGHT = 3;
    private static final int PLATE_HEIGHT = 13;
    private static final int PLATE_PAD = 4;
    private static final int PLATE_TEXT = 3;
    private static final int GAP = 2;
    private static final int TOP = 3;
    private static final int EDGE = 4;
    private static final int BOSS_BAR = 6;
    private static final int EFFECT_ROW = 26;
    private static final int EFFECT_COLUMN = 25;
    private static final int PANEL_WIDTH = 172;
    private static final int PANEL_PAD = 8;
    private static final int LABEL_LINE = 11;
    private static final int TEXT_LINE = 10;
    private static final int TASK_LINE = 12;
    private static final int TASK_INDENT = 8;
    private static final int TITLE_INDENT = Stamp.MARK_WIDTH + 4;
    private static final int SUBTITLE_ROOM = 96;
    private static final int BANNER_HEIGHT = 34;
    private static final int BANNER_CAP = 9;
    private static final int BANNER_PAD = 10;
    private static final int BANNER_LABEL = 6;
    private static final int BANNER_TITLE = 17;
    private static final long BANNER_MILLIS = 4000;
    private static final int BANNER_DROP = 8;
    private static final int OPAQUE_TEXT = 4;
    private static int bossBottom;
    @Nullable
    private static Banners.Payload banner;
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

    public static Optional<Banners.Payload> showing() {
        return Optional.ofNullable(banner).filter(shown -> Util.getMillis() - bannerFrom <= BANNER_MILLIS);
    }

    public static void clearBanners() {
        HudState.banners.clear();
        banner = null;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        CompassPayload reading = HudState.compass;
        boolean compass = reading.mode() != CompassPayload.Mode.HIDDEN;
        int y = Math.max(TOP, bossBottom + EDGE);
        int half = 0;
        if (compass && graphics.guiWidth() - EFFECT_COLUMN * effectColumns(player) < graphics.guiWidth() / 2 + BAND_SIDE + EDGE * 2 + PLATE_HEIGHT) {
            y = Math.max(y, TOP + effectRows(player) * EFFECT_ROW);
        }
        if (compass) {
            band(graphics, font, reading, player, y);
            y += BAND_HEIGHT + POINTER_HEIGHT;
            if (distances(graphics, font, reading, player, y)) {
                y += PLATE_HEIGHT + GAP;
            }
            half = BAND_SIDE + EDGE + PLATE_HEIGHT;
        }
        Urgent urgent = urgent(reading, compass);
        if (urgent != null) {
            half = Math.max(half, urgent(graphics, font, urgent, y));
            y += PLATE_HEIGHT + GAP;
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
            panel(graphics, font, HudState.panel, top, bottom);
        }
        banner(graphics, font, y + GAP);
    }

    private record Run(Component text, int color) {}

    private record Urgent(List<Run> runs, float meter, @Nullable Component after) {}

    @Nullable
    private static Urgent urgent(CompassPayload reading, boolean compass) {
        GazePayload gaze = HudState.gaze;
        if (gaze.watching()) {
            Component veil = gaze.veil() == 0 ? null : Component.translatable("wayfarer.gaze.veil", NUMERALS[gaze.veil()], gaze.capacitySeconds() / 60);
            return new Urgent(List.of(new Run(Component.translatable("wayfarer.gaze.title"), Stamp.GAZE)), Mth.clamp(gaze.fill(), 0, 1), veil);
        }
        if (HudState.night.sleepless()) {
            return new Urgent(List.of(new Run(Component.translatable("wayfarer.night.title"), Stamp.DANGER)), -1, null);
        }
        if (!compass) {
            return null;
        }
        return switch (reading.mode()) {
            case BARE -> null;
            case AIR, COLD, DIM, LOST, HIDDEN -> new Urgent(List.of(new Run(Component.translatable(caption(reading.mode())), Stamp.LABEL)), -1, null);
            case FAR -> new Urgent(List.of(new Run(Component.translatable("wayfarer.compass.ember"), Stamp.LABEL), new Run(Component.literal(" · "), Stamp.DIM),
                    new Run(Component.translatable("wayfarer.compass." + HEAT[reading.heat() + 2]), heatColor(reading.heat()))), -1, null);
        };
    }

    private static String caption(CompassPayload.Mode mode) {
        return switch (mode) {
            case AIR -> "wayfarer.compass.air";
            case COLD -> "wayfarer.compass.cold";
            case DIM -> "wayfarer.compass.dim";
            default -> "wayfarer.compass.lost";
        };
    }

    private static int urgent(GuiGraphics graphics, Font font, Urgent urgent, int y) {
        int width = urgent.runs().stream().mapToInt(run -> Stamp.labelWidth(font, run.text().getString())).sum();
        if (urgent.meter() >= 0) {
            width += PLATE_PAD + BAR_CELLS * (CELL_WIDTH + 1) - 1;
        }
        if (urgent.after() != null) {
            width += PLATE_PAD + font.width(urgent.after());
        }
        int center = graphics.guiWidth() / 2;
        int left = center - width / 2 - PLATE_PAD;
        graphics.blitSprite(Stamp.PLATE, left, y, width + PLATE_PAD * 2, PLATE_HEIGHT);
        int x = left + PLATE_PAD;
        for (Run run : urgent.runs()) {
            x = Stamp.label(graphics, font, run.text().getString(), x, y + PLATE_TEXT, run.color(), true) + 1;
        }
        if (urgent.meter() >= 0) {
            x += PLATE_PAD - 1;
            int filled = Math.round(urgent.meter() * BAR_CELLS);
            for (int cell = 0; cell < BAR_CELLS; cell++) {
                int top = y + (PLATE_HEIGHT - CELL_HEIGHT) / 2;
                boolean on = cell < filled;
                graphics.fill(x, top, x + CELL_WIDTH, top + CELL_HEIGHT, on ? Stamp.ramp("gaze", 2) : Stamp.ramp("ash", 0));
                graphics.fill(x, top, x + CELL_WIDTH, top + 1, on ? Stamp.ramp("gaze", 3) : Stamp.ramp("ash", 3));
                x += CELL_WIDTH + 1;
            }
        }
        if (urgent.after() != null) {
            graphics.drawString(font, urgent.after(), x + PLATE_PAD - 1, y + PLATE_TEXT, Stamp.QUIET, true);
        }
        return width / 2 + PLATE_PAD;
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
        float slide = Math.min(1, Math.min(shown, BANNER_MILLIS - shown) / (float) Stamp.KINDLE_MILLIS);
        float eased = slide * slide * (3 - 2 * slide);
        List<Inline.Piece> label = Inline.lines(font, banner.label(), Integer.MAX_VALUE).stream().findFirst().orElse(List.of());
        String title = banner.title().getString();
        int labelWidth = label.stream().mapToInt(piece -> piece.key() ? Stamp.keycapWidth(font, piece.text()) + 2 : Stamp.labelWidth(font, piece.text())).sum()
                + Stamp.MARK_WIDTH + 4;
        int width = Math.max(labelWidth, Stamp.titleWidth(font, title)) + (BANNER_CAP + BANNER_PAD) * 2;
        int x = graphics.guiWidth() / 2 - width / 2;
        int top = y + Math.round((eased - 1) * BANNER_DROP);
        int alpha = Math.max(OPAQUE_TEXT, Math.round(eased * 255));
        graphics.flush();
        graphics.setColor(1, 1, 1, eased);
        graphics.blitSprite(Stamp.STRIP, x, top, width, BANNER_HEIGHT);
        int markLeft = graphics.guiWidth() / 2 - labelWidth / 2;
        Stamp.mark(graphics, markLeft, top + BANNER_LABEL - 1, false);
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        int cursor = markLeft + Stamp.MARK_WIDTH + 4;
        for (Inline.Piece piece : label) {
            if (piece.key()) {
                cursor = Stamp.keycap(graphics, font, piece.text(), cursor + 1, top + BANNER_LABEL) + 1;
            } else {
                cursor = Stamp.label(graphics, font, piece.text(), cursor, top + BANNER_LABEL, Stamp.withAlpha(Stamp.LABEL, alpha), true) + 1;
            }
        }
        Stamp.title(graphics, font, title, graphics.guiWidth() / 2 - Stamp.titleWidth(font, title) / 2, top + BANNER_TITLE, Stamp.withAlpha(Stamp.GOLD, alpha), true);
    }

    private sealed interface Row permits Label, Title, Text, Task {
        int height();
    }

    private record Label(String text) implements Row {
        @Override
        public int height() {
            return LABEL_LINE;
        }
    }

    private record Title(String text) implements Row {
        @Override
        public int height() {
            return LABEL_LINE;
        }
    }

    private record Text(List<Inline.Piece> line) implements Row {
        @Override
        public int height() {
            return TEXT_LINE;
        }
    }

    private record Task(List<Inline.Piece> line, boolean first, boolean done) implements Row {
        @Override
        public int height() {
            return TASK_LINE;
        }
    }

    private static void panel(GuiGraphics graphics, Font font, Tasks.Panel panel, int top, int bottom) {
        int width = PANEL_WIDTH - PANEL_PAD * 2;
        List<Row> rows = new ArrayList<>();
        if (!panel.kind().getString().isEmpty()) {
            rows.add(new Label(panel.kind().getString()));
        }
        if (!panel.title().getString().isEmpty()) {
            rows.add(new Title(panel.title().getString()));
        }
        Inline.lines(font, panel.objective(), width).forEach(line -> rows.add(new Text(line)));
        for (Component task : panel.open()) {
            task(rows, font, task, width, false);
        }
        for (Component task : panel.done()) {
            task(rows, font, task, width, true);
        }
        int room = bottom - top - PANEL_PAD * 2;
        int used = 0;
        int shown = 0;
        while (shown < rows.size() && used + rows.get(shown).height() <= room) {
            used += rows.get(shown).height();
            shown++;
        }
        if (shown > 0 && rows.get(shown - 1) instanceof Label) {
            used -= rows.get(--shown).height();
        }
        if (shown == 0) {
            return;
        }
        int right = graphics.guiWidth() - EDGE;
        int left = right - PANEL_WIDTH;
        graphics.blitSprite(Stamp.PANEL_CLEAR, left, top, PANEL_WIDTH, used + PANEL_PAD * 2 - 2);
        int y = top + PANEL_PAD;
        int x = left + PANEL_PAD;
        for (Row row : rows.subList(0, shown)) {
            switch (row) {
                case Label label -> Stamp.label(graphics, font, label.text(), x, y, Stamp.LABEL, true);
                case Title title -> {
                    Stamp.mark(graphics, x, y, false);
                    graphics.drawString(font, title.text(), x + TITLE_INDENT, y, Stamp.GOLD, true);
                }
                case Text text -> Inline.draw(graphics, font, text.line(), x, y, Stamp.BONE, true);
                case Task task -> {
                    if (task.first()) {
                        Stamp.ring(graphics, x, y + 1, task.done(), task.done() ? Stamp.DIM : Stamp.QUIET);
                    }
                    Inline.draw(graphics, font, task.line(), x + TASK_INDENT, y, task.done() ? Stamp.DIM : Stamp.BONE, true);
                }
            }
            y += row.height();
        }
    }

    private static void task(List<Row> rows, Font font, Component text, int width, boolean done) {
        List<List<Inline.Piece>> lines = Inline.lines(font, text, width - TASK_INDENT);
        for (int index = 0; index < lines.size(); index++) {
            rows.add(new Task(lines.get(index), index == 0, done));
        }
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

    private static void band(GuiGraphics graphics, Font font, CompassPayload reading, LocalPlayer player, int y) {
        int center = graphics.guiWidth() / 2;
        graphics.blitSprite(Stamp.BAND, center - BAND_SIDE, y, BAND_SIDE * 2, BAND_HEIGHT);
        Stamp.tinted(graphics, Stamp.compass("pointer"), center - POINTER_WIDTH / 2, y + BAND_HEIGHT - 1, POINTER_WIDTH, POINTER_HEIGHT, Stamp.GOLD);
        int text = y + BAND_TEXT;
        List<int[]> marked = new ArrayList<>();
        for (CompassPayload.Marker marker : reading.markers()) {
            float relative = bearing(marker.x(), marker.z(), player);
            if (Math.abs(relative) <= HALF_VIEW_DEGREES) {
                int x = center + offset(relative);
                int half = markWidth(font, marker) / 2 + 1;
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
                graphics.fill(x, text + 3, x + 1, text + 5, Stamp.DIM);
                continue;
            }
            graphics.drawString(font, label, x - font.width(label) / 2 + 1, text, label.length() == 1 ? Stamp.BONE : Stamp.QUIET, true);
        }
        for (CompassPayload.Marker marker : reading.markers()) {
            marker(graphics, font, marker, bearing(marker.x(), marker.z(), player), center, text);
        }
        ResourceLocation hallowed = switch (reading.shelter()) {
            case FIRE -> Stamp.compass("fire");
            case NONE -> null;
            case SHRINE -> Stamp.compass("hall");
        };
        if (hallowed != null) {
            int left = center + BAND_SIDE + GAP;
            graphics.blitSprite(Stamp.PLATE, left, y + 1, PLATE_HEIGHT, PLATE_HEIGHT);
            int size = reading.shelter() == CompassPayload.Shelter.FIRE ? 5 : 7;
            Stamp.tinted(graphics, hallowed, left + (PLATE_HEIGHT - size) / 2, y + 4, size, 7,
                    reading.shelter() == CompassPayload.Shelter.FIRE ? Stamp.GOLD : Stamp.BONE);
        }
    }

    private static int markWidth(Font font, CompassPayload.Marker marker) {
        return switch (marker.kind()) {
            case GOAL, LEAD -> Stamp.MARK_WIDTH;
            case GRAVE -> 5;
            case TEAMMATE -> font.width(initial(marker));
            case TENT -> 7;
        };
    }

    private static String initial(CompassPayload.Marker marker) {
        return marker.name().isEmpty() ? "?" : marker.name().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static void marker(GuiGraphics graphics, Font font, CompassPayload.Marker marker, float relative, int center, int y) {
        boolean lead = marker.kind() == CompassPayload.Kind.GOAL || marker.kind() == CompassPayload.Kind.LEAD;
        if (Math.abs(relative) > HALF_VIEW_DEGREES) {
            int x = center + (relative < 0 ? -HALF_WIDTH - 6 : HALF_WIDTH + 2);
            Stamp.tinted(graphics, Stamp.compass(relative < 0 ? "left" : "right"), x, y, 4, 7, lead ? Stamp.GOLD : Stamp.QUIET);
            return;
        }
        int x = center + offset(relative);
        switch (marker.kind()) {
            case GOAL, LEAD -> Stamp.mark(graphics, x - Stamp.MARK_WIDTH / 2, y, false);
            case GRAVE -> Stamp.tinted(graphics, Stamp.compass("grave"), x - 2, y + 1, 5, 6, Stamp.BONE);
            case TEAMMATE -> graphics.drawString(font, initial(marker), x - font.width(initial(marker)) / 2 + 1, y, Stamp.BONE, true);
            case TENT -> Stamp.tinted(graphics, Stamp.compass("tent"), x - 3, y + 2, 7, 5, Stamp.BONE);
        }
    }

    private static boolean distances(GuiGraphics graphics, Font font, CompassPayload reading, LocalPlayer player, int y) {
        int center = graphics.guiWidth() / 2;
        List<int[]> taken = new ArrayList<>();
        for (CompassPayload.Marker marker : reading.markers()) {
            float relative = bearing(marker.x(), marker.z(), player);
            if (marker.kind() != CompassPayload.Kind.GOAL && Math.abs(relative) > FACING_DEGREES) {
                continue;
            }
            int distance = (int) Math.hypot(marker.x() + 0.5 - player.getX(), marker.z() + 0.5 - player.getZ());
            String text = NumberFormat.getIntegerInstance(Locale.ROOT).format(distance);
            int x = center + (Math.abs(relative) <= HALF_VIEW_DEGREES ? offset(relative) : relative < 0 ? -HALF_WIDTH : HALF_WIDTH);
            int half = font.width(text) / 2 + PLATE_PAD;
            if (taken.stream().anyMatch(span -> x + half > span[0] && x - half < span[1])) {
                continue;
            }
            taken.add(new int[] {x - half, x + half});
            graphics.blitSprite(Stamp.PLATE, x - half, y, half * 2 + 1, PLATE_HEIGHT);
            graphics.drawString(font, text, x - font.width(text) / 2 + 1, y + PLATE_TEXT, marker.kind() == CompassPayload.Kind.GOAL ? Stamp.GOLD : Stamp.BONE, true);
        }
        return !taken.isEmpty();
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

    private static int heatColor(int heat) {
        return heat > 0 ? Stamp.GOLD : heat < 0 ? Stamp.ramp("frost", 3) : Stamp.QUIET;
    }
}
