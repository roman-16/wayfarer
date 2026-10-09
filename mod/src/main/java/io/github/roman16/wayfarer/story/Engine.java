package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.OwnStone;
import io.github.roman16.wayfarer.RestEvent;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Engine {
    private static final int CHECK_TICKS = 10;
    private static final String EMBER = "ember";
    private static final String MAIN = "main";
    static final String PROLOGUE = "prologue";
    static final String TRACKED = "tracked";
    private static final String JOURNAL_ENTRY = "journal_entry";
    private static final String QUEST_BEGUN = "quest_begun";
    private static final String QUEST_DONE = "quest_done";
    private static final String REWARD_TAKEN = "reward_taken";
    private static final String STARTED = "started";
    private static final String WHEN = "when";
    private static final String JOINED = "joined";
    private static final String ASKS = "asks";

    private Engine() {}

    public record Active(Story.Quest quest, Story.Step step, CompoundTag scratch) {
        public String key() {
            return quest.id() + "/" + step.id();
        }
    }

    public static boolean running(MinecraftServer server) {
        return server.getGameRules().getBoolean(Wayfarer.STORY);
    }

    public static List<Active> active(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List<Active> active = new ArrayList<>();
        for (Story.Quest quest : Story.get().quests()) {
            if (progress.open(quest.id())) {
                active.add(new Active(quest, quest.steps().get(progress.step(quest.id())), progress.scratch(quest.id())));
            }
        }
        return active;
    }

    public static Optional<Active> main(MinecraftServer server) {
        return active(server).stream().filter(active -> active.quest().kind().equals(MAIN)).findFirst();
    }

    public static Optional<Active> tracked(ServerPlayer player) {
        String chosen = Progress.of(player.server).tracking.get(player.getUUID());
        List<Active> active = active(player.server);
        return active.stream().filter(open -> open.quest().id().equals(chosen)).findFirst()
                .or(() -> active.stream().filter(open -> open.quest().kind().equals(MAIN) && open.quest().act().equals(PROLOGUE)).findFirst());
    }

    static boolean pursued(MinecraftServer server, Story.Quest quest) {
        return players(server).stream().anyMatch(player -> tracked(player).filter(active -> active.quest() == quest).isPresent());
    }

    public static void track(ServerPlayer player, String quest) {
        Progress progress = Progress.of(player.server);
        if (quest.isEmpty()) {
            progress.tracking.remove(player.getUUID());
        } else if (progress.open(quest)) {
            progress.tracking.put(player.getUUID(), quest);
            Firsts.first(player, TRACKED);
        } else {
            return;
        }
        progress.setDirty();
        Journal.sync(player);
    }

    public static void seen(ServerPlayer player, String entry) {
        Progress progress = Progress.of(player.server);
        if (progress.seen.computeIfAbsent(player.getUUID(), key -> new java.util.LinkedHashSet<>()).add(entry)) {
            progress.setDirty();
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Talk.reset();
        if (!running(server)) {
            return;
        }
        for (Active active : active(server)) {
            active.scratch().remove(STARTED);
            if (Steps.entered(active)) {
                Steps.resume(server, active);
            }
        }
        open(server);
        Progress.of(server).setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !running(server)) {
            return;
        }
        for (Active active : active(server)) {
            if (Steps.entered(active)) {
                Steps.tick(server, active);
            } else if (!Talk.busy() && pursued(server, active.quest())) {
                begin(server, active);
            }
        }
    }

    private static void begin(MinecraftServer server, Active active) {
        if (active.quest().steps().indexOf(active.step()) == 0) {
            Banners.show(Component.translatable("wayfarer.banner.begun"), Component.literal(active.quest().title()));
            Sounds.play(server, QUEST_BEGUN);
        }
        Steps.enter(server, active);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.getTags().contains(People.TAG) && target.getTags().stream().noneMatch(tag -> tag.startsWith(Steps.CLUE))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player) || !running(player.server)) {
            return;
        }
        for (Active active : active(player.server)) {
            if (Steps.entered(active) && Steps.interact(player, active, target)) {
                return;
            }
        }
        if (!Scenes.interact(player, target)) {
            Remarks.answer(player, target);
        }
    }

    @SubscribeEvent
    public static void onRest(RestEvent event) {
        if (!running(event.player().server)) {
            return;
        }
        for (Active active : active(event.player().server)) {
            if (Steps.entered(active)) {
                Steps.rested(event.player(), active, event.fire());
            }
        }
    }

    private static Optional<BlockPos> goal(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!running(server)) {
            return Optional.empty();
        }
        Optional<String> goal = Caravan.heading(player) ? Optional.of(Caravan.NAME) : tracked(player).flatMap(Steps::goal);
        return goal.flatMap(name -> Progress.of(server).place(name));
    }

    static Optional<BlockPos> marked(ServerPlayer player) {
        return goal(player).filter(pos -> !felt(player.server, player, pos));
    }

    private static boolean felt(MinecraftServer server, ServerPlayer player, BlockPos goal) {
        int exact = Balance.get().compass().exactBlocks();
        return !Caravan.heading(player) && tracked(player).map(Steps::felt).orElse(false)
                && player.blockPosition().distToCenterSqr(goal.getX(), player.getY(), goal.getZ()) > (double) exact * exact;
    }

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        MinecraftServer server = event.player().server;
        if (!running(server)) {
            return;
        }
        if (!Progress.of(server).named(EMBER)) {
            event.unnamed();
        }
        Optional<Active> tracked = tracked(event.player());
        for (Active side : active(server)) {
            if (tracked.map(active -> active.quest() != side.quest()).orElse(true) && Steps.entered(side) && pursued(server, side.quest())) {
                Steps.goal(side).ifPresent(name -> Progress.of(server).place(name).ifPresent(pos -> event.markers()
                        .add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, "wayfarer.place." + name, pos.getX(), pos.getZ()))));
            }
        }
        Optional<String> goal = Caravan.heading(event.player()) ? Optional.of(Caravan.NAME) : tracked.flatMap(Steps::goal);
        goal.ifPresent(name -> Progress.of(server).place(name).ifPresent(pos -> {
            if (felt(server, event.player(), pos)) {
                event.aim(new Compass.Aim(pos));
            } else {
                event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.GOAL, "wayfarer.place." + name, pos.getX(), pos.getZ()));
            }
        }));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() != null || !running(player.server)) {
            return;
        }
        main(player.server).filter(active -> Steps.entered(active) && active.step().spec().has("awake")).ifPresent(active -> {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.literal(active.step().text("awake")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Journal.sync(player);
        }
    }

    static List<Active> open(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List<Active> opened = new ArrayList<>();
        for (Story.Quest quest : Story.get().quests()) {
            if (!progress.steps.containsKey(quest.id()) && quest.after().stream().allMatch(progress::done) && halls(progress, quest.act()) >= quest.halls()
                    && (quest.found() == null || progress.place(quest.found()).isPresent())) {
                progress.steps.put(quest.id(), 0);
                progress.scratch.remove(quest.id());
                progress.setDirty();
                if (!pursued(server, quest)) {
                    Banners.show(Component.translatable("wayfarer.banner.new", Tasks.key(Tasks.JOURNAL_KEY)), Component.literal(quest.title()));
                    Sounds.play(server, QUEST_BEGUN);
                }
                opened.add(new Active(quest, quest.steps().getFirst(), progress.scratch(quest.id())));
            }
        }
        Journal.syncAll(server);
        return opened;
    }

    static long halls(Progress progress, String act) {
        return Story.get().quests().stream().filter(quest -> quest.act().equals(act) && quest.hall() && progress.done(quest.id())).count();
    }

    static void road(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        Story.Quest hall = active.quest();
        if (!hall.hall()) {
            return;
        }
        long reached = halls(progress, hall.act()) + 1;
        Story.get().quests().stream()
                .filter(quest -> quest.act().equals(hall.act()) && quest.halls() == reached && !progress.steps.containsKey(quest.id())
                        && quest.after().stream().allMatch(progress::done))
                .forEach(quest -> Talk.say(quest.id(), null, quest.road(), fill(progress)));
    }

    public static void complete(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        if (progress.step(active.quest().id()) != active.quest().steps().indexOf(active.step())) {
            return;
        }
        Vec3 anchor = Steps.anchor(server, active.step());
        Talk.say(active.quest().id(), anchor, active.step().done(), fill(progress));
        if (active.step().spec().has("finds")) {
            Talk.then(() -> open(server));
        }
        if (advance(server, active).isEmpty()) {
            Talk.then(() -> open(server));
        }
    }

    static Optional<Active> advance(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        Story.Quest quest = active.quest();
        Story.Step step = active.step();
        Steps.poses(server, step);
        if (step.spec().has(Steps.MUSIC)) {
            Music.stop(server);
        }
        record(server, progress, quest, step, fill(progress));
        People.introduce(server, introduced(step));
        if (step.spec().has("finds")) {
            Vec3 anchor = Steps.anchor(server, step);
            progress.place(step.text("finds"), anchor == null ? overworld(server).getSharedSpawnPos() : BlockPos.containing(anchor));
        }
        if (step.spec().has("joins")) {
            progress.joined.add(step.text("joins"));
        }
        progress.trust(Story.trusts(step.spec()));
        if (step.spec().has("clears")) {
            Visit.clear(server, ResourceLocation.parse(step.text("clears")));
        }
        if (step.spec().has("closes")) {
            step.spec().getAsJsonObject("closes").entrySet().forEach(entry -> close(server, entry.getKey(), entry.getValue().getAsString()));
        }
        if (step.reward() != null) {
            grant(server, step.reward());
        }
        int next = progress.step(quest.id()) + 1;
        while (next < quest.steps().size() && !holds(progress, quest, quest.steps().get(next))) {
            next++;
        }
        progress.steps.put(quest.id(), next);
        progress.scratch.remove(quest.id());
        progress.setDirty();
        Journal.syncAll(server);
        if (next < quest.steps().size()) {
            return Optional.of(new Active(quest, quest.steps().get(next), progress.scratch(quest.id())));
        }
        if (quest.reward() != null) {
            grant(server, quest.reward());
        }
        Banners.show(Component.translatable("wayfarer.banner.done"), Component.literal(quest.title()));
        Sounds.play(server, QUEST_DONE);
        return Optional.empty();
    }

    private static boolean holds(Progress progress, Story.Quest quest, Story.Step step) {
        if (step.spec().has(JOINED) && !progress.joined.contains(step.text(JOINED))) {
            return false;
        }
        if (!step.spec().has(WHEN)) {
            return true;
        }
        String[] when = step.text(WHEN).split("=", -1);
        return progress.choice(quest.id(), when[0]).orElse("").equals(when[1]);
    }

    static void jump(MinecraftServer server, Active active, String step) {
        Progress progress = Progress.of(server);
        List<Story.Step> steps = active.quest().steps();
        int index = steps.stream().map(Story.Step::id).toList().indexOf(step);
        progress.steps.put(active.quest().id(), index);
        progress.scratch.remove(active.quest().id());
        progress.setDirty();
        Journal.syncAll(server);
    }

    public static void close(MinecraftServer server, String quest, String told) {
        Progress progress = Progress.of(server);
        Story.get().quest(quest).filter(found -> progress.open(found.id())).ifPresent(found -> {
            progress.steps.put(found.id(), found.steps().size());
            progress.scratch.remove(found.id());
            progress.told.computeIfAbsent(found.id(), key -> new ArrayList<>()).add(told);
            progress.setDirty();
            Journal.syncAll(server);
        });
    }

    public static boolean asks(ServerPlayer player, BlockPos pedestal, ResourceLocation shrine) {
        MinecraftServer server = player.server;
        if (!running(server)) {
            return false;
        }
        Progress progress = Progress.of(server);
        for (Active active : active(server)) {
            Story.Step step = active.step();
            if (step.kind().equals("shard") && shrine.equals(Wayfarer.id("shrine/" + step.text("eye"))) && !step.choices().isEmpty()
                    && step.spec().has(ASKS) && progress.done(step.text(ASKS)) && progress.choice(active.quest().id(), step.id()).isEmpty()) {
                Steps.ask(server, active, pedestal);
                return true;
            }
        }
        return false;
    }

    static Talk.Placeholders fill(Progress progress) {
        return text -> {
            String filled = text.replace("{stone}", progress.stone);
            if (filled.contains("{direction}")) {
                BlockPos from = progress.place(Caravan.NAME).orElse(BlockPos.ZERO);
                filled = filled.replace("{direction}", progress.place(FirstCamp.NAME).map(to -> FirstCamp.direction(from, to)).orElse(""));
            }
            return filled;
        };
    }

    static void choose(MinecraftServer server, String quest, String step, Story.Choice choice) {
        Progress progress = Progress.of(server);
        progress.choices.put(quest + "/" + step, choice.id());
        if (choice.story() != null) {
            progress.told.computeIfAbsent(quest, key -> new ArrayList<>()).add(choice.story());
        }
        progress.trust(choice.trusts());
        progress.setDirty();
        if (choice.reward() != null) {
            grant(server, choice.reward());
        }
    }

    static void chosen(ServerPlayer player, String questId, String stepId, @Nullable String speaker, Story.Choice choice) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        choose(server, questId, stepId, choice);
        Talk.say(questId, player.position(), new Story.Line(choice.narrated() ? null : speaker, choice.reply()));
        Story.get().quest(questId).ifPresent(quest -> quest.steps().stream().filter(step -> step.id().equals(stepId)).findFirst()
                .ifPresent(step -> Talk.then(() -> Steps.chosen(player, new Active(quest, step, progress.scratch(quest.id())), choice))));
    }

    private static List<String> introduced(Story.Step step) {
        List<String> names = new ArrayList<>(step.words().keySet());
        Stream.concat(step.lines().stream(), step.done().stream()).map(Story.Line::names).forEach(names::addAll);
        return names;
    }

    private static void record(MinecraftServer server, Progress progress, Story.Quest quest, Story.Step step, Talk.Placeholders fill) {
        List<String> story = step.spec().has("unwritten") && progress.stone.isEmpty() ? List.of(step.text("unwritten")) : step.story();
        if (!step.people().isEmpty() || !step.words().isEmpty()) {
            Sounds.play(server, JOURNAL_ENTRY);
        }
        step.people().keySet().forEach(person -> Notices.written(server, Component.translatable("wayfarer.notice.people", People.label(server, person))));
        step.words().keySet().forEach(word -> Notices.written(server, Component.translatable("wayfarer.notice.words", Component.translatable("wayfarer.word." + word))));
        story.stream().map(fill::fill).forEach(progress.told.computeIfAbsent(quest.id(), key -> new ArrayList<>())::add);
        step.people().forEach((person, line) -> progress.people.computeIfAbsent(person, key -> new ArrayList<>()).add(fill.fill(line)));
        step.words().forEach((word, line) -> progress.words.computeIfAbsent(word, key -> new ArrayList<>()).add(fill.fill(line)));
        progress.setDirty();
    }

    private static void grant(MinecraftServer server, Story.Reward reward) {
        Progress progress = Progress.of(server);
        if (!progress.rewards.contains(reward.key())) {
            progress.rewards.add(reward.key());
            progress.setDirty();
            Banners.show(Component.translatable("wayfarer.banner.reward", Tasks.key(Tasks.JOURNAL_KEY)), Component.literal(reward.title()));
        }
    }

    public static boolean claim(ServerPlayer player, String key) {
        Progress progress = Progress.of(player.server);
        Optional<Story.Reward> reward = rewards().stream().filter(found -> found.key().equals(key)).findFirst();
        if (reward.isEmpty() || !progress.rewards.contains(key) || progress.claimed(key, player.getUUID())) {
            return false;
        }
        List<ItemStack> given = new ArrayList<>();
        for (Story.Item item : reward.get().items()) {
            if (item.book() != null) {
                Pages.read(player.server, item.book());
            }
            ItemStack stack = stack(player, key, item);
            given.add(stack.copy());
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        Notices.given(player, given);
        progress.claims.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(player.getUUID());
        progress.setDirty();
        Talk.tell(player, Component.literal(reward.get().claimed()));
        Sounds.play(player, REWARD_TAKEN);
        Journal.sync(player);
        return true;
    }

    static ItemStack stack(ServerPlayer player, String key, Story.Item item) {
        if (item.own()) {
            return OwnStone.stone(player);
        }
        if (item.book() != null) {
            Library.Book book = Library.get().book(item.book()).orElseThrow(() -> new IllegalStateException(key + " gives the book " + item.book()
                    + ", which story/books.toml does not have"));
            ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
            written.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(book.title()), book.author(), 0,
                    book.pages().stream().map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(), true));
            return written;
        }
        ItemStack stack = new ItemStack(item.tag() == null ? BuiltInRegistries.ITEM.get(item.id())
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, item.tag()))
                .flatMap(tag -> tag.getRandomElement(RandomSource.create(player.getUUID().getLeastSignificantBits() ^ key.hashCode())))
                .map(Holder::value).orElseThrow(() -> new IllegalStateException(key + " gives an item from the empty tag " + item.tag())), item.count());
        if (item.name() != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()).withStyle(style -> style.withItalic(false)));
        }
        if (item.tooltip() != null) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(item.tooltip()).withStyle(ChatFormatting.GRAY))));
        }
        if (item.potion() != null) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.getHolder(item.potion())
                    .orElseThrow(() -> new IllegalStateException(key + " gives the potion " + item.potion() + ", which is not in this game"))));
        }
        return stack;
    }

    static List<Story.Reward> rewards() {
        List<Story.Reward> rewards = new ArrayList<>();
        for (Story.Quest quest : Story.get().quests()) {
            quest.steps().stream().map(Story.Step::reward).filter(reward -> reward != null).forEach(rewards::add);
            quest.steps().stream().flatMap(step -> step.choices().stream()).map(Story.Choice::reward).filter(reward -> reward != null).forEach(rewards::add);
            if (quest.reward() != null) {
                rewards.add(quest.reward());
            }
        }
        return rewards;
    }

    static List<String> found(CompoundTag scratch) {
        return scratch.getList("found", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }

    static void find(CompoundTag scratch, String clue) {
        ListTag found = scratch.getList("found", Tag.TAG_STRING);
        found.add(StringTag.valueOf(clue));
        scratch.put("found", found);
    }

    static List<ServerPlayer> players(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator()).toList();
    }

    static boolean most(MinecraftServer server, long count) {
        int online = players(server).size();
        return online > 0 && count * 100 >= (long) online * Balance.get().sleep().playersPercentage();
    }

    static ServerLevel overworld(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD);
    }

    static BlockPos at(MinecraftServer server, String place) {
        return Progress.of(server).place(place).orElseThrow(() -> new IllegalStateException("the story has no place called " + place + " yet"));
    }
}
