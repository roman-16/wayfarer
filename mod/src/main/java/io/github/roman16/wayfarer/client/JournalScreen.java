package io.github.roman16.wayfarer.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Journal;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

public final class JournalScreen extends Screen {
    private static final String PAGE_SOUND = "journal_page";
    private static final ResourceLocation COVER = Wayfarer.id("journal_cover");
    private static final ResourceLocation SHEET = Wayfarer.id("journal_page");
    private static final ResourceLocation RIBBON = Wayfarer.id("ribbon");
    private static final int MAX_WIDTH = 384;
    private static final int MAX_HEIGHT = 232;
    private static final int OUTSIDE = 12;
    private static final int COVER_EDGE = 8;
    private static final int SPINE = 2;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int RIBBON_HEIGHT = 12;
    private static final int RIBBON_GAP = 3;
    private static final int RIBBON_PULL = 6;
    private static final int RIBBON_TEXT = 4;
    private static final int RIBBON_TUCK = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 72;
    private static final int SLOT = 18;
    private static final int DOT = 3;
    private static final int INK = 0xFF3F2A14;
    private static final int TITLE_INK = 0xFF8A3A0A;
    private static final int QUIET_INK = 0xFF8A7A62;
    private static final int GOLD = 0xFFC8901E;
    private static final int CHOSEN = 0x40B78A4E;
    private static final int POINTED = 0x20B78A4E;
    private static final int RIBBON_INK = 0xFFFFFFFF;
    private static final int RIBBON_POINTED_INK = 0xFFFFFFA0;
    private static final int SPINE_COLOR = 0xFF3A1408;
    private static final int SLOT_FACE = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final String TRACKED_MARK = "✦ ";
    private static final String FOLDED_MARK = "▸ ";
    private static final String UNFOLDED_MARK = "▾ ";
    private static String latest = "{}";
    private static Tab open = Tab.QUESTS;
    private static final Map<Tab, String> CHOSEN_ENTRY = new EnumMap<>(Tab.class);
    private final Set<String> seen = new HashSet<>();
    private boolean doneUnfolded;
    private JsonObject view = new JsonObject();
    private String shown = "";
    private List<Entry> entries = List.of();
    private List<Tab> ribbons = List.of();
    private List<Line> page = List.of();
    private List<ItemStack> slots = List.of();
    private List<Row> rows = List.of();
    private boolean dirty;
    private boolean acting;
    private int left;
    private int top;
    private int bookWidth;
    private int bookHeight;
    private int listScroll;
    private int pageScroll;

    private JournalScreen() {
        super(Component.translatable("wayfarer.journal"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new JournalScreen());
        PacketDistributor.sendToServer(new Tasks.Did("journal"));
        turn();
    }

    public static void show(String view) {
        latest = view;
    }

    public static void forget() {
        latest = "{}";
        open = Tab.QUESTS;
        CHOSEN_ENTRY.clear();
    }

    public static JsonArray places() {
        return view().getAsJsonArray("places");
    }

    private static void turn() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Wayfarer.id(PAGE_SOUND)), 1, 1));
    }

    private enum Tab {
        QUESTS("wayfarer.journal.quests"),
        STORY("wayfarer.journal.story"),
        PEOPLE("wayfarer.journal.people"),
        WORDS("wayfarer.journal.words"),
        STONES("wayfarer.journal.stones"),
        MAP("wayfarer.journal.map");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private enum Kind {
        CARAVAN,
        DONE,
        FOLD,
        LINES,
        QUEST,
        REWARD,
        STONE
    }

    private record Entry(Kind kind, String key, Component label, int color, JsonObject data) {}

    private record Line(FormattedCharSequence text, int indent, int color) {}

    @Override
    protected void init() {
        shown = latest;
        view = view();
        view.getAsJsonArray("seen").forEach(entry -> seen.add(entry.getAsString()));
        ribbons = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            if (tab == Tab.QUESTS || tab == Tab.MAP && ModList.get().isLoaded("journeymap") || !entries(tab).isEmpty()) {
                ribbons.add(tab);
            }
        }
        int room = ribbons.stream().mapToInt(tab -> font.width(Component.translatable(tab.key))).max().orElse(0) + RIBBON_TEXT * 2 + RIBBON_PULL;
        bookWidth = Math.min(width - OUTSIDE * 2 - room, MAX_WIDTH);
        bookHeight = Math.min(height - OUTSIDE * 2, MAX_HEIGHT);
        left = (width - bookWidth - room) / 2;
        top = (height - bookHeight) / 2;
        if (!ribbons.contains(open)) {
            open = Tab.QUESTS;
        }
        entries = entries(open);
        Entry chosen = chosen();
        if (chosen != null) {
            see(chosen);
        }
        page = page(chosen);
        slots = chosen != null && chosen.kind() == Kind.REWARD ? stacks(chosen.data()) : List.of();
        int ribbonY = top + COVER_EDGE + RIBBON_GAP;
        for (Tab tab : ribbons) {
            addRenderableWidget(new Ribbon(tab, left + bookWidth, ribbonY));
            ribbonY += RIBBON_HEIGHT + RIBBON_GAP;
        }
        rows = new ArrayList<>();
        for (Entry entry : entries) {
            rows.add(addWidget(new Row(entry, entry == chosen)));
        }
        place();
        acting = false;
        if (chosen != null && chosen.kind() == Kind.REWARD && !chosen.data().get("claimed").getAsBoolean()) {
            String reward = chosen.data().get("key").getAsString();
            action("wayfarer.journal.claim", () -> PacketDistributor.sendToServer(new Journal.Claim(reward)));
        }
        if (chosen != null && chosen.kind() == Kind.QUEST && !chosen.data().get("id").getAsString().equals(tracked())) {
            String quest = chosen.data().get("id").getAsString();
            action("wayfarer.journal.track", () -> PacketDistributor.sendToServer(new Journal.Track(quest)));
        }
        if (chosen != null && chosen.kind() == Kind.CARAVAN) {
            action("wayfarer.journal.head_in", () -> {
                PacketDistributor.sendToServer(new Caravan.HeadIn());
                onClose();
            });
        }
    }

    private void action(String label, Runnable run) {
        acting = true;
        addRenderableWidget(Button.builder(Component.translatable(label), pressed -> run.run())
                .bounds(left + bookWidth - COVER_EDGE - PAD - BUTTON_WIDTH, top + bookHeight - COVER_EDGE - PAD - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Nullable
    private Entry chosen() {
        String key = CHOSEN_ENTRY.get(open);
        return entries.stream().filter(entry -> entry.key().equals(key) && entry.kind() != Kind.FOLD).findFirst()
                .or(() -> entries.stream().filter(entry -> entry.kind() != Kind.FOLD).findFirst()).orElse(null);
    }

    private void see(Entry entry) {
        CHOSEN_ENTRY.put(open, entry.key());
        if (seen.add(entry.key())) {
            PacketDistributor.sendToServer(new Journal.Seen(entry.key()));
        }
    }

    private String tracked() {
        return view.has("tracked") ? view.get("tracked").getAsString() : "";
    }

    private List<Entry> entries(Tab tab) {
        List<Entry> found = new ArrayList<>();
        switch (tab) {
            case QUESTS -> quests(found);
            case STORY -> lines(found, view.getAsJsonArray("story"), "chapter", false);
            case PEOPLE -> lines(found, view.getAsJsonArray("people"), "person", false);
            case WORDS -> lines(found, view.getAsJsonArray("words"), "word", true);
            case STONES -> view.getAsJsonArray("stones").forEach(element -> {
                JsonObject stone = element.getAsJsonObject();
                String key = "stone:" + stone.get("x").getAsInt() + "," + stone.get("z").getAsInt();
                found.add(new Entry(Kind.STONE, key, Component.translatable("wayfarer.journal.stone", stone.get("things").getAsInt()), INK, stone));
            });
            case MAP -> {}
        }
        return found;
    }

    private void quests(List<Entry> found) {
        for (JsonElement element : view.getAsJsonArray("rewards")) {
            JsonObject reward = element.getAsJsonObject();
            if (!reward.get("claimed").getAsBoolean()) {
                found.add(new Entry(Kind.REWARD, "reward:" + reward.get("key").getAsString(), Component.literal(reward.get("title").getAsString()), GOLD, reward));
            }
        }
        List<JsonObject> quests = new ArrayList<>();
        view.getAsJsonArray("quests").forEach(element -> quests.add(element.getAsJsonObject()));
        for (String kind : new String[] {"main", "side"}) {
            for (JsonObject quest : quests) {
                if (!quest.get("done").getAsBoolean() && quest.get("kind").getAsString().equals(kind)) {
                    boolean tracked = quest.get("id").getAsString().equals(tracked());
                    String key = "quest:" + quest.get("id").getAsString() + ":" + Integer.toHexString(quest.get("objective").getAsString().hashCode());
                    found.add(new Entry(Kind.QUEST, key, Component.literal(quest.get("title").getAsString()), tracked ? TITLE_INK : INK, quest));
                }
            }
        }
        if (view.get("headIn").getAsBoolean()) {
            found.add(new Entry(Kind.CARAVAN, "caravan", Component.translatable("wayfarer.caravan.title"), INK, new JsonObject()));
        }
        List<Entry> done = new ArrayList<>();
        for (JsonObject quest : quests) {
            if (quest.get("done").getAsBoolean()) {
                done.add(new Entry(Kind.DONE, "done:" + quest.get("id").getAsString(), Component.literal(quest.get("title").getAsString()), QUIET_INK, quest));
            }
        }
        for (JsonElement element : view.getAsJsonArray("rewards")) {
            JsonObject reward = element.getAsJsonObject();
            if (reward.get("claimed").getAsBoolean()) {
                done.add(new Entry(Kind.REWARD, "taken:" + reward.get("key").getAsString(), Component.literal(reward.get("title").getAsString()), QUIET_INK, reward));
            }
        }
        if (!done.isEmpty()) {
            found.add(new Entry(Kind.FOLD, "fold", Component.translatable("wayfarer.journal.done", done.size()), QUIET_INK, new JsonObject()));
            if (doneUnfolded) {
                found.addAll(done);
            }
        }
    }

    private void lines(List<Entry> found, JsonArray list, String prefix, boolean words) {
        for (JsonElement element : list) {
            JsonObject entry = element.getAsJsonObject();
            Component label = words ? Component.translatable("wayfarer.word." + entry.get("key").getAsString()) : Component.literal(entry.get("title").getAsString());
            String key = prefix + ":" + entry.get("key").getAsString() + ":" + entry.getAsJsonArray("lines").size();
            found.add(new Entry(Kind.LINES, key, label, INK, entry));
        }
    }

    private List<Line> page(@Nullable Entry entry) {
        List<Line> lines = new ArrayList<>();
        int width = pageWidth() - PAD * 2;
        if (entry == null) {
            add(lines, Component.translatable("wayfarer.journal.empty"), 0, QUIET_INK, width);
            return lines;
        }
        JsonObject data = entry.data();
        add(lines, Component.literal(mark(entry)).append(entry.label()), 0, entry.kind() == Kind.REWARD && entry.color() == GOLD ? GOLD : TITLE_INK, width);
        lines.add(new Line(FormattedCharSequence.EMPTY, 0, INK));
        switch (entry.kind()) {
            case REWARD -> {
                add(lines, Component.literal(data.get("text").getAsString()), 0, INK, width);
                if (data.get("claimed").getAsBoolean()) {
                    lines.add(new Line(FormattedCharSequence.EMPTY, 0, INK));
                    add(lines, Component.translatable("wayfarer.journal.claimed"), 0, QUIET_INK, width);
                }
            }
            case QUEST, DONE -> {
                for (JsonElement told : data.getAsJsonArray("story")) {
                    add(lines, Component.literal(told.getAsString()), 0, INK, width);
                    lines.add(new Line(FormattedCharSequence.EMPTY, 0, INK));
                }
                if (entry.kind() == Kind.QUEST) {
                    add(lines, Component.literal("> " + data.get("objective").getAsString()), 0, INK, width);
                    if (data.get("id").getAsString().equals(tracked())) {
                        HudState.panel.open().forEach(task -> add(lines, Component.literal("☐ ").append(task), PAD, INK, width));
                        HudState.panel.done().forEach(task -> add(lines, Component.literal("☑ ").append(task), PAD, QUIET_INK, width));
                    }
                    JsonArray clues = data.getAsJsonArray("found");
                    if (!clues.isEmpty()) {
                        lines.add(new Line(FormattedCharSequence.EMPTY, 0, INK));
                        add(lines, Component.translatable("wayfarer.journal.found"), 0, TITLE_INK, width);
                        clues.forEach(clue -> add(lines, Component.literal("- " + clue.getAsString()), PAD, INK, width));
                    }
                }
            }
            case CARAVAN -> add(lines, Component.translatable("wayfarer.caravan.objective"), 0, INK, width);
            case LINES -> data.getAsJsonArray("lines").forEach(line -> {
                add(lines, Component.literal(line.getAsString()), 0, INK, width);
                lines.add(new Line(FormattedCharSequence.EMPTY, 0, INK));
            });
            case STONE -> add(lines, Component.translatable("wayfarer.journal.stone.at", data.get("x").getAsInt(), data.get("z").getAsInt()), 0, INK, width);
            case FOLD -> {}
        }
        return lines;
    }

    private List<ItemStack> stacks(JsonObject reward) {
        List<ItemStack> stacks = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return stacks;
        }
        for (JsonElement item : reward.getAsJsonArray("items")) {
            ItemStack.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), item).result().ifPresent(stacks::add);
        }
        return stacks;
    }

    private void add(List<Line> lines, Component text, int indent, int color, int width) {
        font.split(text, width - indent).forEach(line -> lines.add(new Line(line, indent, color)));
    }

    private int pageWidth() {
        return bookWidth / 2 - COVER_EDGE;
    }

    private int listLeft() {
        return left + COVER_EDGE;
    }

    private int pageLeft() {
        return left + bookWidth / 2 + SPINE;
    }

    private int pagesTop() {
        return top + COVER_EDGE;
    }

    private int pagesBottom() {
        return top + bookHeight - COVER_EDGE;
    }

    @Override
    public void tick() {
        if (!latest.equals(shown)) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (GuiEventListener child : children()) {
            if (child instanceof Ribbon ribbon) {
                graphics.blitSprite(RIBBON, ribbon.getX() - RIBBON_TUCK, ribbon.getY(), ribbon.getWidth() + RIBBON_TUCK + RIBBON_TEXT, RIBBON_HEIGHT);
            }
        }
        graphics.blitSprite(COVER, left, top, bookWidth, bookHeight);
        graphics.blitSprite(SHEET, listLeft(), pagesTop(), pageWidth(), pagesBottom() - pagesTop());
        graphics.blitSprite(SHEET, pageLeft() - SPINE, pagesTop(), pageWidth(), pagesBottom() - pagesTop());
        graphics.fill(left + bookWidth / 2 - SPINE / 2, pagesTop(), left + bookWidth / 2 + SPINE / 2, pagesBottom(), SPINE_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, Component.translatable(open.key), listLeft() + PAD, pagesTop() + PAD, TITLE_INK, false);
        graphics.enableScissor(listLeft(), listTop(), listLeft() + pageWidth(), listBottom());
        rows.forEach(row -> row.render(graphics, mouseX, mouseY, partialTick));
        graphics.disableScissor();
        entry(graphics, mouseX, mouseY);
    }

    private int listTop() {
        return pagesTop() + PAD + LINE + LINE / 2;
    }

    private int listBottom() {
        return pagesBottom() - PAD;
    }

    private void place() {
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() * LINE - (listBottom() - listTop())));
        int y = listTop() - listScroll;
        for (Row row : rows) {
            row.setY(y);
            row.visible = y + LINE > listTop() && y < listBottom();
            y += LINE;
        }
    }

    private void entry(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = pageLeft() + PAD;
        int pageTop = pagesTop() + PAD;
        int bottom = pagesBottom() - PAD - (acting ? BUTTON_HEIGHT + PAD : 0) - (slots.isEmpty() ? 0 : SLOT + PAD);
        pageScroll = Mth.clamp(pageScroll, 0, Math.max(0, page.size() * LINE - (bottom - pageTop)));
        graphics.enableScissor(pageLeft(), pageTop, pageLeft() + pageWidth(), bottom);
        int y = pageTop - pageScroll;
        for (Line line : page) {
            graphics.drawString(font, line.text(), x + line.indent(), y, line.color(), false);
            y += LINE;
        }
        graphics.disableScissor();
        ItemStack hovered = ItemStack.EMPTY;
        int slotX = x;
        for (ItemStack stack : slots) {
            graphics.fill(slotX, bottom + PAD, slotX + SLOT, bottom + PAD + SLOT, SLOT_SHADE);
            graphics.fill(slotX + 1, bottom + PAD + 1, slotX + SLOT, bottom + PAD + SLOT, SLOT_LIGHT);
            graphics.fill(slotX + 1, bottom + PAD + 1, slotX + SLOT - 1, bottom + PAD + SLOT - 1, SLOT_FACE);
            graphics.renderItem(stack, slotX + 1, bottom + PAD + 1);
            graphics.renderItemDecorations(font, stack, slotX + 1, bottom + PAD + 1);
            if (mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= bottom + PAD && mouseY < bottom + PAD + SLOT) {
                hovered = stack;
            }
            slotX += SLOT + 2;
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private static void dot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + DOT, y + DOT, GOLD);
    }

    private String mark(Entry entry) {
        return switch (entry.kind()) {
            case QUEST -> entry.data().get("id").getAsString().equals(tracked()) ? TRACKED_MARK : "";
            case FOLD -> doneUnfolded ? UNFOLDED_MARK : FOLDED_MARK;
            default -> "";
        };
    }

    private boolean unseen(Tab tab) {
        return entries(tab).stream().anyMatch(entry -> entry.kind() != Kind.FOLD && !seen.contains(entry.key()));
    }

    private void choose(Tab tab) {
        if (tab == Tab.MAP) {
            MapLink.open();
            return;
        }
        if (tab != open) {
            open = tab;
            listScroll = 0;
            pageScroll = 0;
            turn();
            dirty = true;
        }
    }

    private void choose(Entry entry) {
        if (entry.kind() == Kind.FOLD) {
            doneUnfolded = !doneUnfolded;
        } else {
            CHOSEN_ENTRY.put(open, entry.key());
            pageScroll = 0;
        }
        dirty = true;
    }

    private void refresh() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String focused = getFocused() instanceof AbstractWidget widget ? widget.getMessage().getString() : null;
        rebuildWidgets();
        if (focused != null && minecraft.getLastInputType().isKeyboard()) {
            children().stream().filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().equals(focused))
                    .findFirst().ifPresent(this::setInitialFocus);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        refresh();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < left + bookWidth / 2) {
            listScroll -= (int) (scrollY * LINE * 3);
            place();
        } else {
            pageScroll -= (int) (scrollY * LINE * 3);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (WayfarerClient.JOURNAL.matches(key, scanCode)) {
            onClose();
            return true;
        }
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        refresh();
        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Ribbon extends AbstractButton {
        private final Tab tab;

        private Ribbon(Tab tab, int x, int y) {
            super(x, y, Minecraft.getInstance().font.width(Component.translatable(tab.key)) + RIBBON_TEXT * 2 + (tab == open ? RIBBON_PULL : 0),
                    RIBBON_HEIGHT, Component.translatable(tab.key));
            this.tab = tab;
        }

        @Override
        public void onPress() {
            choose(tab);
        }

        @Override
        public void playDownSound(SoundManager sounds) {}

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX() + (tab == open ? RIBBON_PULL : 0);
            graphics.drawString(font, getMessage(), x + RIBBON_TEXT, getY() + (RIBBON_HEIGHT - font.lineHeight) / 2 + 1,
                    isHoveredOrFocused() ? RIBBON_POINTED_INK : RIBBON_INK);
            if (unseen(tab)) {
                dot(graphics, x, getY() + RIBBON_HEIGHT / 2 - 1);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class Row extends AbstractButton {
        private final Entry entry;
        private final boolean chosen;

        private Row(Entry entry, boolean chosen) {
            super(listLeft() + PAD - 2, 0, pageWidth() - PAD * 2 + 4, LINE, entry.label());
            this.entry = entry;
            this.chosen = chosen;
        }

        @Override
        public void onPress() {
            choose(entry);
        }

        @Override
        public void playDownSound(SoundManager sounds) {}

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return super.clicked(mouseX, mouseY) && mouseY >= listTop() && mouseY < listBottom();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (chosen || isHoveredOrFocused()) {
                graphics.fill(getX(), getY() - 1, getRight(), getBottom() - 1, chosen ? CHOSEN : POINTED);
            }
            if (entry.kind() != Kind.FOLD && !seen.contains(entry.key())) {
                dot(graphics, getX() - DOT, getY() + 3);
            }
            String mark = mark(entry);
            int x = getX() + 2;
            graphics.drawString(font, mark, x, getY(), entry.color(), false);
            int room = getWidth() - 4 - font.width(mark);
            graphics.drawString(font, font.substrByWidth(getMessage(), room).getString(), x + font.width(mark), getY(), entry.color(), false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static JsonObject view() {
        JsonObject view = JsonParser.parseString(latest).getAsJsonObject();
        for (String key : new String[] {"quests", "rewards", "story", "people", "words", "places", "stones", "seen"}) {
            if (!view.has(key)) {
                view.add(key, new JsonArray());
            }
        }
        if (!view.has("headIn")) {
            view.addProperty("headIn", false);
        }
        return view;
    }
}
