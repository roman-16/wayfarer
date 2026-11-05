package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Talk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Conversation {
    private static final int KEPT = 3;
    private static final String ELLIPSIS = "…";
    private static final int ANSWER_GAP = 6;
    private static final int ANSWER_TOP = 32;
    private static final long SAME_TALK_TICKS = 600;
    private static final long LINGER_TICKS = 40;
    private static final long FADE_TICKS = 10;
    private static final int MAX_WIDTH = 320;
    private static final int SIDE_ROOM = 16;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int ANSWER_LINE = 12;
    private static final int NAME_GAP = 5;
    private static final String NAME_SPACE = "  ";
    private static final int ABOVE_MESSAGE = 8;
    private static final int OVERLAY_MESSAGE = 68;
    private static final int OVERLAY_SHIFT = 9;
    private static final int ANSWER_X = 14;
    private static final int ANSWER_PAD = 7;
    private static final int ANSWER_GAP_ROWS = 2;
    private static final int ANSWER_WIDTH = 160;
    private static final int OPAQUE = 0xFF;
    private static final int LOGGED = 32;
    private static final int TICKS_PER_SECOND = 20;
    private static final String TICK_SOUND = "band_tick";
    private static final Deque<Shown> lines = new ArrayDeque<>();
    private static final Deque<Logged> log = new ArrayDeque<>();
    private static List<String> answers = List.of();
    private static long clock;
    private static boolean screenOpen;
    static int answersTop = Integer.MAX_VALUE;
    static int answersRight = Integer.MIN_VALUE;
    static int bandTop = Integer.MAX_VALUE;
    static int bandRight = Integer.MIN_VALUE;
    private static long logged;

    private Conversation() {}

    private record Shown(String by, String tone, Component text, long from, int ticks) {}

    public record Logged(long seq, String text) {}

    public static void hear(Talk.Subtitle subtitle) {
        if (quiet()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Wayfarer.id(TICK_SOUND)), 1, 1));
        }
        lines.addLast(new Shown(subtitle.by(), subtitle.tone(), subtitle.text(), clock, subtitle.ticks()));
        while (lines.size() > KEPT) {
            lines.removeFirst();
        }
        if (!subtitle.clip().isEmpty()) {
            Speech.band(subtitle.clip(), subtitle.source());
        }
        String said = subtitle.by().isEmpty() ? subtitle.text().getString() : "<" + subtitle.by() + "> " + subtitle.text().getString();
        note(said + (subtitle.clip().isEmpty() ? " (silent)" : String.format(Locale.ROOT, " (voiced, %.1f s)", subtitle.ticks() / (float) TICKS_PER_SECOND)));
    }

    private static boolean quiet() {
        Shown last = lines.peekLast();
        return answers.isEmpty() && (last == null || clock > last.from() + last.ticks() + LINGER_TICKS);
    }

    public static void ask(Talk.Answers offered) {
        answers = offered.choices().stream().map(Component::getString).toList();
        if (!answers.isEmpty()) {
            note("answers: " + String.join(" | ", answers));
        }
    }

    public static List<Logged> since(long seq) {
        return log.stream().filter(entry -> entry.seq() > seq).toList();
    }

    public static void forget() {
        lines.clear();
        answers = List.of();
    }

    private static void note(String text) {
        log.addLast(new Logged(++logged, text));
        while (log.size() > LOGGED) {
            log.removeFirst();
        }
    }

    static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.getConnection() == null) {
            screenOpen = false;
            return;
        }
        boolean open = minecraft.screen != null;
        if (open != screenOpen) {
            screenOpen = open;
            PacketDistributor.sendToServer(new Talk.Screen(open));
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (minecraft.level.tickRateManager().runsNormally()) {
            clock++;
        }
        if (answers.isEmpty() || minecraft.screen != null) {
            return;
        }
        KeyMapping[] slots = minecraft.options.keyHotbarSlots;
        for (int index = 0; index < Math.min(answers.size(), slots.length); index++) {
            boolean pressed = false;
            while (slots[index].consumeClick()) {
                pressed = true;
            }
            if (pressed) {
                PacketDistributor.sendToServer(new Talk.Pick(index));
                return;
            }
        }
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        answersTop = Integer.MAX_VALUE;
        answersRight = Integer.MIN_VALUE;
        bandTop = Integer.MAX_VALUE;
        bandRight = Integer.MIN_VALUE;
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        float scale = HudSize.scale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        int width = Math.round(graphics.guiWidth() / scale);
        int height = Math.round(graphics.guiHeight() / scale);
        int lift = Math.max(Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) + OVERLAY_SHIFT, OVERLAY_MESSAGE) + ABOVE_MESSAGE;
        int top = band(graphics, minecraft.font, width, Math.round((graphics.guiHeight() - lift) / scale), scale);
        answers(graphics, minecraft.font, width, height, top, scale);
        graphics.pose().popPose();
    }

    private static int band(GuiGraphics graphics, Font font, int width, int bottom, float scale) {
        Shown current = lines.peekLast();
        if (current == null) {
            return bottom;
        }
        long end = current.from() + current.ticks() + LINGER_TICKS;
        if (answers.isEmpty() && clock >= end) {
            return bottom;
        }
        float alpha = answers.isEmpty() ? Mth.clamp((end - clock) / (float) FADE_TICKS, 0, 1) : 1;
        int room = Math.min(MAX_WIDTH, width - SIDE_ROOM * 2) - PAD * 2;
        List<Row> rows = new ArrayList<>();
        List<Shown> before = new ArrayList<>(lines);
        before.removeLast();
        for (Shown shown : answers.isEmpty() ? before.subList(Math.max(0, before.size() - (KEPT - 1)), before.size()) : List.<Shown>of()) {
            if (current.from() - shown.from() <= SAME_TALK_TICKS) {
                rows.add(new Row(cut(font, text(shown), room), true));
            }
        }
        font.split(text(current), room).forEach(row -> rows.add(new Row(row, false)));
        int widest = rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        int bandWidth = widest + PAD * 2;
        int bandHeight = rows.size() * LINE + PAD * 2 - 2;
        int left = (width - bandWidth) / 2;
        int top = bottom - bandHeight;
        bandTop = Math.round(top * scale);
        bandRight = Math.round((left + bandWidth) * scale);
        graphics.flush();
        graphics.setColor(1, 1, 1, alpha);
        graphics.blitSprite(Stamp.PANEL_CLEAR, left, top, bandWidth, bandHeight);
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        int shade = Math.max(4, Math.round(OPAQUE * alpha));
        int y = top + PAD;
        for (Row row : rows) {
            int opacity = row.faint() ? Math.min(shade, Stamp.FAINT) : shade;
            graphics.drawString(font, row.text(), left + PAD, y, Stamp.withAlpha(row.faint() ? Stamp.QUIET : Stamp.BONE, opacity), true);
            y += LINE;
        }
        return top;
    }

    private static FormattedCharSequence cut(Font font, Component text, int room) {
        if (font.width(text) <= room) {
            return text.getVisualOrderText();
        }
        FormattedText kept = font.substrByWidth(text, room - font.width(ELLIPSIS));
        return Language.getInstance().getVisualOrder(FormattedText.composite(kept, Component.literal(ELLIPSIS)));
    }

    private static Component text(Shown shown) {
        if (shown.by().isEmpty()) {
            return shown.text().copy().withColor(Stamp.rgb(Stamp.QUIET));
        }
        MutableComponent named = Component.literal(shown.by().toUpperCase(Locale.ROOT)).withColor(Stamp.rgb(Stamp.speaker(shown.tone())));
        return named.append(Component.literal(NAME_SPACE)).append(shown.text());
    }

    private static void answers(GuiGraphics graphics, Font font, int width, int height, int bandTop, float scale) {
        if (answers.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int x = width / 2 + ANSWER_X;
        int keyWidth = 0;
        for (int index = 0; index < answers.size(); index++) {
            keyWidth = Math.max(keyWidth, Stamp.keycapWidth(font, key(minecraft, index).getString()));
        }
        int textX = x + ANSWER_PAD + keyWidth + NAME_GAP;
        int room = Math.min(ANSWER_WIDTH, width - textX - SIDE_ROOM - ANSWER_PAD);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int rows = 0;
        for (String answer : answers) {
            List<FormattedCharSequence> split = font.split(Component.literal(answer), room);
            wrapped.add(split);
            rows += split.size();
        }
        int tall = rows * LINE + answers.size() * (ANSWER_LINE - LINE) + (answers.size() - 1) * ANSWER_GAP_ROWS + ANSWER_PAD * 2;
        int broadest = wrapped.stream().flatMap(List::stream).mapToInt(font::width).max().orElse(0);
        int right = textX + broadest + ANSWER_PAD;
        int top = Math.max(ANSWER_TOP, Math.min(height / 2 - tall / 2, bandTop - ANSWER_GAP - tall));
        answersTop = Math.round(top * scale);
        answersRight = Math.round(right * scale);
        graphics.blitSprite(Stamp.PANEL_CLEAR, x, top, right - x, tall);
        int y = top + ANSWER_PAD + 2;
        for (int index = 0; index < answers.size(); index++) {
            Stamp.keycap(graphics, font, key(minecraft, index).getString(), x + ANSWER_PAD, y);
            for (FormattedCharSequence row : wrapped.get(index)) {
                graphics.drawString(font, row, textX, y, Stamp.BONE, true);
                y += LINE;
            }
            y += ANSWER_LINE - LINE + ANSWER_GAP_ROWS;
        }
    }

    private static Component key(Minecraft minecraft, int index) {
        return minecraft.options.keyHotbarSlots[index].getTranslatedKeyMessage();
    }

    private record Row(FormattedCharSequence text, boolean faint) {}
}
