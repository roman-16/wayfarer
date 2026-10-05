package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.RestEvent;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Engine {
    private static final int CHECK_TICKS = 10;
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

    public static Optional<Active> tracked(MinecraftServer server) {
        return active(server).stream().filter(active -> active.quest().kind().equals("main")).findFirst();
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
        Remarks.answer(player, target);
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

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        MinecraftServer server = event.player().server;
        if (!running(server)) {
            return;
        }
        tracked(server).flatMap(Steps::goal).ifPresent(goal -> Progress.of(server).place(goal).ifPresent(pos -> {
            event.aim(Compass.Aim.NONE);
            event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, "wayfarer.place." + goal, pos.getX(), pos.getZ()));
        }));
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
            if (!progress.steps.containsKey(quest.id()) && quest.after().stream().allMatch(progress::done)) {
                progress.steps.put(quest.id(), 0);
                progress.scratch.remove(quest.id());
                progress.setDirty();
                Talk.notice(Component.translatable("wayfarer.quest.new", quest.title()).withStyle(ChatFormatting.GOLD));
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
        Talk.say(active.step().done(), stone(progress));
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
        record(progress, step, stone(progress));
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
        Talk.notice(Component.translatable("wayfarer.quest.done", quest.title()).withStyle(ChatFormatting.GOLD));
        return Optional.empty();
    }

    private static Talk.Placeholders stone(Progress progress) {
        return text -> text.replace("{stone}", progress.stone);
    }

    static void choose(MinecraftServer server, String quest, String step, Story.Choice choice) {
        Progress progress = Progress.of(server);
        progress.choices.put(quest + "/" + step, choice.id());
        if (choice.story() != null) {
            progress.told.add(choice.story());
        }
        progress.setDirty();
    }

    static void chosen(MinecraftServer server, Talk.Offer offer, Story.Choice choice) {
        Progress progress = Progress.of(server);
        choose(server, offer.quest(), offer.step(), choice);
        Talk.say(new Story.Line(offer.speaker(), choice.reply()));
        Story.INSTANCE.quest(offer.quest()).ifPresent(quest -> quest.steps().stream().filter(step -> step.id().equals(offer.step())).findFirst()
                .ifPresent(step -> Talk.then(() -> complete(server, new Active(quest, step, progress.scratch(quest.id()))))));
    }

    private static void record(Progress progress, Story.Step step, Talk.Placeholders fill) {
        List<String> story = step.spec().has("unwritten") && progress.stone.isEmpty() ? List.of(step.text("unwritten")) : step.story();
        story.stream().map(fill::fill).forEach(progress.told::add);
        step.people().forEach((person, line) -> progress.people.computeIfAbsent(person, key -> new ArrayList<>()).add(fill.fill(line)));
        step.words().forEach((word, line) -> progress.words.computeIfAbsent(word, key -> new ArrayList<>()).add(fill.fill(line)));
        progress.setDirty();
    }

    private static void grant(MinecraftServer server, Story.Reward reward) {
        Progress progress = Progress.of(server);
        if (!progress.rewards.contains(reward.key())) {
            progress.rewards.add(reward.key());
            progress.setDirty();
            Talk.notice(Component.translatable("wayfarer.reward.waiting", reward.title(), Component.keybind("key.wayfarer.journal")).withStyle(ChatFormatting.GOLD));
        }
    }

    public static boolean claim(ServerPlayer player, String key) {
        Progress progress = Progress.of(player.server);
        Optional<Story.Reward> reward = rewards().stream().filter(found -> found.key().equals(key)).findFirst();
        if (reward.isEmpty() || !progress.rewards.contains(key) || progress.claimed(key, player.getUUID())) {
            return false;
        }
        for (Story.Item item : reward.get().items()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item.id()), item.count());
            if (item.name() != null) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()).withStyle(style -> style.withItalic(false)));
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        progress.claims.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(player.getUUID());
        progress.setDirty();
        player.sendSystemMessage(Component.literal(reward.get().claimed()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Journal.sync(player);
        return true;
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
