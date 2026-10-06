package io.github.roman16.wayfarer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Journal;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

public final class JournalScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int TOP = 48;
    private static final int LINE = 11;
    private static final int TAB_WIDTH = 96;
    private static final int TAB_HEIGHT = 20;
    private static final int TAKE_WIDTH = 64;
    private static final int HEAD_IN_WIDTH = 128;
    private static final int GAP = 4;
    private static final int TITLE = 0xFFD27A;
    private static final int TEXT = 0xFFFFFF;
    private static final int QUIET = 0xA0A0A0;
    private static final int PAPER = 0xC0101010;
    private static String latest = "{}";
    private static Tab open = Tab.QUESTS;
    private String shown = "";
    private int scroll;
    private int contentHeight;
    private List<Waiting> waiting = List.of();
    private int header;

    private JournalScreen() {
        super(Component.translatable("wayfarer.journal"));
    }

    public static void open() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new JournalScreen());
        PacketDistributor.sendToServer(new Tasks.Did("journal"));
    }

    public static void show(String view) {
        latest = view;
    }

    private enum Tab {
        QUESTS("wayfarer.journal.quests"),
        STORY("wayfarer.journal.story"),
        PEOPLE("wayfarer.journal.people"),
        WORDS("wayfarer.journal.words");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    @Override
    protected void init() {
        shown = latest;
        int y = TOP;
        for (Tab tab : Tab.values()) {
            Button button = Button.builder(Component.translatable(tab.key), pressed -> {
                open = tab;
                scroll = 0;
                rebuildWidgets();
            }).bounds(MARGIN, y, TAB_WIDTH, TAB_HEIGHT).build();
            button.active = tab != open;
            addRenderableWidget(button);
            y += TAB_HEIGHT + GAP;
        }
        if (ModList.get().isLoaded("journeymap")) {
            addRenderableWidget(Button.builder(Component.translatable("wayfarer.journal.map"), pressed -> MapLink.open())
                    .bounds(MARGIN, y, TAB_WIDTH, TAB_HEIGHT).build());
        }
        waiting = new ArrayList<>();
        header = 0;
        if (open == Tab.QUESTS) {
            int left = MARGIN * 2 + TAB_WIDTH;
            int at = TOP;
            if (view().has("headIn") && view().get("headIn").getAsBoolean()) {
                addRenderableWidget(Button.builder(Component.translatable("wayfarer.journal.head_in"), pressed -> {
                    PacketDistributor.sendToServer(new Caravan.HeadIn());
                    onClose();
                }).bounds(width - MARGIN - HEAD_IN_WIDTH, at, HEAD_IN_WIDTH, TAB_HEIGHT).build());
                at += TAB_HEIGHT + GAP;
            }
            for (JsonElement element : view().getAsJsonArray("rewards")) {
                JsonObject reward = element.getAsJsonObject();
                if (!reward.get("claimed").getAsBoolean()) {
                    String key = reward.get("key").getAsString();
                    addRenderableWidget(Button.builder(Component.translatable("wayfarer.journal.claim"),
                            pressed -> PacketDistributor.sendToServer(new Journal.Claim(key)))
                            .bounds(width - MARGIN - TAKE_WIDTH, at, TAKE_WIDTH, TAB_HEIGHT).build());
                    List<FormattedCharSequence> lines = font.split(Component.literal(reward.get("title").getAsString() + ": " + reward.get("text").getAsString()),
                            width - MARGIN - TAKE_WIDTH - GAP - left);
                    waiting.add(new Waiting(lines, at));
                    at += Math.max(TAB_HEIGHT, lines.size() * LINE) + GAP;
                }
            }
            header = at - TOP;
        }
    }

    private record Waiting(List<FormattedCharSequence> lines, int top) {}

    @Override
    public void tick() {
        if (!latest.equals(shown)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, MARGIN, TOP - LINE * 2, TITLE);
        int left = MARGIN * 2 + TAB_WIDTH;
        int right = width - MARGIN;
        int bottom = height - MARGIN;
        graphics.fill(left - GAP, TOP - GAP, right + GAP, bottom + GAP, PAPER);
        for (Waiting reward : waiting) {
            int y = reward.top() + (reward.lines().size() == 1 ? (TAB_HEIGHT - font.lineHeight) / 2 : 0);
            for (FormattedCharSequence line : reward.lines()) {
                graphics.drawString(font, line, left, y, TITLE);
                y += LINE;
            }
        }
        int top = TOP + header;
        List<Row> rows = rows(right - left);
        contentHeight = rows.size() * LINE;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - top)));
        graphics.enableScissor(left, top, right, bottom);
        int y = top - scroll;
        for (Row row : rows) {
            if (y + LINE > top && y < bottom) {
                graphics.drawString(font, row.text(), left + row.indent(), y, row.color());
            }
            y += LINE;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) (scrollY * LINE * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (WayfarerClient.JOURNAL.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(FormattedCharSequence text, int indent, int color) {}

    private List<Row> rows(int width) {
        JsonObject view = view();
        List<Row> rows = new ArrayList<>();
        switch (open) {
            case QUESTS -> {
                for (boolean done : new boolean[] {false, true}) {
                    for (JsonElement element : view.getAsJsonArray("quests")) {
                        JsonObject quest = element.getAsJsonObject();
                        if (quest.get("done").getAsBoolean() != done) {
                            continue;
                        }
                        String kind = quest.get("kind").getAsString().equals("main") ? "wayfarer.journal.main" : "wayfarer.journal.side";
                        Component heading = Component.translatable(kind).append(" ").append(quest.get("title").getAsString());
                        add(rows, done ? heading.copy().withStyle(ChatFormatting.STRIKETHROUGH) : heading, 0, done ? QUIET : TITLE, width);
                        if (!done) {
                            add(rows, Component.literal(quest.get("summary").getAsString()), MARGIN, QUIET, width);
                            add(rows, Component.literal("> " + quest.get("objective").getAsString()), MARGIN, TEXT, width);
                            for (JsonElement found : quest.getAsJsonArray("found")) {
                                add(rows, Component.literal("- " + found.getAsString()), MARGIN * 2, QUIET, width);
                            }
                        }
                        rows.add(new Row(FormattedCharSequence.EMPTY, 0, TEXT));
                    }
                }
                for (JsonElement element : view.getAsJsonArray("rewards")) {
                    JsonObject reward = element.getAsJsonObject();
                    if (reward.get("claimed").getAsBoolean()) {
                        add(rows, Component.literal(reward.get("title").getAsString() + ": " + reward.get("text").getAsString() + " ")
                                .append(Component.translatable("wayfarer.journal.claimed")), 0, QUIET, width);
                    }
                }
            }
            case STORY -> view.getAsJsonArray("story").forEach(line -> {
                add(rows, Component.literal(line.getAsString()), 0, TEXT, width);
                rows.add(new Row(FormattedCharSequence.EMPTY, 0, TEXT));
            });
            case PEOPLE -> entries(rows, view.getAsJsonArray("people"), false, width);
            case WORDS -> entries(rows, view.getAsJsonArray("words"), true, width);
        }
        if (rows.isEmpty()) {
            add(rows, Component.translatable("wayfarer.journal.empty"), 0, QUIET, width);
        }
        return rows;
    }

    private void entries(List<Row> rows, Iterable<JsonElement> entries, boolean words, int width) {
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            Component title = words ? Component.translatable("wayfarer.word." + entry.get("key").getAsString()) : Component.literal(entry.get("title").getAsString());
            add(rows, title, 0, TITLE, width);
            for (JsonElement line : entry.getAsJsonArray("lines")) {
                add(rows, Component.literal(line.getAsString()), MARGIN, TEXT, width);
            }
            rows.add(new Row(FormattedCharSequence.EMPTY, 0, TEXT));
        }
    }

    private void add(List<Row> rows, Component text, int indent, int color, int width) {
        for (FormattedCharSequence line : font.split(text, width - indent)) {
            rows.add(new Row(line, indent, color));
        }
    }

    private JsonObject view() {
        JsonObject view = JsonParser.parseString(shown).getAsJsonObject();
        for (String key : new String[] {"quests", "rewards", "story", "people", "words", "places"}) {
            if (!view.has(key)) {
                view.add(key, new com.google.gson.JsonArray());
            }
        }
        return view;
    }

    public static JsonObject places() {
        return JsonParser.parseString(latest).getAsJsonObject();
    }
}
