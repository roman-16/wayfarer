package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Music;
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
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
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
    private static final String JOURNAL_ENTRY = "journal_entry";
    private static final String QUEST_BEGUN = "quest_begun";
    private static final String QUEST_DONE = "quest_done";
    private static final String REWARD_TAKEN = "reward_taken";
    private static final String STARTED = "started";

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
        for (Story.Quest quest : Story.INSTANCE.quests()) {
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
        return active(player.server).stream().filter(active -> active.quest().id().equals(chosen)).findFirst().or(() -> main(player.server));
    }

    public static void track(ServerPlayer player, String quest) {
        Progress progress = Progress.of(player.server);
        if (progress.open(quest)) {
            progress.tracking.put(player.getUUID(), quest);
            progress.setDirty();
            Journal.sync(player);
        }
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
            } else {
                Steps.enter(server, active);
            }
        }
        openQuests(server);
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
            }
        }
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
        Optional<String> goal = Caravan.heading(server) ? Optional.of(Caravan.NAME) : tracked(player).flatMap(Steps::goal);
        return goal.flatMap(name -> Progress.of(server).place(name));
    }

    static Optional<BlockPos> marked(ServerPlayer player) {
        return goal(player).filter(pos -> !felt(player.server, player, pos));
    }

    private static boolean felt(MinecraftServer server, ServerPlayer player, BlockPos goal) {
        int exact = Balance.INSTANCE.compass().exactBlocks();
        return !Caravan.heading(server) && tracked(player).map(Steps::felt).orElse(false)
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
            if (tracked.map(active -> active.quest() != side.quest()).orElse(true)) {
                Steps.goal(side).ifPresent(name -> Progress.of(server).place(name).ifPresent(pos -> event.markers()
                        .add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, "wayfarer.place." + name, pos.getX(), pos.getZ()))));
            }
        }
        Optional<String> goal = Caravan.heading(server) ? Optional.of(Caravan.NAME) : tracked.flatMap(Steps::goal);
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
        main(player.server).filter(active -> active.step().spec().has("awake")).ifPresent(active -> {
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

    public static void openQuests(MinecraftServer server) {
        open(server).forEach(active -> Steps.enter(server, active));
    }

    static List<Active> open(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List<Active> opened = new ArrayList<>();
        for (Story.Quest quest : Story.INSTANCE.quests()) {
            if (!progress.steps.containsKey(quest.id()) && quest.after().stream().allMatch(progress::done)
                    && (quest.found() == null || progress.place(quest.found()).isPresent())) {
                progress.steps.put(quest.id(), 0);
                progress.scratch.remove(quest.id());
                progress.setDirty();
                Banners.show(Component.translatable("wayfarer.banner.begun", quest.title()));
                Sounds.play(server, QUEST_BEGUN);
                opened.add(new Active(quest, quest.steps().getFirst(), progress.scratch(quest.id())));
            }
        }
        Journal.syncAll(server);
        return opened;
    }

    public static void complete(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        if (progress.step(active.quest().id()) != active.quest().steps().indexOf(active.step())) {
            return;
        }
        Talk.say(active.step().done(), fill(progress));
        Optional<Active> next = advance(server, active);
        if (next.isPresent()) {
            Talk.then(() -> Steps.enter(server, next.get()));
        } else {
            Talk.then(() -> openQuests(server));
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
        if (step.reward() != null) {
            grant(server, step.reward());
        }
        int next = progress.step(quest.id()) + 1;
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
        Banners.show(Component.translatable("wayfarer.banner.done", quest.title()));
        Sounds.play(server, QUEST_DONE);
        return Optional.empty();
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
        progress.setDirty();
    }

    static void chosen(ServerPlayer player, String questId, String stepId, @Nullable String speaker, Story.Choice choice) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        choose(server, questId, stepId, choice);
        Talk.say(new Story.Line(speaker, choice.reply()));
        Story.INSTANCE.quest(questId).ifPresent(quest -> quest.steps().stream().filter(step -> step.id().equals(stepId)).findFirst()
                .ifPresent(step -> Talk.then(() -> Steps.chosen(player, new Active(quest, step, progress.scratch(quest.id())), choice))));
    }

    private static List<String> introduced(Story.Step step) {
        List<String> names = new ArrayList<>(step.words().keySet());
        Stream.concat(step.lines().stream(), step.done().stream()).map(Story.Line::names).forEach(names::addAll);
        return names;
    }

    private static void record(MinecraftServer server, Progress progress, Story.Quest quest, Story.Step step, Talk.Placeholders fill) {
        List<String> story = step.spec().has("unwritten") && progress.stone.isEmpty() ? List.of(step.text("unwritten")) : step.story();
        if (!story.isEmpty() || !step.people().isEmpty() || !step.words().isEmpty()) {
            Sounds.play(server, JOURNAL_ENTRY);
        }
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
            Banners.show(Component.translatable("wayfarer.banner.reward", reward.title(), Component.keybind("key.wayfarer.journal")));
        }
    }

    public static boolean claim(ServerPlayer player, String key) {
        Progress progress = Progress.of(player.server);
        Optional<Story.Reward> reward = rewards().stream().filter(found -> found.key().equals(key)).findFirst();
        if (reward.isEmpty() || !progress.rewards.contains(key) || progress.claimed(key, player.getUUID())) {
            return false;
        }
        for (Story.Item item : reward.get().items()) {
            ItemStack stack = stack(player, key, item);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        progress.claims.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(player.getUUID());
        progress.setDirty();
        player.sendSystemMessage(Component.literal(reward.get().claimed()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Sounds.play(player, REWARD_TAKEN);
        Journal.sync(player);
        return true;
    }

    static ItemStack stack(ServerPlayer player, String key, Story.Item item) {
        if (item.own()) {
            return OwnStone.stone(player);
        }
        if (item.book() != null) {
            Library.Book book = Library.INSTANCE.book(item.book()).orElseThrow(() -> new IllegalStateException(key + " gives the book " + item.book()
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
        return stack;
    }

    static List<Story.Reward> rewards() {
        List<Story.Reward> rewards = new ArrayList<>();
        for (Story.Quest quest : Story.INSTANCE.quests()) {
            quest.steps().stream().map(Story.Step::reward).filter(reward -> reward != null).forEach(rewards::add);
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
        return online > 0 && count * 100 >= (long) online * Balance.INSTANCE.sleep().playersPercentage();
    }

    static ServerLevel overworld(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD);
    }

    static BlockPos at(MinecraftServer server, String place) {
        return Progress.of(server).place(place).orElseThrow(() -> new IllegalStateException("the story has no place called " + place + " yet"));
    }
}
