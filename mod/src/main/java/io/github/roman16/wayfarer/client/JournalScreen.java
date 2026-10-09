package io.github.roman16.wayfarer.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Inline;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Gear;
import io.github.roman16.wayfarer.story.Journal;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

public final class JournalScreen extends Screen {
    private static final String PAGE_SOUND = "journal_page";
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 240;
    private static final int OUTSIDE = 12;
    static final int COVER_EDGE = 12;
    static final int SPINE = 10;
    static final int PAD = 8;
    static final int LINE = 10;
    private static final int TASK_LINE = 12;
    private static final int RIBBON_HEIGHT = 13;
    private static final int RIBBON_GAP = 2;
    private static final int RIBBON_PULL = 6;
    private static final int RIBBON_NOTCH = 5;
    private static final int RIBBON_TUCK = 6;
    private static final int RIBBON_WIDTH = Stamp.ICON_SIZE + 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_LEAST = 48;
    private static final int SLOT = 18;
    private static final int DOT = 3;
    private static final int ICON_INDENT = Stamp.MARK_WIDTH + 3;
    static final int TITLE_GAP = 4;
    private static final int INK = Stamp.INK;
    private static final int TITLE_INK = Stamp.INK_RED;
    private static final int QUIET_INK = Stamp.INK_QUIET;
    private static final int GOLD = Stamp.ramp("ember", 1);
    private static final int CHOSEN = Stamp.withAlpha(Stamp.ramp("paper", 1), Stamp.FAINT);
    private static final int POINTED = Stamp.withAlpha(Stamp.ramp("paper", 1), Stamp.FAINT / 2);
    private static final String FOLDED_MARK = "+ ";
    private static final String UNFOLDED_MARK = "- ";
    private static final String MORE_MARK = "...";
    private static String latest = "{}";
    private static Tab open = Tab.QUESTS;
    private static final Map<Tab, String> CHOSEN_ENTRY = new EnumMap<>(Tab.class);
    private static final Map<String, String> CHOSEN_THING = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private boolean doneUnfolded;
    private JsonObject view = new JsonObject();
    private String shown = "";
    private List<Entry> entries = List.of();
    private List<Tab> ribbons = List.of();
    private List<Line> page = List.of();
    private List<ItemStack> slots = List.of();
    private List<Row> rows = List.of();
    private List<ThingButton> things = List.of();
    private boolean dirty;
    private int acting;
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
        Recap.remember(view);
    }

    public static void forget() {
        latest = "{}";
        firstPage();
    }

    public static void firstPage() {
        open = Tab.QUESTS;
        CHOSEN_ENTRY.clear();
        CHOSEN_THING.clear();
    }

    public static String latest() {
        return latest;
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
        PAGES("wayfarer.journal.pages"),
        FIRE("wayfarer.journal.fire"),
        STONES("wayfarer.journal.stones"),
        MAP("wayfarer.journal.map"),
        GEAR("wayfarer.journal.gear");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private enum Kind {
        BOOK,
        CARAVAN,
        DONE,
        FOLD,
        GEAR,
        LINES,
        QUEST,
        REWARD,
        STONE,
        TALK
    }

    private record Entry(Kind kind, String key, Component label, int color, JsonObject data) {}

    private enum Icon {
        DONE,
        MARK,
        OPEN
    }

    private record Line(List<Inline.Piece> pieces, int indent, int color, @Nullable Icon icon) {
        static final Line BLANK = new Line(List.of(), 0, INK, null);
    }

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
        int room = RIBBON_WIDTH + RIBBON_PULL;
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
        things = new ArrayList<>();
        page = page(chosen);
        slots = chosen != null && chosen.kind() == Kind.REWARD ? stacks(chosen.data()) : List.of();
        int ribbonY = top + COVER_EDGE + RIBBON_GAP * 2;
        for (Tab tab : ribbons) {
            addRenderableWidget(new Ribbon(tab, left + bookWidth, ribbonY));
            ribbonY += RIBBON_HEIGHT + RIBBON_GAP;
        }
        rows = new ArrayList<>();
        for (Entry entry : entries) {
            rows.add(addWidget(new Row(entry, entry == chosen)));
        }
        things.forEach(this::addWidget);
        place();
        acting = 0;
        if (chosen != null && chosen.kind() == Kind.GEAR) {
            gearActions(chosen.data());
        }
        if (chosen != null && chosen.kind() == Kind.REWARD && !chosen.data().get("claimed").getAsBoolean()) {
            String reward = chosen.data().get("key").getAsString();
            action("wayfarer.journal.claim", () -> PacketDistributor.sendToServer(new Journal.Claim(reward)));
        }
        if (chosen != null && chosen.kind() == Kind.QUEST && !chosen.data().get("id").getAsString().equals(tracked())) {
            String quest = chosen.data().get("id").getAsString();
            action("wayfarer.journal.track", () -> PacketDistributor.sendToServer(new Journal.Track(quest)));
        }
        if (chosen != null && chosen.data().has("memory") && (chosen.kind() == Kind.QUEST || chosen.kind() == Kind.DONE)) {
            String memory = chosen.data().get("memory").getAsString();
            action("wayfarer.journal.dream_again", byFire(), () -> {
                PacketDistributor.sendToServer(new Journal.Dream(memory));
                onClose();
            });
        }
        if (chosen != null && chosen.kind() == Kind.CARAVAN) {
            action("wayfarer.journal.head_in", () -> {
                PacketDistributor.sendToServer(new Caravan.HeadIn());
                onClose();
            });
        }
    }

    private boolean byFire() {
        return minecraft != null && minecraft.player != null && Memories.fire(minecraft.player.level(), minecraft.player.blockPosition()).isPresent();
    }

    private void action(String label, Runnable run) {
        action(label, true, run);
    }

    private void action(String label, boolean active, Runnable run) {
        Component text = Component.translatable(label);
        int buttonWidth = Math.max(BUTTON_LEAST, font.width(text) + PAD + PAD / 2);
        int right = left + bookWidth - COVER_EDGE - PAD - acting;
        Button button = Button.builder(text, pressed -> run.run())
                .bounds(right - buttonWidth, top + bookHeight - COVER_EDGE - PAD - BUTTON_HEIGHT, buttonWidth, BUTTON_HEIGHT).build();
        button.active = active;
        addRenderableWidget(button);
        acting += buttonWidth + RIBBON_GAP;
    }

    private void gearActions(JsonObject row) {
        JsonObject thing = chosenThing(row);
        if (thing == null) {
            return;
        }
        String key = thing.get("key").getAsString();
        if (!thing.get("known").getAsBoolean()) {
            action("wayfarer.journal.unknown", false, () -> {});
            return;
        }
        if (thing.get("tracked").getAsBoolean()) {
            action("wayfarer.journal.untrack", () -> PacketDistributor.sendToServer(new Gear.Track("")));
        } else if (thing.get("trackable").getAsBoolean() && !thing.get("had").getAsBoolean()) {
            action("wayfarer.journal.track", () -> PacketDistributor.sendToServer(new Gear.Track(key)));
        }
        if (thing.has("jei") && ModList.get().isLoaded("jei") && JeiLink.ready()) {
            ResourceLocation item = ResourceLocation.parse(thing.get("jei").getAsString());
            action("wayfarer.journal.how", () -> JeiLink.show(item));
        }
    }

    @Nullable
    private static JsonObject chosenThing(JsonObject row) {
        List<JsonObject> all = row.getAsJsonArray("things").asList().stream().map(JsonElement::getAsJsonObject).toList();
        String wanted = CHOSEN_THING.get(row.get("id").getAsString());
        return all.stream().filter(thing -> thing.get("key").getAsString().equals(wanted)).findFirst()
                .or(() -> all.stream().filter(thing -> thing.get("known").getAsBoolean() && !thing.get("had").getAsBoolean()).findFirst())
                .or(() -> all.stream().filter(thing -> !thing.get("known").getAsBoolean()).findFirst())
                .orElse(all.isEmpty() ? null : all.getLast());
    }

    private void gear(List<Line> lines, JsonObject row, int width) {
        String who = row.get("who").getAsString();
        JsonObject chosen = chosenThing(row);
        for (JsonElement element : row.getAsJsonArray("things")) {
            JsonObject thing = element.getAsJsonObject();
            boolean known = thing.get("known").getAsBoolean();
            Component label = known ? Component.literal(thing.get("name").getAsString()) : Component.translatable("wayfarer.gear.unknown", who);
            int color = thing == chosen ? GOLD : known && thing.get("had").getAsBoolean() ? QUIET_INK : INK;
            things.add(new ThingButton(row.get("id").getAsString(), thing.get("key").getAsString(), label, lines.size()));
            add(lines, label, 0, color, width, known ? thing.get("had").getAsBoolean() ? Icon.DONE : Icon.OPEN : null);
        }
        lines.add(Line.BLANK);
        if (chosen != null && chosen.get("known").getAsBoolean()) {
            if (chosen.get("mystery").getAsBoolean()) {
                add(lines, Component.translatable("wayfarer.gear.mystery"), 0, INK, width);
            } else {
                add(lines, Component.literal(chosen.get("good").getAsString()), 0, INK, width);
            }
            chosen.getAsJsonArray("needs").forEach(element -> {
                JsonObject need = element.getAsJsonObject();
                add(lines, Component.translatable("wayfarer.gear.need", need.get("count").getAsInt(), need.get("name").getAsString(),
                        need.get("where").getAsString()), 0, INK, width);
            });
            if (!chosen.get("mystery").getAsBoolean()) {
                add(lines, Component.literal(chosen.get("made").getAsString()), 0, QUIET_INK, width);
            }
            if (chosen.get("tracked").getAsBoolean()) {
                add(lines, Component.translatable("wayfarer.gear.tracked"), 0, QUIET_INK, width);
            }
        }
        add(lines, Component.translatable("wayfarer.gear.who", who), 0, QUIET_INK, width);
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
            case PAGES -> view.getAsJsonArray("pages").forEach(element -> {
                JsonObject book = element.getAsJsonObject();
                found.add(new Entry(Kind.BOOK, "page:" + book.get("key").getAsString(), Component.literal(book.get("title").getAsString()), INK, book));
            });
            case FIRE -> view.getAsJsonArray("fire").forEach(element -> {
                JsonObject talk = element.getAsJsonObject();
                String title = talk.get("title").getAsString();
                Component label = title.isEmpty() ? Component.translatable("wayfarer.journal.fire.road") : Component.literal(title);
                String key = "fire:" + talk.get("key").getAsString() + ":" + talk.getAsJsonArray("lines").size();
                found.add(new Entry(Kind.TALK, key, label, INK, talk));
            });
            case STONES -> view.getAsJsonArray("stones").forEach(element -> {
                JsonObject stone = element.getAsJsonObject();
                String key = "stone:" + stone.get("x").getAsInt() + "," + stone.get("z").getAsInt();
                found.add(new Entry(Kind.STONE, key, Component.translatable("wayfarer.journal.stone", stone.get("things").getAsInt()), INK, stone));
            });
            case MAP -> {}
            case GEAR -> view.getAsJsonArray("gear").forEach(element -> {
                JsonObject row = element.getAsJsonObject();
                long known = row.getAsJsonArray("things").asList().stream().filter(thing -> thing.getAsJsonObject().get("known").getAsBoolean()).count();
                found.add(new Entry(Kind.GEAR, "gear:" + row.get("id").getAsString() + ":" + known, Component.literal(row.get("title").getAsString()), INK, row));
            });
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
        switch (entry.kind()) {
            case REWARD -> {
                add(lines, Component.literal(data.get("text").getAsString()), 0, INK, width);
                if (data.get("claimed").getAsBoolean()) {
                    lines.add(Line.BLANK);
                    add(lines, Component.translatable("wayfarer.journal.claimed"), 0, QUIET_INK, width);
                }
            }
            case QUEST, DONE -> {
                for (JsonElement told : data.getAsJsonArray("story")) {
                    add(lines, Component.literal(told.getAsString()), 0, INK, width);
                    lines.add(Line.BLANK);
                }
                if (entry.kind() == Kind.QUEST) {
                    add(lines, Component.literal(data.get("objective").getAsString()), 0, INK, width, Icon.MARK);
                    if (data.get("id").getAsString().equals(tracked())) {
                        HudState.panel.open().forEach(task -> add(lines, task, ICON_INDENT, INK, width, Icon.OPEN));
                        HudState.panel.done().forEach(task -> add(lines, task, ICON_INDENT, QUIET_INK, width, Icon.DONE));
                    }
                    JsonArray clues = data.getAsJsonArray("found");
                    if (!clues.isEmpty()) {
                        lines.add(Line.BLANK);
                        add(lines, Component.translatable("wayfarer.journal.found"), 0, TITLE_INK, width);
                        clues.forEach(clue -> add(lines, Component.literal("- " + clue.getAsString()), PAD, INK, width));
                    }
                }
                if (data.has("memory") && !byFire()) {
                    lines.add(Line.BLANK);
                    add(lines, Component.translatable("wayfarer.journal.dream_again.fire"), 0, QUIET_INK, width);
                }
            }
            case CARAVAN -> add(lines, Component.translatable("wayfarer.caravan.objective"), 0, INK, width);
            case LINES -> data.getAsJsonArray("lines").forEach(line -> {
                add(lines, Component.literal(line.getAsString()), 0, INK, width);
                lines.add(Line.BLANK);
            });
            case STONE -> add(lines, Component.translatable("wayfarer.journal.stone.at", data.get("x").getAsInt(), data.get("z").getAsInt()), 0, INK, width);
            case BOOK -> {
                add(lines, Component.translatable("wayfarer.journal.book.by", data.get("author").getAsString()), 0, QUIET_INK, width);
                for (JsonElement text : data.getAsJsonArray("pages")) {
                    lines.add(Line.BLANK);
                    add(lines, Component.literal(text.getAsString()), 0, INK, width);
                }
            }
            case GEAR -> gear(lines, data, width);
            case TALK -> data.getAsJsonArray("lines").forEach(element -> {
                JsonObject said = element.getAsJsonObject();
                String by = said.get("by").getAsString();
                if (by.isEmpty()) {
                    add(lines, Component.literal(said.get("text").getAsString()).withStyle(ChatFormatting.ITALIC), 0, QUIET_INK, width);
                } else {
                    add(lines, Component.literal(by + ": ").withColor(TITLE_INK).append(Component.literal(said.get("text").getAsString()).withColor(INK)), 0, INK, width);
                }
            });
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
        add(lines, text, indent, color, width, null);
    }

    private void add(List<Line> lines, Component text, int indent, int color, int width, @Nullable Icon icon) {
        int shift = icon == null ? 0 : ICON_INDENT;
        List<List<Inline.Piece>> split = Inline.lines(font, text, width - indent - shift);
        for (int index = 0; index < split.size(); index++) {
            lines.add(new Line(split.get(index), indent + shift, color, index == 0 ? icon : null));
        }
    }

    private int pageWidth() {
        return (bookWidth - COVER_EDGE * 2 - SPINE) / 2;
    }

    private int listLeft() {
        return left + COVER_EDGE;
    }

    private int pageLeft() {
        return listLeft() + pageWidth() + SPINE;
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
                graphics.blitSprite(ribbon.tab == open ? Stamp.RIBBON_ACTIVE : Stamp.RIBBON, ribbon.getX() - RIBBON_TUCK, ribbon.getY(),
                        ribbon.getWidth() + RIBBON_TUCK + RIBBON_NOTCH, RIBBON_HEIGHT);
            }
        }
        graphics.blitSprite(Stamp.BOOK, left, top, bookWidth, bookHeight);
        graphics.blitSprite(Stamp.PAGE_LEFT, listLeft(), pagesTop(), pageWidth(), pagesBottom() - pagesTop());
        graphics.blitSprite(Stamp.PAGE_RIGHT, pageLeft(), pagesTop(), pageWidth(), pagesBottom() - pagesTop());
        graphics.blitSprite(Stamp.SPINE, listLeft() + pageWidth(), pagesTop(), SPINE, pagesBottom() - pagesTop());
        Stamp.mark(graphics, listLeft() + pageWidth() + (SPINE - Stamp.MARK_WIDTH) / 2, top + (COVER_EDGE - Stamp.MARK_HEIGHT) / 2, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Stamp.title(graphics, font, Component.translatable(open.key).getString(), listLeft() + PAD, pagesTop() + PAD, TITLE_INK, false);
        Stamp.divider(graphics, listLeft() + PAD, listTop() - Stamp.DIVIDER_HEIGHT - 2, pageWidth() - PAD * 2, true);
        graphics.enableScissor(listLeft(), listTop(), listLeft() + pageWidth(), listBottom());
        rows.forEach(row -> row.render(graphics, mouseX, mouseY, partialTick));
        graphics.disableScissor();
        things.forEach(thing -> thing.render(graphics, mouseX, mouseY, partialTick));
        entry(graphics, mouseX, mouseY);
    }

    private int listTop() {
        return pagesTop() + PAD + Stamp.DISPLAY_HEIGHT + TITLE_GAP + Stamp.DIVIDER_HEIGHT + 2;
    }

    private int entryTop(@Nullable Entry entry) {
        return pagesTop() + PAD + (entry == null ? 0 : Stamp.DISPLAY_HEIGHT + TITLE_GAP + Stamp.DIVIDER_HEIGHT + 2);
    }

    private int listBottom() {
        return pagesBottom() - PAD;
    }

    private void placeThings() {
        int pageTop = entryTop(chosen());
        for (ThingButton thing : things) {
            int y = pageTop + page.subList(0, thing.line).stream().mapToInt(JournalScreen::height).sum() - pageScroll;
            thing.setY(y);
            thing.visible = y >= pageTop && y + LINE <= pagesBottom() - PAD;
        }
    }

    private void place() {
        placeThings();
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
        Entry chosen = chosen();
        if (chosen != null) {
            int color = chosen.kind() == Kind.REWARD && chosen.color() == GOLD ? GOLD : TITLE_INK;
            String title = font.substrByWidth(Stamp.display(chosen.label().getString()), pageWidth() - PAD * 2).getString();
            Stamp.title(graphics, font, title, x, pagesTop() + PAD, color, false);
            Stamp.divider(graphics, x, entryTop(chosen) - Stamp.DIVIDER_HEIGHT - 2, pageWidth() - PAD * 2, true);
        }
        int pageTop = entryTop(chosen);
        int bottom = pagesBottom() - PAD - (acting > 0 ? BUTTON_HEIGHT + PAD : 0) - (slots.isEmpty() ? 0 : SLOT + PAD);
        pageScroll = Mth.clamp(pageScroll, 0, Math.max(0, page.stream().mapToInt(JournalScreen::height).sum() - (bottom - pageTop)));
        graphics.enableScissor(pageLeft(), pageTop, pageLeft() + pageWidth(), bottom);
        int y = pageTop - pageScroll;
        for (Line line : page) {
            if (line.icon() != null) {
                int iconX = x + line.indent() - ICON_INDENT;
                switch (line.icon()) {
                    case DONE -> Stamp.ring(graphics, iconX, y + 1, true, QUIET_INK);
                    case MARK -> Stamp.mark(graphics, iconX, y, true);
                    case OPEN -> Stamp.ring(graphics, iconX, y + 1, false, INK);
                }
            }
            Inline.draw(graphics, font, line.pieces(), x + line.indent(), y, line.color(), false);
            y += height(line);
        }
        graphics.disableScissor();
        if (y > bottom) {
            graphics.drawString(font, MORE_MARK, pageLeft() + pageWidth() - PAD - font.width(MORE_MARK), bottom, QUIET_INK, false);
        }
        ItemStack hovered = ItemStack.EMPTY;
        int slotX = x;
        for (ItemStack stack : slots) {
            graphics.blitSprite(Stamp.SLOT, slotX, bottom + PAD, SLOT, SLOT);
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

    private static int height(Line line) {
        return line.pieces().stream().anyMatch(Inline.Piece::key) ? TASK_LINE : LINE;
    }

    private boolean tracked(Entry entry) {
        return entry.kind() == Kind.QUEST && entry.data().get("id").getAsString().equals(tracked());
    }

    private boolean unseen(Tab tab) {
        return entries(tab).stream().anyMatch(entry -> entry.kind() != Kind.FOLD && !seen.contains(entry.key()));
    }

    private void choose(Tab tab) {
        if (tab == Tab.MAP) {
            MapLink.open();
            return;
        }
        if (tab == Tab.GEAR) {
            PacketDistributor.sendToServer(new Tasks.Did("gear"));
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
            placeThings();
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
            super(x, y, RIBBON_WIDTH + (tab == open ? RIBBON_PULL : 0), RIBBON_HEIGHT, Component.translatable(tab.key));
            this.tab = tab;
            setTooltip(Tooltip.create(Component.translatable(tab.key)));
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
            boolean active = tab == open;
            int color = active ? INK : isHoveredOrFocused() ? Stamp.GOLD : Stamp.BONE;
            Stamp.tinted(graphics, Stamp.icon(tab.name().toLowerCase(Locale.ROOT), false), x + 3, getY() + 2, Stamp.ICON_SIZE, Stamp.ICON_SIZE, color);
            if (unseen(tab)) {
                dot(graphics, x - 1, getY() + RIBBON_HEIGHT / 2 - 1);
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
            int x = getX() + 2;
            int shift = 0;
            if (tracked(entry)) {
                Stamp.mark(graphics, x, getY(), true);
                shift = ICON_INDENT;
            } else if (entry.kind() == Kind.FOLD) {
                String fold = doneUnfolded ? UNFOLDED_MARK : FOLDED_MARK;
                graphics.drawString(font, fold, x, getY(), entry.color(), false);
                shift = font.width(fold);
            }
            int room = getWidth() - 4 - shift;
            graphics.drawString(font, font.substrByWidth(getMessage(), room).getString(), x + shift, getY(), entry.color(), false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class ThingButton extends AbstractButton {
        private final String row;
        private final String key;
        private final int line;

        private ThingButton(String row, String key, Component label, int line) {
            super(pageLeft() + PAD - 2, 0, pageWidth() - PAD * 2 + 4, LINE, label);
            this.row = row;
            this.key = key;
            this.line = line;
        }

        @Override
        public void onPress() {
            CHOSEN_THING.put(row, key);
            dirty = true;
        }

        @Override
        public void playDownSound(SoundManager sounds) {}

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY() - 1, getRight(), getBottom() - 1, POINTED);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static JsonObject view() {
        JsonObject view = JsonParser.parseString(latest).getAsJsonObject();
        for (String key : new String[] {"fire", "gear", "pages", "people", "places", "quests", "rewards", "seen", "stones", "story", "words"}) {
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
