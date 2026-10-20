package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Ember;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Joining;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Shards;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class StoryDev {
    private static final Bundled<JsonObject> STATES = Bundled.json("/wayfarer/states.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_story", Comparator.comparingLong(ChunkPos::toLong));
    private static final int OWNER = 4;
    private static final int LOAD_RADIUS = 2;
    private static final int ROAD_BEYOND = 32;
    private static final int ROAD_SAMPLE = 16;
    private static final int[] HEADINGS = {0, 45, -45, 90, -90, 135, -135, 180};
    private static final int CAMP_REACH = 8;
    private static final int STAND_NEAR = 3;
    private static final int STAND_FAR = 4;
    private static final int RING_STEPS = 8;
    private static final int HEIGHT = 4;
    private static final double EYES = 1.6;
    private static final String ANSWER = "answer";
    private static final String CHARMS = "charms";
    private static final String COMPASS = "compass";
    private static final String GEAR = "gear";
    private static final String JOURNAL = "journal";
    private static final String LOOK = "look";
    private static final String MAP = "map";
    @Nullable
    private static Run run;
    private static Map<UUID, Set<String>> marks = Map.of();

    private StoryDev() {}

    private static final class Run {
        private final CommandSourceStack source;
        @Nullable
        private final String target;
        @Nullable
        private final Path save;
        private final Map<String, String> answers;
        @Nullable
        private final String stone;
        @Nullable
        private final Runnable then;
        private final Map<UUID, GameType> modes = new HashMap<>();
        private BlockPos here;
        @Nullable
        private ChunkPos ticket;
        @Nullable
        private String building;
        @Nullable
        private String moved;
        private int played;

        private Run(CommandSourceStack source, @Nullable String target, @Nullable Path save, Way way, BlockPos here) {
            this.source = source;
            this.target = target;
            this.save = save;
            this.answers = way.answers();
            this.stone = way.stone();
            this.then = way.then();
            this.here = here;
        }
    }

    record Way(Map<String, String> answers, @Nullable String stone, @Nullable Runnable then) {
        private static final Way FIRST = new Way(Map.of(), null, null);
    }

    public static long seed() {
        return STATES.get().get("seed").getAsLong();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("story")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.literal("build").then(Commands.argument("folder", StringArgumentType.string())
                                .then(Commands.argument("step", StringArgumentType.greedyString()).executes(context -> begin(context.getSource(),
                                        StringArgumentType.getString(context, "step"), Path.of(StringArgumentType.getString(context, "folder")), Way.FIRST)))))
                        .then(Commands.literal("next").executes(context -> begin(context.getSource(), null, null, Way.FIRST)))
                        .then(Commands.literal("to").then(Commands.argument("step", StringArgumentType.greedyString())
                                .executes(context -> begin(context.getSource(), StringArgumentType.getString(context, "step"), null, Way.FIRST))))
                        .then(Commands.literal("side").then(Commands.argument("step", StringArgumentType.greedyString())
                                .executes(context -> side(context.getSource(), StringArgumentType.getString(context, "step")))))
                        .then(Commands.literal("gift").then(Commands.argument("gift", StringArgumentType.word())
                                .executes(context -> gift(context.getSource(), StringArgumentType.getString(context, "gift")))))
                        .then(Commands.literal("deed").then(Commands.argument("deed", StringArgumentType.word())
                                .executes(context -> deed(context.getSource(), StringArgumentType.getString(context, "deed")))))
                        .then(Commands.literal("choose").then(Commands.argument("choice", StringArgumentType.greedyString())
                                .executes(context -> choose(context.getSource(), StringArgumentType.getString(context, "choice")))))
                        .then(Commands.literal("gear").then(Commands.argument("row", StringArgumentType.word())
                                .executes(context -> gear(context.getSource(), StringArgumentType.getString(context, "row")))))
                        .then(Commands.literal("said").then(Commands.argument("line", StringArgumentType.word())
                                .executes(context -> said(context.getSource(), StringArgumentType.getString(context, "line")))))
                        .then(Commands.literal("unclaim").then(Commands.argument("reward", StringArgumentType.greedyString())
                                .executes(context -> unclaim(context.getSource(), StringArgumentType.getString(context, "reward")))))
                        .then(Commands.literal("marks")
                                .then(Commands.literal("keep").executes(context -> keepMarks(context.getSource())))
                                .then(Commands.literal("restore").executes(context -> restoreMarks(context.getSource()))))
                        .then(Commands.literal("memory").then(Commands.argument("step", StringArgumentType.word())
                                .executes(context -> memory(context.getSource(), StringArgumentType.getString(context, "step")))))
                        .then(Commands.literal("marsh").then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(context -> marsh(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"))))))));
    }

    private static int marsh(CommandSourceStack source, int x, int z) {
        Marsh.move(source.getServer(), x, z);
        source.sendSuccess(() -> Component.literal("story: the Marsh Mothers' hall moves under " + x + " " + z), false);
        return 1;
    }

    private static int memory(CommandSourceStack source, String step) {
        if (!Memories.jump(source.getServer(), step)) {
            source.sendFailure(Component.literal("story failed: no memory is dreamed that has a step " + step));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("story: the memory stands at " + step), false);
        return 1;
    }

    private static int gear(CommandSourceStack source, String row) {
        if (!Gear.show(Progress.of(source.getServer()), row)) {
            source.sendFailure(Component.literal("story failed: story/gear.toml has no row " + row));
            return 0;
        }
        Journal.syncAll(source.getServer());
        source.getServer().getPlayerList().getPlayers().forEach(Tasks::refresh);
        source.sendSuccess(() -> Component.literal("story: the gear row " + row + " shows"), false);
        return 1;
    }

    private static int said(CommandSourceStack source, String line) {
        if (!Gear.said(Progress.of(source.getServer()), line)) {
            source.sendFailure(Component.literal("story failed: story/gear.toml has no line " + line));
            return 0;
        }
        Journal.syncAll(source.getServer());
        source.getServer().getPlayerList().getPlayers().forEach(Tasks::refresh);
        source.sendSuccess(() -> Component.literal("story: " + line + " was said"), false);
        return 1;
    }

    private static int keepMarks(CommandSourceStack source) {
        marks = copy(Progress.of(source.getServer()).seen);
        source.sendSuccess(() -> Component.literal("story: the journal's marks are kept"), false);
        return 1;
    }

    private static int restoreMarks(CommandSourceStack source) {
        Progress progress = Progress.of(source.getServer());
        progress.seen.clear();
        progress.seen.putAll(copy(marks));
        progress.setDirty();
        Journal.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("story: the journal's marks stand as kept"), false);
        return 1;
    }

    private static Map<UUID, Set<String>> copy(Map<UUID, Set<String>> seen) {
        Map<UUID, Set<String>> copied = new LinkedHashMap<>();
        seen.forEach((player, entries) -> copied.put(player, new LinkedHashSet<>(entries)));
        return copied;
    }

    static boolean playing() {
        return run != null;
    }

    private static int side(CommandSourceStack source, String target) {
        String[] parts = target.split("/", 2);
        Optional<Story.Quest> quest = Story.get().quest(parts[0]);
        int index = quest.map(found -> found.steps().stream().map(Story.Step::id).toList().indexOf(parts.length > 1 ? parts[1] : "")).orElse(-1);
        if (quest.isEmpty() || index < 0) {
            source.sendFailure(Component.literal("story failed: no quest step " + target));
            return 0;
        }
        Progress progress = Progress.of(source.getServer());
        Talk.reset();
        progress.steps.put(quest.get().id(), index);
        progress.scratch.remove(quest.get().id());
        progress.setDirty();
        Journal.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("story: " + target + " begins"), false);
        return 1;
    }

    private static int gift(CommandSourceStack source, String gift) {
        Progress progress = Progress.of(source.getServer());
        progress.gifts.add(gift);
        progress.setDirty();
        Journal.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("story: the team holds the gift " + gift), false);
        return 1;
    }

    private static int deed(CommandSourceStack source, String deed) throws CommandSyntaxException {
        if (!Deeds.ALL.contains(deed)) {
            source.sendFailure(Component.literal("story failed: there is no deed " + deed));
            return 0;
        }
        Deeds.earn(source.getPlayerOrException(), deed);
        source.sendSuccess(() -> Component.literal("story: the deed " + deed + " is earned"), false);
        return 1;
    }

    private static int choose(CommandSourceStack source, String spoken) {
        String[] words = spoken.trim().split(" +");
        if (words.length != 2) {
            source.sendFailure(Component.literal("story failed: choose takes a choice and its answer, as the_hollow/pip lost"));
            return 0;
        }
        String choice = words[0];
        String answer = words[1];
        Progress progress = Progress.of(source.getServer());
        progress.choices.put(choice, answer);
        progress.setDirty();
        Journal.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("story: " + choice + " stands as " + answer), false);
        return 1;
    }

    private static int unclaim(CommandSourceStack source, String reward) {
        ServerPlayer player = source.getPlayer();
        Progress progress = Progress.of(source.getServer());
        if (player == null || !progress.claimed(reward, player.getUUID())) {
            source.sendFailure(Component.literal("story failed: " + (player == null ? "only a player has rewards to give back" : "no reward " + reward
                    + " was taken; taken are " + progress.claims.keySet())));
            return 0;
        }
        progress.claims.get(reward).remove(player.getUUID());
        progress.setDirty();
        Journal.sync(player);
        Tasks.refresh(player);
        source.sendSuccess(() -> Component.literal("story: " + reward + " waits again"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (run == null) {
            return;
        }
        try {
            go(event.getServer());
        } catch (RuntimeException exception) {
            if (run != null) {
                fail(event.getServer(), exception.getMessage() == null ? exception.toString() : exception.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        run = null;
    }

    static int begin(CommandSourceStack source, @Nullable String target, @Nullable Path save, Way way) {
        MinecraftServer server = source.getServer();
        if (run != null) {
            source.sendFailure(Component.literal("story failed: it is already on its way to " + (run.target == null ? "the next step" : run.target)));
            return 0;
        }
        if (server.getPlayerList().getPlayerCount() == 0) {
            source.sendFailure(Component.literal("story failed: nobody is online to walk it; bring someone in with wayfarer pretend join <name>"));
            return 0;
        }
        Optional<Engine.Active> current = Engine.main(server);
        if (target != null) {
            Optional<String> wrong = wrong(server, target);
            if (wrong.isPresent()) {
                source.sendFailure(Component.literal("story failed: " + wrong.get()));
                return 0;
            }
            if (save == null && current.filter(active -> active.key().equals(target) && Steps.entered(active)).isPresent()) {
                source.sendSuccess(() -> Component.literal("story: at " + target + ", where it already stood"), false);
                if (way.then() != null) {
                    way.then().run();
                }
                return 1;
            }
        }
        run = new Run(source, target, save, way, server.getPlayerList().getPlayers().getFirst().blockPosition());
        Talk.reset();
        current.filter(Steps::entered).ifPresent(active -> {
            run.moved = active.key();
            Steps.reset(server, active);
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            run.modes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);
        }
        return 1;
    }

    private static Optional<String> wrong(MinecraftServer server, String target) {
        String[] parts = target.split("/", 2);
        Optional<Story.Quest> quest = Story.get().quest(parts[0]);
        int index = quest.map(found -> found.steps().stream().map(Story.Step::id).toList().indexOf(parts.length > 1 ? parts[1] : "")).orElse(-1);
        if (index < 0) {
            return Optional.of("there is no step " + target + "; a step is named quest/step, as ash/the_door is");
        }
        Progress progress = Progress.of(server);
        if (progress.done(parts[0]) || progress.open(parts[0]) && progress.step(parts[0]) > index) {
            return Optional.of(target + " is behind; the story stands at " + Engine.main(server).map(Engine.Active::key).orElse("its end"));
        }
        return Optional.empty();
    }

    private static void go(MinecraftServer server) {
        if (run.building != null) {
            hurry(server, run.building);
            if (!Places.ready(server, run.building)) {
                return;
            }
            run.building = null;
        }
        while (run != null && loaded(server, run.here)) {
            Optional<Engine.Active> tracked = Engine.main(server);
            if (tracked.isEmpty()) {
                if (run.target == null && run.played > 0) {
                    stand(server, run.here);
                    say("story: at the end, after " + steps(run.played));
                    finish(server);
                } else {
                    fail(server, run.target == null ? "the story has ended" : "the story ends before " + run.target);
                }
                return;
            }
            Engine.Active active = tracked.get();
            if (run.target == null ? run.played > 0 : active.key().equals(run.target)) {
                arrive(server, active);
                return;
            }
            if (run.save != null) {
                keep(server, active);
            }
            learn(server, active);
            if (!play(server, active)) {
                return;
            }
            run.played++;
            say("story: played " + active.key());
        }
    }

    private static boolean play(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Balance.Clock clock = Balance.get().clock();
        if (!active.key().equals(run.moved)) {
            Steps.moves(server, step);
            run.moved = active.key();
        }
        if (step.spec().has(Steps.BUILDS) && !built(server, step.text(Steps.BUILDS))) {
            return false;
        }
        Steps.toll(server, active);
        switch (step.kind()) {
            case "talk" -> {
                if (step.spec().has("place") && !built(server, step.text("place"))) {
                    return false;
                }
                answer(active).ifPresent(choice -> Engine.choose(server, active.quest().id(), step.id(), choice));
                body(server, step.with()).ifPresent(at -> run.here = at);
            }
            case "bury" -> {
                People.find(server, step.text("whom")).ifPresent(Entity::discard);
                BlockPos sign = Prologue.grave(level);
                String words = run.stone != null ? run.stone : step.choices().stream().map(Story.Choice::stone).filter(Objects::nonNull).findFirst().orElse("");
                Prologue.engrave(level, sign, words);
                progress.stone = words;
                run.here = sign;
            }
            case "walk" -> run.here = step.text("from").equals(Prologue.LAST_CAMP) ? road(level, Engine.at(server, step.text("from")))
                    : Engine.at(server, step.text("from"));
            case "camp" -> {
                BlockPos fire = ground(level, run.here, 0, CAMP_REACH);
                level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
                progress.place(Steps.OWN_CAMP, fire);
                run.here = fire;
            }
            case "dusk" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                Clock.jump(level, clock.dusk(), clock.night());
                Journey.of(server).wake();
            }
            case "reach" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                run.here = Engine.at(server, step.text("place"));
            }
            case "shard" -> {
                Optional<String> chosen = progress.choice(active.quest().id(), step.id());
                Optional<Story.Choice> choice = chosen.flatMap(id -> step.choices().stream().filter(offered -> offered.id().equals(id)).findFirst())
                        .or(() -> answer(active));
                if (chosen.isEmpty() && Engine.asking(progress, step)) {
                    choice.ifPresent(first -> Engine.choose(server, active.quest().id(), step.id(), first));
                }
                List<String> taking = choice.map(Story.Choice::takes).orElseGet(() -> Steps.eyes(step));
                taking.forEach(eye -> Journey.of(server).claim(Steps.hall(eye)));
                server.getPlayerList().getPlayers().forEach(Shards::sync);
                Steps.gifts(progress, step, taking);
                run.here = Engine.at(server, step.text("at"));
            }
            case "rest" -> {
                BlockPos fire = Engine.at(server, step.text("at"));
                server.getPlayerList().getPlayers().forEach(player -> player.setData(Wayfarer.LAST_FIRE, GlobalPos.of(Level.OVERWORLD, fire)));
                run.here = fire;
            }
            case "hold" -> {
                Hold.stop();
                if (step.spec().has("from")) {
                    Ford.lay(server);
                    run.here = Engine.at(server, step.text("at"));
                }
            }
            case "dawn", "sleep" -> Clock.jump(level, Steps.DAWN, clock.road());
            case "sail" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                run.here = Engine.at(server, Shipyard.NAME);
            }
            case "vision" -> {
                if (!built(server, step.text("at"))) {
                    return false;
                }
                run.here = Engine.at(server, step.text("at"));
            }
            case "duel" -> {
                if (!built(server, step.text("at"))) {
                    return false;
                }
                progress.choices.put(active.key(), outcome(active, Duel.WON));
                run.here = Engine.at(server, step.text("at"));
            }
            case "chase" -> progress.choices.put(active.key(), outcome(active, Chase.CAUGHT));
            case "board" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                run.here = Engine.at(server, step.text("place"));
            }
            case "escort" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                progress.choices.put(active.key(), outcome(active, Escort.ARRIVED));
                run.here = Engine.at(server, step.text("place"));
            }
            case "locks" -> {
                if (!built(server, Locks.NAME)) {
                    return false;
                }
                progress.gifts.add("stone_sight");
                run.here = Engine.at(server, Locks.STONE);
            }
            case "worm" -> {
                if (!built(server, SandSingers.LAIR)) {
                    return false;
                }
                progress.choices.put(active.key(), outcome(active, "spared"));
                run.here = Engine.at(server, SandSingers.LAIR);
            }
            case "sea" -> {
                progress.choices.put(active.key(), outcome(active, "held"));
                Clock.jump(level, Steps.DAWN, clock.road());
            }
            case "visit" -> {
                answer(active).ifPresent(choice -> Engine.choose(server, active.quest().id(), step.id(), choice));
                run.here = Engine.at(server, Caravan.NAME);
            }
            case "caravan" -> {
                if (!Caravan.campedToday(server)) {
                    Caravan.headIn(server);
                    run.building = Caravan.NAME;
                    return false;
                }
                Clock.jump(level, clock.evening(), null);
                run.here = Engine.at(server, Caravan.NAME);
            }
            default -> {}
        }
        hear(server, active);
        boolean ended = Engine.advance(server, active).isEmpty();
        if (ended || step.spec().has("finds")) {
            Engine.open(server);
        }
        if (ended && level.getDayTime() % Clock.DAY > Clock.DAY / 4 && Engine.main(server).map(next -> !next.step().kind().equals("visit")).orElse(true)) {
            Clock.jump(level, Steps.DAWN, clock.road());
        }
        Talk.reset();
        return true;
    }

    private static Optional<Story.Choice> answer(Engine.Active active) {
        String answered = run.answers.get(active.key());
        List<Story.Choice> offered = active.step().choices();
        return offered.stream().filter(choice -> choice.id().equals(answered)).findFirst().or(() -> offered.stream().findFirst());
    }

    private static String outcome(Engine.Active active, String otherwise) {
        return run.answers.getOrDefault(active.key(), otherwise);
    }

    private static void learn(MinecraftServer server, Engine.Active active) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            asked(server, player, active.step()).forEach(control -> Firsts.first(player, Tasks.FIRST + control));
        }
    }

    private static Set<String> asked(MinecraftServer server, ServerPlayer player, Story.Step step) {
        Set<String> asked = new TreeSet<>(Set.of(JOURNAL));
        Map<String, Component> gear = new LinkedHashMap<>();
        Gear.tasks(player, gear);
        if (gear.containsKey(GEAR)) {
            asked.add(GEAR);
        }
        if (Ember.carried(server)) {
            asked.add(CHARMS);
        }
        if ((step.kind().equals("talk") || step.kind().equals("visit")) && !step.choices().isEmpty()) {
            asked.add(ANSWER);
        }
        if (Engine.marked(player).isPresent()) {
            asked.add(COMPASS);
        }
        for (String control : List.of(LOOK, MAP)) {
            if (step.spec().has("tasks") && step.spec().getAsJsonArray("tasks").contains(new JsonPrimitive(control))) {
                asked.add(control);
            }
        }
        return asked;
    }

    private static void hear(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        Progress progress = Progress.of(server);
        Talk.Placeholders fill = Engine.fill(progress);
        String quest = active.quest().id();
        List<Progress.Heard> heard = List.copyOf(progress.conversations.getOrDefault(quest, List.of()));
        step.lines().stream().map(line -> new Progress.Heard(label(server, line.by()), fill.fill(line.text()))).filter(line -> !heard.contains(line))
                .forEach(line -> progress.hear(quest, line.by(), line.text()));
        String asker = step.kind().equals("talk") || step.kind().equals("visit") ? step.lines().stream().map(Story.Line::by).filter(Objects::nonNull).reduce((first, second) -> second)
                .orElse(null) : null;
        progress.choice(quest, step.id()).flatMap(chosen -> step.choices().stream().filter(choice -> choice.id().equals(chosen)).findFirst())
                .ifPresent(choice -> {
                    progress.hear(quest, server.getPlayerList().getPlayers().getFirst().getDisplayName().getString(), choice.text());
                    progress.hear(quest, label(server, asker), choice.reply());
                });
        step.done().forEach(line -> progress.hear(quest, label(server, line.by()), fill.fill(line.text())));
    }

    private static String label(MinecraftServer server, @Nullable String by) {
        return by == null ? "" : People.label(server, by);
    }

    private static void arrive(MinecraftServer server, Engine.Active active) {
        stand(server, active);
        Talk.reset();
        follow(server, active);
        if (run.save != null) {
            save(server, run.save);
        }
        Engine.active(server).stream().filter(open -> !Steps.entered(open) && Engine.pursued(server, open.quest())).forEach(open -> Steps.enter(server, open));
        say("story: at " + active.key() + ", after " + steps(run.played));
        Runnable then = run.then;
        finish(server);
        if (then != null) {
            then.run();
        }
    }

    private static void keep(MinecraftServer server, Engine.Active active) {
        Path folder = run.save.getParent().getParent().resolve(active.key());
        if (Files.exists(folder.resolve("level.dat"))) {
            return;
        }
        stand(server, active);
        follow(server, active);
        save(server, folder);
        server.getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.SPECTATOR));
        say("story: kept " + active.key());
    }

    private static void follow(MinecraftServer server, Engine.Active active) {
        server.getPlayerList().getPlayers().forEach(player -> Engine.track(player, active.quest().id()));
    }

    private static void stand(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        if (!active.key().equals(run.moved)) {
            Steps.moves(server, step);
            run.moved = active.key();
        }
        stand(server, switch (step.kind()) {
            case "talk" -> body(server, step.with()).orElse(run.here);
            case "bury" -> body(server, step.text("whom")).orElse(run.here);
            default -> run.here;
        });
    }

    private static void stand(MinecraftServer server, BlockPos subject) {
        ServerLevel level = Engine.overworld(server);
        BlockPos feet = ground(level, subject, STAND_NEAR, STAND_FAR);
        float yaw = (float) Math.toDegrees(Math.atan2(subject.getZ() - feet.getZ(), subject.getX() - feet.getX())) - 90;
        Progress progress = Progress.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
            List.copyOf(progress.rewards).forEach(reward -> Engine.claim(player, reward));
        }
        Joining.leave(server, new Joining.Place(Level.OVERWORLD, feet));
        restore(server);
    }

    private static void restore(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setGameMode(run.modes.getOrDefault(player.getUUID(), GameType.SURVIVAL));
        }
    }

    private static void finish(MinecraftServer server) {
        restore(server);
        if (run.ticket != null) {
            Engine.overworld(server).getChunkSource().removeRegionTicket(TICKET, run.ticket, LOAD_RADIUS, run.ticket);
        }
        run = null;
    }

    private static void fail(MinecraftServer server, String why) {
        run.source.sendFailure(Component.literal("story failed: " + why));
        finish(server);
    }

    private static String steps(int count) {
        return count + (count == 1 ? " step" : " steps");
    }

    private static void say(String line) {
        run.source.sendSuccess(() -> Component.literal(line), false);
    }

    private static boolean built(MinecraftServer server, String place) {
        if (Places.ready(server, place)) {
            return true;
        }
        Places.build(server, place);
        run.building = place;
        return false;
    }

    private static void hurry(MinecraftServer server, String place) {
        switch (place) {
            case Tollbridge.NAME -> Tollbridge.tick(server);
            case Caravan.NAME -> Caravan.tick(server);
            case Diggers.NAME, Diggers.HALL -> Diggers.tick(server);
            case FirstCamp.NAME -> FirstCamp.tick(server);
            case FirstVillage.NAME, FirstVillage.STONE -> FirstVillage.tick(server);
            case Ford.NAME -> Ford.tick(server);
            case Marsh.NAME, Marsh.HALL, Marsh.CELLAR -> Marsh.raise(server);
            case Frost.NAME, Frost.TRACKS, Frost.HALL, Frost.STONE, Frost.LAKE, Frost.LAKE_STONE -> Frost.tick(server);
            case Mine.NAME, Mine.DEEP, Mine.CART -> Mine.tick(server);
            case ReadingHouse.NAME -> ReadingHouse.tick(server);
            case Shipyard.NAME, Shipyard.WRECK, Shipyard.LAUNCH -> Shipyard.tick(server);
            case TideFolk.NAME, TideFolk.HALL -> TideFolk.tick(server);
            case DryCity.NAME, DryCity.STONE -> DryCity.tick(server);
            case WendCamp.NAME, WendCamp.CHAIR -> WendCamp.tick(server);
            case SandSingers.NAME, SandSingers.LAIR -> SandSingers.tick(server);
            case Locks.NAME, Locks.STONE -> Locks.tick(server);
            case Errands.MISSING, Errands.SETTLED, Errands.BURNING, Errands.OUTPOST, Errands.RITE, Errands.BRAM, Errands.CAIRNS, Errands.SEAL, Errands.DEN,
                    Errands.BUNDLE, Errands.LAST_BUNDLE, Errands.NEVER_SLEEPS, Errands.NEST, Errands.FIRST_GATE -> Errands.tick(server);
            case Act3.HOLLOW, Act3.AIRFIELD, Act3.KEEPERS -> Act3.tick(server);
            case Act3Halls.WARD, Act3Halls.WARD_STONE, Act3Halls.WARD_CHAMPION, Act3Halls.WARD_LINES, Act3Halls.MANSION_HALL, Act3Halls.MANSION, Act3Halls.MANSION_STAND, Act3Halls.SCRIBES,
                    Act3Halls.SCRIBES_STONE, Act3Halls.GRAVEYARD, Act3Halls.GRAVEYARD_STONE -> Act3Halls.tick(server);
            case Voyage.FAR_SHORE -> Voyage.tick(server);
            default -> throw new IllegalStateException("the story has no way to build " + place);
        }
    }

    private static boolean loaded(MinecraftServer server, BlockPos at) {
        ServerLevel level = Engine.overworld(server);
        ChunkPos chunk = new ChunkPos(at);
        if (!chunk.equals(run.ticket)) {
            if (run.ticket != null) {
                level.getChunkSource().removeRegionTicket(TICKET, run.ticket, LOAD_RADIUS, run.ticket);
            }
            level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
            run.ticket = chunk;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(level, at.getX() + 0.5, at.getY() + HEIGHT, at.getZ() + 0.5, player.getYRot(), 0);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(chunk.x + dx, chunk.z + dz) || !level.areEntitiesLoaded(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<BlockPos> body(MinecraftServer server, @Nullable String person) {
        return person == null ? Optional.empty() : People.find(server, person).map(Entity::blockPosition);
    }

    private static BlockPos road(ServerLevel level, BlockPos from) {
        Unseen.Heights heights = new Unseen.Heights(level);
        int away = Balance.get().prologue().awayBlocks() + ROAD_BEYOND;
        int beyond = away + Balance.get().tollbridge().aheadBlocks() + ROAD_BEYOND;
        BlockPos best = null;
        int driest = -1;
        for (int heading : HEADINGS) {
            double way = Math.toRadians(heading);
            if (heights.wet(from.getX() + (int) Math.round(Math.cos(way) * away), from.getZ() + (int) Math.round(Math.sin(way) * away))) {
                continue;
            }
            int dry = 0;
            for (int distance = away; distance <= beyond; distance += ROAD_SAMPLE) {
                if (!heights.wet(from.getX() + (int) Math.round(Math.cos(way) * distance), from.getZ() + (int) Math.round(Math.sin(way) * distance))) {
                    dry++;
                }
            }
            if (dry > driest) {
                driest = dry;
                int x = from.getX() + (int) Math.round(Math.cos(way) * away);
                int z = from.getZ() + (int) Math.round(Math.sin(way) * away);
                best = new BlockPos(x, heights.surface(x, z) + 1, z);
            }
        }
        if (best == null) {
            throw new IllegalStateException("every road " + away + " blocks from " + from.toShortString() + " ends in water; pick another seed in story/states.toml");
        }
        return best;
    }

    private static BlockPos ground(ServerLevel level, BlockPos around, int nearest, int farthest) {
        for (boolean sighted : new boolean[] {true, false}) {
            for (int ring = nearest; ring <= farthest; ring++) {
                for (int step = 0; step < (ring == 0 ? 1 : RING_STEPS * ring); step++) {
                    double angle = Math.PI * 2 * step / (RING_STEPS * Math.max(ring, 1));
                    int x = around.getX() + (int) Math.round(Math.cos(angle) * ring);
                    int z = around.getZ() + (int) Math.round(Math.sin(angle) * ring);
                    for (int dy = 0; dy <= HEIGHT * 2; dy++) {
                        BlockPos feet = new BlockPos(x, around.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2), z);
                        if (standing(level, feet) && (!sighted || sees(level, feet, around))) {
                            return feet;
                        }
                    }
                }
            }
        }
        return around;
    }

    private static boolean sees(ServerLevel level, BlockPos feet, BlockPos subject) {
        Vec3 eyes = Vec3.atBottomCenterOf(feet).add(0, EYES, 0);
        Vec3 face = Vec3.atBottomCenterOf(subject).add(0, EYES, 0);
        return level.clip(new ClipContext(eyes, face, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private static boolean stripped(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_");
    }

    private static boolean standing(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockState under = level.getBlockState(floor);
        return under.isFaceSturdy(level, floor, Direction.UP) && !(under.is(BlockTags.LOGS) && !stripped(under)) && !under.is(BlockTags.LEAVES)
                && !under.is(Blocks.CAMPFIRE) && level.getFluidState(floor).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    private static void save(MinecraftServer server, Path folder) {
        server.saveEverything(true, true, true);
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path partial = folder.resolveSibling(folder.getFileName() + ".partial");
        try {
            delete(partial);
            delete(folder);
            try (Stream<Path> files = Files.walk(world)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().equals("session.lock")) {
                        continue;
                    }
                    Path copy = partial.resolve(world.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.move(partial, folder, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot keep the world in " + folder + ": " + exception.getMessage(), exception);
        }
    }

    private static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
