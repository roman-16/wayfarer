package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Climate;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Compass;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Shards;
import io.github.roman16.wayfarer.Handover;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.OwnStone;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.RestEvent;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Waking;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class Engine {
    private static final byte CHART_SCALE = 2;
    private static final String CARRIES = "carries:";
    private static final String ROAD = "road";
    private static final int REACH_TICKS = 640;
    private static final String STORIES = "stories";
    private static final String GIFT = "gift:";
    private static final String HOUR = "hour:";
    private static final String LAND = "land:";
    private static final String MEMBER = "joined:";
    private static final String PAGE = "page:";
    private static final String TRUSTED = "trusted:";
    private static final String TRUSTS = "trusts:";
    private static final String ALSO = "&";
    private static final int CHECK_TICKS = 10;
    private static final String EMBER = "ember";
    static final String MAIN = "main";
    static final String PROLOGUE = "prologue";
    private static final String JOURNAL_ENTRY = "journal_entry";
    private static final String QUEST_BEGUN = "quest_begun";
    private static final String WAIT = "wait";
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

    public static boolean holdsNight(MinecraftServer server) {
        return active(server).stream().anyMatch(active -> Steps.ownNight(active.step()) && (Steps.entered(active) || pursued(server, active.quest())));
    }

    public static Optional<Active> main(MinecraftServer server) {
        return active(server).stream().filter(active -> active.quest().kind().equals(MAIN)).findFirst();
    }

    public static Optional<Active> tracked(ServerPlayer player) {
        String chosen = Progress.of(player.server).tracking.get(player.getUUID());
        List<Active> active = active(player.server);
        return active.stream().filter(open -> open.quest().id().equals(chosen)).findFirst()
                .or(() -> active.stream().filter(open -> open.quest().follows()).findFirst());
    }

    static boolean pursued(MinecraftServer server, Story.Quest quest) {
        return players(server).stream().anyMatch(player -> tracked(player).filter(active -> active.quest() == quest).isPresent());
    }

    public static void track(ServerPlayer player, String quest) {
        Progress progress = Progress.of(player.server);
        if (quest.isEmpty()) {
            progress.tracking.remove(player.getUUID());
        } else if (progress.open(quest) && Story.get().quest(quest).filter(spec -> !waiting(progress, spec)).isPresent()) {
            progress.tracking.put(player.getUUID(), quest);
        } else {
            return;
        }
        progress.setDirty();
        Record.event(player.server, "tracked", player, line -> line.addProperty("quest", quest));
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
        Scenes.resume(server);
        open(server);
        Progress.of(server).setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !running(server) || Waking.asleep(server)) {
            return;
        }
        crossing(server);
        if (server.getTickCount() % REACH_TICKS == 0) {
            open(server);
        }
        for (Active active : active(server)) {
            if (!Steps.started(active) && !holds(Progress.of(server), active.quest(), active.step())) {
                pass(server, active);
            } else if (Steps.entered(active)) {
                Steps.tick(server, active);
            } else if (!Talk.busy() && (pursued(server, active.quest()) || active.step().kind().equals(WAIT))) {
                begin(server, active);
            }
        }
    }

    private static void pass(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        Story.Quest quest = active.quest();
        int next = progress.step(quest.id()) + 1;
        while (next < quest.steps().size() && !holds(progress, quest, quest.steps().get(next))) {
            next++;
        }
        progress.steps.put(quest.id(), next);
        progress.scratch.remove(quest.id());
        progress.setDirty();
        Journal.syncAll(server);
    }

    private static void begin(MinecraftServer server, Active active) {
        int index = active.quest().steps().indexOf(active.step());
        if (index == 0 && !active.step().kind().equals(WAIT)) {
            Banners.show(Component.translatable("wayfarer.banner.begun"), Component.literal(title(Progress.of(server), active.quest())));
            Sounds.play(server, QUEST_BEGUN);
        }
        if (active.step().kind().equals(WAIT)) {
            Progress progress = Progress.of(server);
            progress.tracking.values().removeIf(active.quest().id()::equals);
            progress.setDirty();
        }
        Steps.enter(server, active);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!People.story(target) && target.getTags().stream().noneMatch(tag -> tag.startsWith(Steps.CLUE))) {
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
        if (target.getTags().contains(Steps.CLUE_BOX)) {
            Clues.through(player, target);
            return;
        }
        if (!Forge.interact(player, target) && !Dice.interact(player, target) && !Scenes.interact(player, target)) {
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

    static Optional<BlockPos> goal(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!running(server)) {
            return Optional.empty();
        }
        Optional<String> goal = Caravan.homeward(player) ? Optional.of(Caravan.NAME) : tracked(player).flatMap(Steps::goal);
        return goal.flatMap(name -> Places.aim(Progress.of(server), player, name));
    }

    public static boolean sentNear(ServerPlayer player, int blocks) {
        return !Caravan.homeward(player) && goal(player).filter(pos -> pos.distToCenterSqr(player.getX(), pos.getY(), player.getZ()) <= (double) blocks * blocks)
                .isPresent();
    }

    static Optional<BlockPos> marked(ServerPlayer player) {
        return goal(player).filter(pos -> !felt(player.server, player, pos));
    }

    private static boolean felt(MinecraftServer server, ServerPlayer player, BlockPos goal) {
        int exact = Balance.get().compass().exactBlocks();
        return !Caravan.homeward(player) && tracked(player).map(Steps::felt).orElse(false)
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
                int exact = Balance.get().compass().exactBlocks();
                Steps.goal(side).ifPresent(name -> Places.aim(Progress.of(server), event.player(), name).filter(pos -> !Steps.felt(side)
                        || event.player().blockPosition().distToCenterSqr(pos.getX(), event.player().getY(), pos.getZ()) <= (double) exact * exact).ifPresent(pos -> event.markers()
                        .add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, Places.label(server, name), pos.getX(), pos.getY(), pos.getZ()))));
            }
        }
        Optional<String> goal = Caravan.homeward(event.player()) ? Optional.of(Caravan.NAME) : tracked.flatMap(Steps::goal);
        if (Caravan.homeward(event.player()) && !Caravan.met(server)) {
            event.rise();
        }
        Optional<Active> searching = Caravan.homeward(event.player()) ? Optional.empty() : tracked.filter(active -> active.step().kind().equals("clues"));
        goal.ifPresent(name -> Places.aim(Progress.of(server), event.player(), name)
                .map(place -> searching.flatMap(active -> Clues.nearest(event.player(), active)).orElse(place)).ifPresent(pos -> {
            event.aim(new Compass.Aim(pos));
            if (!felt(server, event.player(), pos)) {
                event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.GOAL, Places.label(server, name), pos.getX(), pos.getY(), pos.getZ()));
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
                    && (quest.found() == null || progress.place(quest.found()).isPresent()) && (quest.read() == null || progress.pages.contains(quest.read()))
                    && quest.when().stream().allMatch(condition -> holds(progress, condition)) && reachable(server, quest)) {
                progress.steps.put(quest.id(), 0);
                progress.scratch.remove(quest.id());
                progress.setDirty();
                Talk.say(quest.id(), null, carry(server, quest.id() + "/" + ROAD, quest.road()), fill(progress));
                if (!waiting(progress, quest)) {
                    Talk.then(quest.id(), () -> announce(server, quest));
                }
                opened.add(new Active(quest, quest.steps().getFirst(), progress.scratch(quest.id())));
            }
        }
        Journal.syncAll(server);
        return opened;
    }

    private static boolean reachable(MinecraftServer server, Story.Quest quest) {
        Progress progress = Progress.of(server);
        int reach = Balance.get().story().reachDays() * Balance.get().caravan().dayBlocks();
        List<Vec3> team = players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD).map(Entity::position).toList();
        return quest.steps().stream().filter(step -> step.spec().has("place")).map(step -> Places.aim(progress, step.text("place")))
                .flatMap(Optional::stream).allMatch(place -> team.isEmpty() || team.stream()
                        .anyMatch(at -> at.multiply(1, 0, 1).distanceTo(Vec3.atCenterOf(place).multiply(1, 0, 1)) <= reach));
    }

    static boolean waiting(Progress progress, Story.Quest quest) {
        return progress.steps.containsKey(quest.id()) && !progress.done(quest.id()) && quest.steps().get(progress.step(quest.id())).kind().equals(WAIT);
    }

    private static void announce(MinecraftServer server, Story.Quest quest) {
        if (!pursued(server, quest)) {
            Banners.show(Component.translatable("wayfarer.banner.new", Tasks.key(Tasks.JOURNAL_KEY)), Component.literal(title(Progress.of(server), quest)));
            Sounds.play(server, QUEST_BEGUN);
        }
    }

    private static void crossing(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = overworld(server);
        long day = Math.floorDiv(level.getDayTime(), Clock.DAY);
        if (progress.crossedDay != day) {
            progress.crossed.clear();
            progress.crossedDay = day;
            progress.setDirty();
        }
        for (ServerPlayer player : players(server)) {
            if (player.level() == level && progress.crossed.add(Climate.of(level.getBiome(player.blockPosition())))) {
                progress.setDirty();
            }
        }
    }

    static boolean holds(Progress progress, String when) {
        return Arrays.stream(when.split(ALSO)).allMatch(part -> part.startsWith("!") ? !met(progress, part.substring(1)) : met(progress, part));
    }

    private static boolean met(Progress progress, String condition) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (condition.startsWith(LAND)) {
            return progress.crossed.contains(Climate.valueOf(condition.substring(LAND.length()).toUpperCase(Locale.ROOT)));
        }
        if (condition.startsWith(HOUR)) {
            String[] span = condition.substring(HOUR.length()).split("-");
            long now = server == null ? 0 : Math.floorMod(overworld(server).getDayTime(), Clock.DAY);
            long from = Clock.ticks(span[0]);
            long to = Clock.ticks(span[1]);
            return from <= to ? now >= from && now < to : now >= from || now < to;
        }
        if (condition.startsWith(CARRIES)) {
            TagKey<Item> carried = TagKey.create(Registries.ITEM, Wayfarer.id(condition.substring(CARRIES.length())));
            return server != null && players(server).stream().anyMatch(player -> player.getInventory().contains(carried));
        }
        if (condition.startsWith(GIFT)) {
            return progress.gift(condition.substring(GIFT.length()));
        }
        if (condition.startsWith(TRUSTS)) {
            return progress.trust(condition.substring(TRUSTS.length())) > 0;
        }
        if (condition.startsWith(TRUSTED)) {
            return Doors.trusting(progress) >= Integer.parseInt(condition.substring(TRUSTED.length()));
        }
        if (condition.startsWith(MEMBER)) {
            return progress.joined.contains(condition.substring(MEMBER.length()));
        }
        if (condition.startsWith(PAGE)) {
            return progress.pages.contains(condition.substring(PAGE.length()));
        }
        return chose(progress, condition);
    }

    static boolean chose(Progress progress, String chose) {
        String[] step = chose.split("=", -1);
        return List.of(step[1].split("\\|", -1)).contains(progress.choices.getOrDefault(step[0], ""));
    }

    public static String title(Progress progress, Story.Quest quest) {
        return quest.renamed().entrySet().stream().filter(entry -> progress.named(entry.getKey())).map(Map.Entry::getValue).reduce((first, second) -> second)
                .orElse(quest.title());
    }

    static long halls(Progress progress, String act) {
        return Story.get().quests().stream().filter(quest -> quest.act().equals(act) && quest.hall() && progress.done(quest.id())).count();
    }

    public static void complete(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        if (progress.step(active.quest().id()) != active.quest().steps().indexOf(active.step())) {
            return;
        }
        Vec3 anchor = Steps.anchor(server, active.step());
        if (active.step().spec().has("shoots")) {
            People.shoot(server, active.step().text("shoots"));
        }
        List<Story.Line> done = active.step().done();
        if (active.step().kind().equals(Caravan.KIND) && !(Places.ready(server, Caravan.NAME) && Steps.home(server))) {
            done = carry(server, active.quest().id() + "/" + active.step().id(), done);
        }
        Talk.say(active.quest().id(), anchor, done, fill(progress));
        if (active.step().spec().has("finds")) {
            Talk.then(active.quest().id(), () -> open(server));
        }
        if (advance(server, active).isEmpty()) {
            Talk.then(active.quest().id(), () -> open(server));
        }
    }

    private static List<Story.Line> carry(MinecraftServer server, String key, List<Story.Line> lines) {
        Progress progress = Progress.of(server);
        List<Story.Line> said = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (absent(server, lines.get(index).by())) {
                progress.carried.add(key + "/" + index);
                progress.setDirty();
            } else {
                said.add(lines.get(index));
            }
        }
        return said;
    }

    private static boolean absent(MinecraftServer server, @Nullable String by) {
        if (by == null || Story.get().person(by).voice()) {
            return false;
        }
        int reach = Balance.get().talk().reachBlocks();
        return People.find(server, by).filter(body -> players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceTo(body) <= reach))
                .isEmpty();
    }

    public static void retell(MinecraftServer server) {
        Progress progress = Progress.of(server);
        for (String carried : List.copyOf(progress.carried)) {
            String[] key = carried.split("/");
            int index = Integer.parseInt(key[2]);
            Story.get().quest(key[0]).flatMap(quest -> key[1].equals(ROAD) ? Optional.of(quest.road().get(index))
                    : quest.steps().stream().filter(step -> step.id().equals(key[1])).findFirst().map(step -> step.done().get(index)))
                    .ifPresent(line -> Talk.say(key[0], null, List.of(line), fill(progress)));
        }
        progress.carried.clear();
        progress.setDirty();
    }

    static Optional<Active> advance(MinecraftServer server, Active active) {
        Progress progress = Progress.of(server);
        Story.Quest quest = active.quest();
        Story.Step step = active.step();
        Steps.faces(server, step);
        if (step.spec().has(Steps.MUSIC)) {
            Music.stop(server);
        }
        record(server, progress, quest, step, fill(progress));
        Record.event(server, "step ended", null, line -> {
            line.addProperty("quest", quest.id());
            line.addProperty("step", step.id());
        });
        People.introduce(server, introduced(step));
        if (!step.clues().isEmpty()) {
            Clues.clear(server, step);
        }
        if (step.spec().has("finds")) {
            Vec3 anchor = Steps.anchor(server, step);
            progress.place(step.text("finds"), anchor == null ? overworld(server).getSharedSpawnPos() : BlockPos.containing(anchor));
        }
        if (step.spec().has("joins")) {
            progress.joined.add(step.text("joins"));
            Caravan.join(server, step.text("joins"));
        }
        if (step.spec().has("teaches") && (!step.spec().has("teaches_when")
                || step.spec().getAsJsonArray("teaches_when").asList().stream().anyMatch(when -> holds(progress, when.getAsString())))) {
            progress.gifts.add(step.text("teaches"));
        }
        if (step.spec().has("page")) {
            Pages.read(server, step.text("page"));
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
            if (step.kind().equals(WAIT) && !quest.steps().get(next).kind().equals(WAIT)) {
                announce(server, quest);
            }
            return Optional.of(new Active(quest, quest.steps().get(next), progress.scratch(quest.id())));
        }
        if (quest.reward() != null) {
            grant(server, quest.reward());
        }
        Banners.show(Component.translatable("wayfarer.banner.done"), Component.literal(title(progress, quest)));
        Sounds.play(server, QUEST_DONE);
        Deeds.done(server, quest);
        After.done(server, quest);
        if (quest.kind().equals(MAIN) && !Caravan.near(server)) {
            Nights.calm(server);
        }
        return Optional.empty();
    }

    private static boolean holds(Progress progress, Story.Quest quest, Story.Step step) {
        if (step.spec().has(JOINED) && !progress.joined.contains(step.text(JOINED))) {
            return false;
        }
        if (!step.spec().has(WHEN)) {
            return true;
        }
        String condition = step.text(WHEN);
        if (condition.startsWith("!") || condition.contains("/") || condition.contains(":")) {
            return holds(progress, condition);
        }
        String[] when = condition.split("=", -1);
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

    public static boolean touches(ServerPlayer player, BlockPos stone) {
        return running(player.server) && Vision.touch(player, stone);
    }

    public static boolean asks(ServerPlayer player, BlockPos stone, ResourceLocation shard) {
        MinecraftServer server = player.server;
        if (!running(server)) {
            return false;
        }
        Progress progress = Progress.of(server);
        for (Active active : active(server)) {
            Story.Step step = active.step();
            if (step.kind().equals("shard") && Steps.eyes(step).contains(Shards.eye(shard)) && asking(progress, step)
                    && progress.choice(active.quest().id(), step.id()).isEmpty()) {
                Steps.ask(server, active, stone);
                return true;
            }
        }
        return false;
    }

    static boolean asking(Progress progress, Story.Step step) {
        return !step.choices().isEmpty() && (!step.spec().has(ASKS) || progress.done(step.text(ASKS)));
    }

    static Talk.Placeholders fill(Progress progress) {
        return text -> text.replace("{stone}", progress.stone);
    }

    static void choose(MinecraftServer server, String quest, String step, Story.Choice choice) {
        Progress progress = Progress.of(server);
        progress.choices.put(quest + "/" + step, choice.id());
        if (choice.story() != null) {
            progress.told.computeIfAbsent(quest, key -> new ArrayList<>()).add(choice.story());
        }
        progress.trust(choice.trusts());
        if (choice.leaves() != null) {
            progress.joined.remove(choice.leaves());
            People.leave(server, choice.leaves());
        }
        progress.setDirty();
        if (choice.reward() != null) {
            grant(server, choice.reward());
        }
    }

    static void chosen(ServerPlayer player, String questId, String stepId, @Nullable String speaker, Story.Choice choice) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        if (choice.refused() != null && choice.when() != null && !holds(progress, choice.when())) {
            Talk.say(questId, player.position(), new Story.Line(speaker, choice.refused()));
            Story.get().quest(questId).ifPresent(quest -> quest.steps().stream().filter(step -> step.id().equals(stepId)).findFirst()
                    .ifPresent(step -> Talk.then(questId, () -> Steps.offer(server, new Active(quest, step, progress.scratch(quest.id()))))));
            return;
        }
        choose(server, questId, stepId, choice);
        if (!choice.reply().isEmpty()) {
            Talk.say(questId, player.position(), new Story.Line(choice.narrated() ? null : speaker, choice.reply()));
        }
        Story.get().quest(questId).ifPresent(quest -> quest.steps().stream().filter(step -> step.id().equals(stepId)).findFirst()
                .ifPresent(step -> Talk.then(questId, () -> Steps.chosen(player, new Active(quest, step, progress.scratch(quest.id())), choice))));
    }

    private static List<String> introduced(Story.Step step) {
        List<String> names = new ArrayList<>(step.words().keySet());
        Stream.concat(step.lines().stream(), step.done().stream()).map(Story.Line::names).forEach(names::addAll);
        return names;
    }

    private static void record(MinecraftServer server, Progress progress, Story.Quest quest, Story.Step step, Talk.Placeholders fill) {
        List<String> story = step.spec().has("unwritten") && progress.stone.isEmpty() ? List.of(step.text("unwritten"))
                : progress.choice(quest.id(), step.id()).filter(outcome -> step.spec().has(STORIES) && step.spec().getAsJsonObject(STORIES).has(outcome))
                        .map(outcome -> step.spec().getAsJsonObject(STORIES).getAsJsonArray(outcome).asList().stream().map(JsonElement::getAsString).toList())
                        .orElse(step.story());
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
            Talk.whenQuiet(reward.key(), () -> Banners.show(Component.translatable("wayfarer.banner.reward", Tasks.key(Tasks.JOURNAL_KEY)),
                    Component.literal(reward.title())));
        }
    }

    public static boolean claim(ServerPlayer player, String key) {
        Progress progress = Progress.of(player.server);
        Optional<Story.Reward> reward = rewards().stream().filter(found -> found.key().equals(key)).findFirst();
        if (reward.isEmpty() || !progress.rewards.contains(key) || progress.claimed(key, player.getUUID())) {
            return false;
        }
        List<Story.Item> items = reward.get().items();
        List<ItemStack> stacks = items.stream().map(item -> stack(player, key, item)).toList();
        long free = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
        long needed = IntStream.range(0, stacks.size()).filter(index -> !stacks.get(index).isEmpty() && (items.get(index).worn()
                ? !player.getItemBySlot(player.getEquipmentSlotForItem(stacks.get(index))).isEmpty()
                : player.getInventory().getSlotWithRemainingSpace(stacks.get(index)) < 0)).count();
        if (needed > free) {
            player.displayClientMessage((needed == 1 ? Component.translatable("wayfarer.journal.room_one") : Component.translatable("wayfarer.journal.room", needed))
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        List<ItemStack> given = new ArrayList<>();
        for (Story.Item item : reward.get().items()) {
            if (item.book() != null) {
                Pages.read(player.server, item.book());
            }
        }
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            given.add(stack.copy());
            if (items.get(index).worn()) {
                Handover.wear(player, stack);
            } else {
                Handover.give(player, stack);
            }
        }
        Notices.given(player, given);
        Record.event(player.server, "reward", player, line -> line.addProperty("reward", key));
        Set<UUID> claimed = progress.claims.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        boolean first = claimed.isEmpty();
        claimed.add(player.getUUID());
        progress.setDirty();
        Tasks.refresh(player);
        if (first && reward.get().given() != null) {
            Talk.say(Talk.ROAD, player.position(), reward.get().given());
        }
        Talk.tell(player, Component.literal(reward.get().claimed()));
        Sounds.play(player, REWARD_TAKEN);
        Journal.sync(player);
        return true;
    }

    static ItemStack stack(ServerPlayer player, String key, Story.Item item) {
        if (item.own()) {
            return OwnStone.stone(player);
        }
        if (item.map() != null) {
            return chart(player, item);
        }
        if (item.book() != null) {
            Library.Book book = Library.get().book(item.book()).orElseThrow(() -> new IllegalStateException(key + " gives the book " + item.book()
                    + ", which story/books.toml does not have"));
            return book.written();
        }
        if (item.tent() != null) {
            return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Prologue.TENTS, item.tent() + "_" + Prologue.tent())));
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
        if (item.instrument() != null) {
            stack.set(DataComponents.INSTRUMENT, BuiltInRegistries.INSTRUMENT.getHolder(item.instrument())
                    .orElseThrow(() -> new IllegalStateException(key + " gives the instrument " + item.instrument() + ", which is not in this game")));
        }
        if (item.potion() != null) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.getHolder(item.potion())
                    .orElseThrow(() -> new IllegalStateException(key + " gives the potion " + item.potion() + ", which is not in this game"))));
        }
        return stack;
    }

    private static ItemStack chart(ServerPlayer player, Story.Item item) {
        Places.build(player.server, item.map());
        Optional<BlockPos> at = Places.place(player.server, item.map());
        ServerLevel level = overworld(player.server);
        ItemStack chart = at.map(target -> {
            ItemStack drawn = MapItem.create(level, target.getX(), target.getZ(), CHART_SCALE, true, true);
            MapItem.renderBiomePreviewMap(level, drawn);
            MapItemSavedData.addTargetDecoration(drawn, target, "+", MapDecorationTypes.RED_X);
            return drawn;
        }).orElseGet(() -> new ItemStack(Items.MAP));
        if (item.name() != null) {
            chart.set(DataComponents.CUSTOM_NAME, Component.literal(item.name()).withStyle(style -> style.withItalic(false)));
        }
        if (item.tooltip() != null) {
            chart.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(item.tooltip()).withStyle(ChatFormatting.GRAY))));
        }
        return chart;
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
