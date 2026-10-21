package io.github.roman16.wayfarer;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import io.github.roman16.wayfarer.client.Conversation;
import io.github.roman16.wayfarer.mixin.dev.GuiAccessor;
import io.github.roman16.wayfarer.story.Banners;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

final class Legs {
    private static final double FIGHT_BLOCKS = 16;
    private static final long FIGHT_WAIT_TICKS = 600;
    private static final double LOST_BLOCKS = 24;
    private static final double FOLLOW_BLOCKS = 4;
    private static final double FIND_BLOCKS = 64;
    private static final double CAMP_BLOCKS = 16;
    private static final String TENTS = "nomadictents";
    private static final int SEGMENT_BLOCKS = 48;
    private static final int SEGMENT_NEAR = 6;
    private static final int LOW_FOOD = 6;
    private static final float LOW_HEALTH_SHARE = 1 / 3f;
    private static final float WELL_SHARE = 2 / 3f;
    private static final int FED = 12;
    private static final int LOW_AIR = 2;
    private static final float HURT = 3;
    private static final long QUIET_TICKS = 160;
    private static final long LISTEN_QUIET_TICKS = 320;
    private static final int UNDERGROUND_BLOCKS = 4;
    private static final int DROP_BLOCKS = 6;
    private static final int HOLLOW_MOST = 4096;
    private static final long STUCK_TICKS = 800;
    private static final int REPATH_TICKS = 10;
    private static final double CLOSER = 0.5;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SAFE_FALL = 3;
    private static final int AIR_STEPS = 10;
    private static final int CHATTER = 16;
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String UNTIL = "until";
    private static final List<String> PARTS = List.of("dawn", "morning", "noon", "afternoon", "dusk", "night");
    private static final long STILL_CLOCK_TICKS = 600;
    private static final double ROW_NEAR = 3;
    private static final float ROW_AIM = 8;
    private static final float ROW_AHEAD = 45;
    private static final int ASHORE_TICKS = 20;
    private static final int BOAT_STILL_TICKS = 20;
    private static final double BOAT_STILL_SPEED = 0.02;
    private static final int DETOUR_TICKS = 40;
    private static final int CHART_BLOCKS = 64;
    private static final int CHART_STEP = 4;
    private static final double CHART_NEAR = 2;
    private static final double CHART_GAIN = 4;
    private static final int CHART_PATIENCE = 4;
    private static final int DETOUR_REACH = 6;
    private static final double HALF_BLOCK = 0.5;
    private static final double LEAP_NEAR = 2;
    private static final double CENTRE_SLACK = 0.125;
    private static final int CENTRE_TICKS = 32;
    private static final int CLIMB_BLOCKS = 16;
    private static final float LOOK_UP = -90;
    private static final int TREE_BLOCKS = 32;
    private static final int TREE_REACH = 2;
    private static final Map<Item, List<Block>> MINED_FROM = Map.of(Items.COAL, List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), Items.COBBLED_DEEPSLATE,
            List.of(Blocks.DEEPSLATE), Items.COBBLESTONE, List.of(Blocks.STONE), Items.DIAMOND, List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), Items.RAW_COPPER,
            List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE), Items.RAW_GOLD, List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE), Items.RAW_IRON,
            List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE));
    private static final int CHATTER_CHARS = 256;
    private static final int WADES = 4;
    private static final double STRIKE_BACK_BLOCKS = 4;
    private static final int STRIKE_BACK_TICKS = 64;
    private static final float SEAT_TURN = 96;
    private static final double SHORE_STOP_BLOCKS = 32;
    private static final double EDGE_TICKS = 4;
    private static final long WADE_STEP_TICKS = 64;
    private static final ResourceLocation CHOPPED_LOG = ResourceLocation.fromNamespaceAndPath("treechop", "chopped_log");
    private static final long GATHER_DRY_TICKS = 2560;
    private static final String HERE = "here";
    private static final int PIT_RADIUS = 16;
    private static final int PIT_STEP = 4;
    private static final int PIT_SAMPLES = 16;
    private static final int PIT_BLOCKS = 8;
    private static final double PRESS_MARGIN = 0.1;
    private static final double CLEAR_BLOCKS = 1.5;
    private static final double CLEAR_NEAR = 0.75;
    private static final int CLEAR_TICKS = 100;
    private static final List<Float> DETOURS = List.of(45f, -45f, 90f, -90f, 135f, -135f, 180f);
    private static final int OPEN_WATER_BLOCKS = 32;
    private static final int LAND_TICKS = 40;
    private static final double SHORE_BLOCKS = 8;
    private static final int WATER_STEP = 4;
    private static final long SLOW_CLOCK_TICKS = 400;
    private static final double SLOW_CLOCK = 0.5;
    private static final long LONG_WAIT_MINUTES = 8;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int TICKS_PER_DAY = 24000;
    private static final Map<String, Integer> PART_STARTS = Map.of("dawn", 22500, "morning", 1000, "noon", 5000, "afternoon", 7000, "dusk", 11500,
            "night", 13500);
    private static final Deque<String> chatter = new ArrayDeque<>();
    private static final Set<String> shownTasks = new HashSet<>();
    private static final List<String> told = new ArrayList<>();
    private static boolean hungry = true;
    private static boolean weak;
    @Nullable
    private static Vec3 shore;
    @Nullable
    private static Vec3 cave;
    @Nullable
    private static Vec3 brink;
    @Nullable
    private static Vec3 ledge;
    private static boolean configured;
    @Nullable
    private static Action action;

    enum Kind { EAT, FIGHT, FOLLOW, GATHER, GO, IDLE, LISTEN, ROW }

    private static final class Action {
        final Kind kind;
        String what;
        final Vec3 from;
        final float pitch;
        final long seq;
        final Set<Banners.Payload> banners;
        Tasks.Panel panel;
        final boolean screen;
        @Nullable
        GoalXZ target;
        @Nullable
        Goal arrival;
        @Nullable
        Goal ground;
        @Nullable
        Goal goal;
        @Nullable
        Goal aside;
        boolean surface = true;
        boolean segmentFailed;
        boolean blocked;
        boolean dug;
        boolean digging;
        long landTicks;
        long skyTicks;
        @Nullable
        BlockPos spot;
        int centred;
        final Set<BlockPos> hollow = new HashSet<>();
        boolean swims;
        boolean loose;
        boolean burrows;
        boolean stand;
        boolean leaps;
        boolean edgeless;
        int barTime;
        final int slot;
        @Nullable
        Entity foe;
        @Nullable
        Predicate<Entity> wanted;
        long sought;
        @Nullable
        String until;
        long dayTime;
        long movedAt;
        long ashore;
        @Nullable
        Entity clearOf;
        @Nullable
        Vec3 clearPoint;
        long still;
        long detourTicks;
        float detour;
        List<Vec3> chart = List.of();
        int charted;
        long unchartedUntil;
        List<BlockPos> wade = List.of();
        int waded;
        long wadeSince;
        final Set<BlockPos> wadeEnds = new HashSet<>();
        int struck;
        long struckTick;
        @Nullable
        Entity struckAt;
        List<Block> blocks = List.of();
        @Nullable
        String failed;
        @Nullable
        String handedBack;
        boolean started;
        float health;
        long ticks;
        long lastLine = -QUIET_TICKS;
        long quiet = LISTEN_QUIET_TICKS;
        long heard;
        long closer;
        int gained;
        long gainedAt;
        @Nullable
        BlockState chop;
        double nearest = Double.MAX_VALUE;
        int count;

        Action(Kind kind, String what, LocalPlayer player) {
            this.kind = kind;
            this.what = what;
            this.from = player.position();
            this.pitch = player.getXRot();
            this.seq = Conversation.since(0).stream().mapToLong(Conversation.Logged::seq).max().orElse(0);
            this.heard = seq;
            this.banners = shown();
            this.panel = HudState.panel;
            this.screen = Minecraft.getInstance().screen != null;
            this.health = player.getHealth();
            this.slot = player.getInventory().selected;
        }
    }

    private Legs() {}

    static void begin(Minecraft minecraft, String verb, String argument) {
        LocalPlayer player = minecraft.player;
        configure();
        BaritoneAPI.getSettings().allowParkour.value = true;
        chatter.clear();
        told.clear();
        IBaritone baritone = baritone();
        baritone.getPathingBehavior().cancelEverything();
        String[] words = argument.isBlank() ? new String[0] : argument.strip().split(" ");
        switch (verb) {
            case "go" -> {
                Action go = new Action(Kind.GO, argument, player);
                BaritoneAPI.getSettings().allowBreak.value = false;
                BaritoneAPI.getSettings().allowPlace.value = false;
                if (words.length == 1) {
                    go.loose = true;
                    int blocks = Integer.parseInt(words[0]);
                    float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                    go.target = new GoalXZ(Mth.floor(player.getX() - Mth.sin(yaw) * blocks), Mth.floor(player.getZ() + Mth.cos(yaw) * blocks));
                } else if (words.length == 2) {
                    BlockPos column = Driver.block(minecraft, words[0], "~", words[1]);
                    go.target = new GoalXZ(column.getX(), column.getZ());
                } else if (words.length == 3) {
                    go.spot = Driver.block(minecraft, words[0], words[1], words[2]);
                    go.target = new GoalXZ(go.spot.getX(), go.spot.getZ());
                } else {
                    throw new IllegalArgumentException("go takes <blocks>, <x> <z> or <x> <y> <z>, not " + argument);
                }
                go.swims = shore != null && shore.distanceTo(player.position()) <= SHORE_BLOCKS;
                go.burrows = cave != null && cave.distanceTo(player.position()) <= SHORE_BLOCKS;
                go.leaps = brink != null && brink.distanceTo(player.position()) <= SHORE_BLOCKS;
                go.edgeless = ledge != null && ledge.distanceTo(player.position()) <= SHORE_BLOCKS;
                shore = null;
                cave = null;
                brink = null;
                ledge = null;
                go.arrival = go.spot == null ? around(go.target) : new GoalBlock(go.spot);
                go.surface = go.spot == null;
                go.goal = segment(minecraft, go);
                action = go;
                go.clearOf = pressing(minecraft).orElse(null);
                if (go.clearOf == null) {
                    baritone.getCustomGoalProcess().setGoalAndPath(go.goal);
                } else {
                    go.clearPoint = beside(player, go.clearOf, go.target);
                }
            }
            case "follow" -> {
                Entity target = nearest(minecraft, matching(argument), FIND_BLOCKS)
                        .orElseThrow(() -> new IllegalArgumentException("nobody called or of the kind " + argument + " is within " + (int) FIND_BLOCKS + " blocks"));
                Action follow = new Action(Kind.FOLLOW, Senses.describe(target), player);
                follow.foe = target;
                BaritoneAPI.getSettings().allowBreak.value = false;
                BaritoneAPI.getSettings().allowPlace.value = false;
                action = follow;
                baritone.getFollowProcess().follow(entity -> entity == target);
            }
            case "gather" -> {
                String last = words.length > 1 ? words[words.length - 1] : "";
                if (!last.matches("\\d+")) {
                    throw new IllegalArgumentException("gather takes <block> <count>, not " + argument);
                }
                String name = argument.strip().substring(0, argument.strip().lastIndexOf(' ')).strip();
                Item item = Hands.item(name).orElseThrow(() -> new IllegalArgumentException("no block or item is called " + name));
                List<Block> blocks = new ArrayList<>(Hands.blocks(item));
                blocks.addAll(MINED_FROM.getOrDefault(item, List.of()));
                if (blocks.isEmpty()) {
                    throw new IllegalArgumentException(item.getDescription().getString() + " is no block to gather");
                }
                if (blocks.stream().anyMatch(block -> block.defaultBlockState().is(BlockTags.LOGS))) {
                    BuiltInRegistries.BLOCK.getOptional(CHOPPED_LOG).ifPresent(blocks::add);
                }
                Action gather = new Action(Kind.GATHER, BuiltInRegistries.ITEM.getKey(item).toString(), player);
                gather.count = Integer.parseInt(last);
                gather.blocks = blocks;
                BaritoneAPI.getSettings().allowBreak.value = true;
                BaritoneAPI.getSettings().allowPlace.value = true;
                action = gather;
                baritone.getMineProcess().mine(gather.count, blocks.toArray(Block[]::new));
            }
            case "fight" -> {
                boolean stand = argument.strip().equals(HERE) || argument.strip().endsWith(" " + HERE);
                String kind = stand ? argument.strip().substring(0, argument.strip().length() - HERE.length()).strip() : argument;
                Predicate<Entity> seen = kind.isBlank() ? player::hasLineOfSight
                        : entity -> Senses.visible(minecraft, entity) || minecraft.shouldEntityAppearGlowing(entity);
                Predicate<Entity> wanted = (kind.isBlank() ? (Predicate<Entity>) Senses::hostile : matching(kind))
                        .and(entity -> entity instanceof LivingEntity living && living.isAlive() && seen.test(entity));
                Optional<Entity> target = nearest(minecraft, wanted, kind.isBlank() ? FIGHT_BLOCKS : LOST_BLOCKS);
                if (target.isEmpty() && kind.isBlank()) {
                    throw new IllegalArgumentException("nothing to fight is in sight within " + (int) FIGHT_BLOCKS + " blocks");
                }
                Action fight = new Action(Kind.FIGHT, target.map(Senses::describe).orElse(kind.strip()), player);
                fight.foe = target.orElse(null);
                fight.wanted = kind.isBlank() ? null : wanted;
                fight.stand = stand;
                BaritoneAPI.getSettings().allowBreak.value = false;
                BaritoneAPI.getSettings().allowPlace.value = false;
                action = fight;
            }
            case "idle" -> {
                Action idle = new Action(Kind.IDLE, argument, player);
                if (words.length == 2 && words[0].equals(UNTIL)) {
                    if (!PARTS.contains(words[1])) {
                        throw new IllegalArgumentException("idle until takes one of " + String.join(", ", PARTS) + ", not " + words[1]);
                    }
                    idle.until = words[1];
                    idle.dayTime = minecraft.level.getDayTime();
                } else {
                    idle.count = Integer.parseInt(argument.strip()) * TICKS_PER_SECOND;
                }
                action = idle;
            }
            case "listen" -> {
                Action listen = new Action(Kind.LISTEN, "the talk", player);
                listen.lastLine = 0;
                action = listen;
            }
            case "row" -> {
                if (!(player.getVehicle() instanceof Boat)) {
                    throw new IllegalArgumentException("row needs a boat or raft under the player; embark first");
                }
                if (words.length != 2) {
                    throw new IllegalArgumentException("row takes <x> <z>, not " + argument);
                }
                Action row = new Action(Kind.ROW, argument, player);
                row.target = new GoalXZ(Integer.parseInt(words[0]), Integer.parseInt(words[1]));
                action = row;
            }
            case "eat" -> {
                ItemStack held = player.getMainHandItem();
                if (held.getFoodProperties(player) == null) {
                    throw new IllegalArgumentException("the selected item, " + held.getHoverName().getString() + ", is not food");
                }
                Action eat = new Action(Kind.EAT, held.getHoverName().getString(), player);
                eat.count = held.getCount();
                action = eat;
                if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
                    player.setXRot(LOOK_UP);
                    player.xRotO = LOOK_UP;
                }
                minecraft.options.keyUse.setDown(true);
            }
            default -> throw new IllegalArgumentException("no such step " + verb);
        }
    }

    static void turn(Minecraft minecraft, String argument) {
        String[] words = argument.strip().split(" ");
        LocalPlayer player = minecraft.player;
        player.setYRot(player.getYRot() + Float.parseFloat(words[0]));
        if (words.length > 1) {
            player.setXRot(Mth.clamp(Float.parseFloat(words[1]), -90, 90));
        }
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    @Nullable
    static Kind kind() {
        return action == null ? null : action.kind;
    }

    static String doing() {
        if (action == null) {
            return "standing";
        }
        String kind = action.kind.name().toLowerCase(Locale.ROOT);
        return action.digging ? kind + ", digging and climbing" : kind;
    }

    static boolean engaged() {
        return action != null && action.kind == Kind.FIGHT && action.foe != null;
    }

    @Nullable
    static GoalXZ destination() {
        return action == null || action.kind != Kind.GO ? null : action.target;
    }

    static List<String> chatter() {
        return List.copyOf(chatter);
    }

    static List<String> drainTold() {
        List<String> lines = List.copyOf(told);
        told.clear();
        return lines;
    }

    static void handBack(String why) {
        if (action != null) {
            action.handedBack = why;
        }
    }

    static void resume(Minecraft minecraft) {
        Action current = action;
        if (current == null) {
            return;
        }
        current.closer = current.ticks;
        current.health = minecraft.player.getHealth();
        IBaritone baritone = baritone();
        switch (current.kind) {
            case GO -> {
                if (!current.wade.isEmpty()) {
                    current.wadeSince = current.ticks;
                    return;
                }
                current.goal = segment(minecraft, current);
                baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
            }
            case FOLLOW -> {
                Entity target = current.foe;
                baritone.getFollowProcess().follow(entity -> entity == target);
            }
            case GATHER -> baritone.getMineProcess().mine(current.count, current.blocks.toArray(Block[]::new));
            case EAT -> minecraft.options.keyUse.setDown(true);
            case FIGHT, IDLE, ROW -> {
            }
        }
    }

    record Tick(boolean progressed, @Nullable String done, @Nullable String failed) {}

    static Tick tick(Minecraft minecraft) {
        Action current = action;
        LocalPlayer player = minecraft.player;
        if (current == null) {
            return new Tick(false, "nothing to do", null);
        }
        if (Body.acting()) {
            return new Tick(true, null, null);
        }
        current.ticks++;
        if (current.failed != null) {
            return stop(minecraft, null, current.kind.name().toLowerCase(Locale.ROOT) + " failed: " + current.failed);
        }
        Optional<String> interrupted = interrupted(minecraft, current);
        if (interrupted.isPresent()) {
            return stop(minecraft, interrupted.get(), null);
        }
        IBaritone baritone = baritone();
        return switch (current.kind) {
            case GO -> {
                if (current.clearOf != null) {
                    yield clear(minecraft, current);
                }
                if (!current.wade.isEmpty()) {
                    yield wade(minecraft, current);
                }
                BlockPos at = player.blockPosition();
                boolean there = (current.ground != null ? current.ground : current.arrival).isInGoal(at.getX(), at.getY(), at.getZ())
                        || current.aside != null && current.aside.isInGoal(at.getX(), at.getY(), at.getZ());
                if (there && current.aside == null && crowded(minecraft, player.getBoundingBox())) {
                    current.aside = aside(minecraft, at);
                    current.goal = current.aside;
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                    yield new Tick(true, null, null);
                }
                if (there && current.spot != null && current.aside == null && centring(minecraft, player, current)) {
                    yield new Tick(true, null, null);
                }
                if (there) {
                    yield stop(minecraft, "arrived", null);
                }
                if (!player.isInWater()) {
                    current.landTicks++;
                } else if (current.landTicks >= LAND_TICKS && !current.swims) {
                    current.landTicks = 0;
                    int open = openWater(minecraft, current);
                    if (open >= OPEN_WATER_BLOCKS) {
                        shore = player.position();
                        yield stop(minecraft, "stopped: open water ahead, " + open + " blocks of it toward the goal as far as can be seen; go again to swim", null);
                    }
                } else {
                    current.landTicks = 0;
                }
                int roof = roof(minecraft, player);
                if (roof >= UNDERGROUND_BLOCKS && current.skyTicks >= LAND_TICKS && !current.burrows && !current.digging) {
                    cave = player.position();
                    yield stop(minecraft, "stopped: the way goes underground here, under " + roof + " blocks of rock and earth; go again to take it", null);
                }
                current.skyTicks = roof >= UNDERGROUND_BLOCKS ? 0 : current.skyTicks + 1;
                Optional<String> leap = current.leaps || !player.onGround() ? Optional.empty() : leap(player, baritone);
                if (leap.isPresent()) {
                    brink = player.position();
                    yield stop(minecraft, "stopped: " + leap.get() + "; go again to jump", null);
                }
                Optional<String> edge = current.leaps || current.edgeless || current.digging ? Optional.empty() : edge(minecraft, player);
                if (edge.isPresent()) {
                    baritone.getPathingBehavior().cancelEverything();
                    player.setSprinting(false);
                    player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                    ledge = player.position();
                    yield stop(minecraft, null, "go failed: " + edge.get() + "; the walk took it for ground, and the player stopped on the brink");
                }
                Optional<String> refused = refused(minecraft, current);
                if (refused.isPresent()) {
                    yield stop(minecraft, "stopped: digging here, the bar says \"" + refused.get() + "\"; find another way", null);
                }
                if (current.digging && bridging(minecraft, player, current.hollow)) {
                    yield stop(minecraft, null, "go failed: the way on builds out over a drop at " + at.toShortString() + "; step back and find another way");
                }
                if (current.digging) {
                    hollows(minecraft, at, current.hollow);
                }
                if (current.blocked) {
                    current.blocked = false;
                    if (startWade(minecraft, current)) {
                        yield new Tick(true, null, null);
                    }
                    yield current.dug && !restock(minecraft, player) ? stop(minecraft, null, "go failed: no way there from " + at.toShortString() + climbWith(player)) : dig(minecraft, current);
                }
                if (current.digging && current.goal.isInGoal(at.getX(), at.getY(), at.getZ())) {
                    current.digging = false;
                    BaritoneAPI.getSettings().allowBreak.value = false;
                    BaritoneAPI.getSettings().allowPlace.value = false;
                    told.add("dug and climbed through to " + at.toShortString() + ", walking on");
                }
                if (current.segmentFailed) {
                    current.segmentFailed = false;
                    current.surface = false;
                    current.ground = null;
                    current.goal = current.arrival;
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                } else if (current.goal != current.arrival && current.goal.isInGoal(at.getX(), at.getY(), at.getZ())) {
                    current.goal = segment(minecraft, current);
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                }
                boolean closer = closer(current, current.target.heuristic(at.getX(), at.getY(), at.getZ()));
                if (current.ticks > REPATH_TICKS && !baritone.getCustomGoalProcess().isActive()) {
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                }
                yield stuck(minecraft, current, closer);
            }
            case FOLLOW -> {
                if (current.foe.isRemoved() || current.foe.distanceTo(player) > FIND_BLOCKS) {
                    yield stop(minecraft, null, "follow failed: " + current.what + " is gone");
                }
                if (current.foe.distanceTo(player) <= FOLLOW_BLOCKS) {
                    yield stop(minecraft, "reached " + current.what, null);
                }
                yield stuck(minecraft, current, closer(current, current.foe.distanceTo(player)));
            }
            case GATHER -> {
                int has = held(player, current.what);
                if (has >= current.count) {
                    yield stop(minecraft, "has " + has + " " + current.what, null);
                }
                if (!room(player, current.what)) {
                    yield stop(minecraft, null, "gather failed: no room in the inventory for " + current.what + ", holding " + has + "; clear a slot and gather again");
                }
                if (has > current.gained) {
                    current.gained = has;
                    current.gainedAt = current.ticks;
                }
                if (current.ticks - current.gainedAt > GATHER_DRY_TICKS) {
                    yield stop(minecraft, null, "gather failed: nothing more came in for " + GATHER_DRY_TICKS / TICKS_PER_SECOND + " s, holding " + has);
                }
                if (minecraft.hitResult instanceof BlockHitResult chopping && chopping.getType() == HitResult.Type.BLOCK) {
                    BlockState chopped = minecraft.level.getBlockState(chopping.getBlockPos());
                    if (chopped.is(BlockTags.LOGS) && minecraft.gameMode.isDestroying() && !tree(minecraft, chopping.getBlockPos())) {
                        yield stop(minecraft, null, "gather failed: the " + chopped.getBlock().getName().getString() + " at " + chopping.getBlockPos().toShortString()
                                + " is part of something built, not a tree; gather elsewhere, holding " + has);
                    }
                    if (BuiltInRegistries.BLOCK.getKey(chopped.getBlock()).equals(CHOPPED_LOG) && !chopped.equals(current.chop)) {
                        current.chop = chopped;
                        current.closer = current.ticks;
                    }
                }
                if (current.ticks > REPATH_TICKS && !baritone.getMineProcess().isActive()) {
                    yield stop(minecraft, null, "gather failed: no more " + current.what + " to be found, holding " + has);
                }
                yield stuck(minecraft, current, closer(current, -has));
            }
            case FIGHT -> fight(minecraft, current);
            case LISTEN -> current.ticks - current.lastLine >= current.quiet
                    ? stop(minecraft, "nothing more was said for " + current.quiet / TICKS_PER_SECOND + " s", null) : new Tick(true, null, null);
            case IDLE -> {
                if (current.until != null) {
                    yield until(minecraft, current);
                }
                yield current.ticks >= current.count ? stop(minecraft, "nothing happened for " + current.count / TICKS_PER_SECOND + " s", null)
                        : new Tick(true, null, null);
            }
            case ROW -> row(minecraft, current);
            case EAT -> {
                boolean eaten = player.getMainHandItem().getCount() < current.count || !player.getMainHandItem().getHoverName().getString().equals(current.what);
                minecraft.options.keyUse.setDown(!eaten);
                current.started |= player.isUsingItem();
                if (eaten || current.started && !player.isUsingItem()) {
                    yield stop(minecraft, "ate " + current.what + ", food " + player.getFoodData().getFoodLevel() + " of 20", null);
                }
                if (!current.started && current.ticks > REPATH_TICKS * 2) {
                    yield stop(minecraft, null, "eat failed: " + current.what + " cannot be eaten now, food " + player.getFoodData().getFoodLevel() + " of 20");
                }
                yield new Tick(true, null, null);
            }
        };
    }

    private static Tick until(Minecraft minecraft, Action current) {
        long dayTime = minecraft.level.getDayTime();
        String time = Senses.time(dayTime);
        if (time.endsWith(", " + current.until)) {
            String waited = current.ticks <= 1 ? "it is " + current.until + " already" : current.until + " came";
            return stop(minecraft, waited + ": " + time + ", after " + Senses.span(current.ticks), null);
        }
        if (dayTime != current.dayTime) {
            current.dayTime = dayTime;
            current.movedAt = current.ticks;
        }
        if (current.ticks - current.movedAt > STILL_CLOCK_TICKS) {
            return stop(minecraft, "stopped: the clock stands still at " + time, null);
        }
        double pace = minecraft.level.getDayTimePerTick() < 0 ? 1 : minecraft.level.getDayTimePerTick();
        long left = Math.floorMod(PART_STARTS.get(current.until) - dayTime, TICKS_PER_DAY);
        long minutes = Math.round(left / Math.max(pace, 1e-3) / TICKS_PER_SECOND / SECONDS_PER_MINUTE);
        if (current.ticks >= SLOW_CLOCK_TICKS && pace < SLOW_CLOCK && minutes > LONG_WAIT_MINUTES) {
            return stop(minecraft, "stopped: the clock runs at about 1/" + Math.round(1 / Math.max(pace, 1e-3)) + " of its pace; at this pace "
                    + current.until + " is about " + minutes + " minutes off, " + time, null);
        }
        return new Tick(true, null, null);
    }

    private static Tick row(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        if (!(player.getVehicle() instanceof Boat boat)) {
            return stop(minecraft, "stopped: out of the boat", null);
        }
        double dx = current.target.getX() + 0.5 - boat.getX();
        double dz = current.target.getZ() + 0.5 - boat.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        if (far <= ROW_NEAR) {
            return stop(minecraft, "arrived", null);
        }
        current.ashore = boat.isInWater() || !boat.onGround() ? 0 : current.ashore + 1;
        if (current.ashore > ASHORE_TICKS) {
            return stop(minecraft, "stopped: reached land", null);
        }
        float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        current.still = boat.getDeltaMovement().horizontalDistance() < BOAT_STILL_SPEED ? current.still + 1 : 0;
        if (current.ticks - current.closer > BOAT_STILL_TICKS * CHART_PATIENCE && far <= SHORE_STOP_BLOCKS && Driver.ashore(minecraft, boat.blockPosition())) {
            return stop(minecraft, "at the shore, " + Math.round(far) + " blocks short of the goal", null);
        }
        if (current.charted < current.chart.size()) {
            Vec3 mark = current.chart.get(current.charted);
            if (Math.hypot(mark.x - boat.getX(), mark.z - boat.getZ()) < CHART_NEAR) {
                current.charted++;
                current.closer = current.ticks;
            }
            if (current.still > BOAT_STILL_TICKS * CHART_PATIENCE) {
                current.chart = List.of();
                current.still = 0;
                current.unchartedUntil = current.ticks + DETOUR_TICKS * 2;
            }
        } else if (current.still > BOAT_STILL_TICKS && current.detourTicks <= 0 && current.ticks >= current.unchartedUntil) {
            current.chart = chart(minecraft, boat, current.target);
            current.charted = 0;
            if (!current.chart.isEmpty()) {
                current.still = 0;
                current.closer = current.ticks;
                told.add("charted a way through open water round what blocks the boat, " + current.chart.size() * CHART_STEP + " blocks");
            }
        }
        if (current.charted < current.chart.size()) {
            Vec3 next = current.chart.get(current.charted);
            bearing = (float) (Mth.atan2(next.z - boat.getZ(), next.x - boat.getX()) * Mth.RAD_TO_DEG) - 90;
        } else if (current.detourTicks > 0) {
            current.detourTicks--;
            bearing = current.detour;
        } else if (current.still > BOAT_STILL_TICKS) {
            current.still = 0;
            float towards = bearing;
            Optional<Float> open = DETOURS.stream().map(offset -> Mth.wrapDegrees(towards + offset)).filter(way -> open(minecraft, boat, way)).findFirst();
            if (open.isPresent()) {
                current.detour = open.get();
                current.detourTicks = DETOUR_TICKS;
                told.add("rowed round a rock, heading " + Math.round(Mth.wrapDegrees(current.detour - towards)) + " degrees off the way");
                bearing = current.detour;
            }
        }
        Entity foe = Senses.lastAttacker();
        if (foe != null && foe.isAlive() && !foe.isRemoved() && foe.distanceTo(player) <= STRIKE_BACK_BLOCKS && Senses.hurtLately(minecraft, STRIKE_BACK_TICKS)
                && (current.still > BOAT_STILL_TICKS || current.struckAt == foe && current.ticks - current.struckTick < BOAT_STILL_TICKS)
                && Math.abs(Mth.wrapDegrees((float) (Mth.atan2(foe.getZ() - boat.getZ(), foe.getX() - boat.getX()) * Mth.RAD_TO_DEG) - 90 - boat.getYRot())) <= SEAT_TURN) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            if (Body.strike(minecraft, foe, current.ticks, true)) {
                current.struck = foe == current.struckAt ? current.struck + 1 : 1;
                current.struckAt = foe;
                current.struckTick = current.ticks;
                String line = "let go of the oars and struck back at " + Senses.describe(foe);
                if (!told.isEmpty() && told.getLast().startsWith(line)) {
                    told.removeLast();
                }
                told.add(current.struck > 1 ? line + ", " + current.struck + " times" : line);
            }
            current.closer = current.ticks;
            return new Tick(true, null, null);
        }
        float turn = Mth.wrapDegrees(bearing - boat.getYRot());
        minecraft.options.keyRight.setDown(turn > ROW_AIM);
        minecraft.options.keyLeft.setDown(turn < -ROW_AIM);
        minecraft.options.keyUp.setDown(Math.abs(turn) < ROW_AHEAD);
        player.setYRot(bearing);
        return stuck(minecraft, current, closer(current, far));
    }

    private static Optional<Entity> pressing(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        AABB box = player.getBoundingBox().inflate(PRESS_MARGIN);
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity != player && !(entity instanceof LivingEntity) && entity.canBeCollidedWith() && entity.getBoundingBox().intersects(box))
                .findFirst();
    }

    private static Vec3 beside(LocalPlayer player, Entity vehicle, GoalXZ goal) {
        Vec3 centre = vehicle.getBoundingBox().getCenter();
        Vec3 way = new Vec3(goal.getX() + HALF_BLOCK - centre.x, 0, goal.getZ() + HALF_BLOCK - centre.z);
        way = way.lengthSqr() < 1 ? new Vec3(1, 0, 0) : way.normalize();
        Vec3 side = new Vec3(-way.z, 0, way.x);
        if (player.position().subtract(centre).dot(side) < 0) {
            side = side.scale(-1);
        }
        double reach = Math.max(vehicle.getBbWidth(), vehicle.getBoundingBox().getZsize()) / 2 + CLEAR_BLOCKS;
        return new Vec3(centre.x + side.x * reach, player.getY(), centre.z + side.z * reach);
    }

    private static Optional<String> refused(Minecraft minecraft, Action current) {
        GuiAccessor gui = (GuiAccessor) minecraft.gui;
        int shown = gui.wayfarer$bar() == null ? 0 : gui.wayfarer$barTime();
        boolean fresh = shown > current.barTime;
        current.barTime = shown;
        return current.digging && fresh && current.ticks > 1 ? Optional.of(gui.wayfarer$bar().getString()) : Optional.empty();
    }

    private static boolean tree(Minecraft minecraft, BlockPos log) {
        BlockPos.MutableBlockPos top = log.mutable();
        while (minecraft.level.getBlockState(top.above()).is(BlockTags.LOGS) && top.getY() < log.getY() + TREE_BLOCKS) {
            top.move(Direction.UP);
        }
        return BlockPos.betweenClosedStream(top.offset(-TREE_REACH, -TREE_REACH, -TREE_REACH), top.offset(TREE_REACH, TREE_REACH, TREE_REACH))
                .map(minecraft.level::getBlockState)
                .anyMatch(state -> state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT));
    }

    private static boolean room(LocalPlayer player, String what) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(what));
        Inventory inventory = player.getInventory();
        if (inventory.getFreeSlot() >= 0) {
            return true;
        }
        for (ItemStack stack : inventory.items) {
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static boolean centring(Minecraft minecraft, LocalPlayer player, Action current) {
        double dx = current.spot.getX() + HALF_BLOCK - player.getX();
        double dz = current.spot.getZ() + HALF_BLOCK - player.getZ();
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= CENTRE_SLACK || current.centred >= CENTRE_TICKS || !player.onGround()) {
            if (current.centred > 0) {
                minecraft.options.keyUp.setDown(false);
                minecraft.options.keyShift.setDown(false);
            }
            return false;
        }
        if (current.centred == 0) {
            baritone().getPathingBehavior().cancelEverything();
        }
        current.centred++;
        player.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90);
        minecraft.options.keyShift.setDown(true);
        minecraft.options.keyUp.setDown(true);
        return true;
    }

    private static Tick clear(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        Entity vehicle = current.clearOf;
        Vec3 point = current.clearPoint;
        double far = Math.hypot(point.x - player.getX(), point.z - player.getZ());
        boolean free = !player.getBoundingBox().inflate(PRESS_MARGIN).intersects(vehicle.getBoundingBox());
        if (free && far <= CLEAR_NEAR || current.ticks > CLEAR_TICKS || vehicle.isRemoved()) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyJump.setDown(false);
            told.add("stepped out of the " + vehicle.getName().getString() + "'s way to " + BlockPos.containing(player.position()).toShortString());
            current.clearOf = null;
            current.goal = segment(minecraft, current);
            baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
            return new Tick(true, null, null);
        }
        player.setYRot((float) (Mth.atan2(point.z - player.getZ(), point.x - player.getX()) * Mth.RAD_TO_DEG) - 90);
        minecraft.options.keyUp.setDown(true);
        minecraft.options.keyJump.setDown(!free);
        return new Tick(true, null, null);
    }

    private static List<Vec3> chart(Minecraft minecraft, Boat boat, GoalXZ target) {
        int surface = Mth.floor(boat.getY());
        int startX = Mth.floor(boat.getX() - HALF_BLOCK);
        int startZ = Mth.floor(boat.getZ() - HALF_BLOCK);
        int side = CHART_BLOCKS * 2 + 1;
        int[] parent = new int[side * side];
        Arrays.fill(parent, -1);
        int start = CHART_BLOCKS * side + CHART_BLOCKS;
        parent[start] = start;
        ArrayDeque<Integer> frontier = new ArrayDeque<>(List.of(start));
        double from = Math.hypot(target.getX() - startX, target.getZ() - startZ);
        int best = start;
        double bestFar = from;
        while (!frontier.isEmpty()) {
            int cell = frontier.poll();
            int cx = cell % side;
            int cz = cell / side;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= side || nz >= side || parent[nz * side + nx] >= 0) {
                        continue;
                    }
                    int x = startX + nx - CHART_BLOCKS;
                    int z = startZ + nz - CHART_BLOCKS;
                    boolean squeezed = dx != 0 && dz != 0 && (!afloat(minecraft, x - dx, surface, z) || !afloat(minecraft, x, surface, z - dz));
                    if (squeezed || !afloat(minecraft, x, surface, z)) {
                        continue;
                    }
                    parent[nz * side + nx] = cell;
                    frontier.add(nz * side + nx);
                    double far = Math.hypot(target.getX() - x, target.getZ() - z);
                    if (far < bestFar) {
                        bestFar = far;
                        best = nz * side + nx;
                    }
                }
            }
        }
        if (bestFar > from - CHART_GAIN) {
            return List.of();
        }
        List<Vec3> way = new ArrayList<>();
        for (int cell = best; cell != start; cell = parent[cell]) {
            way.add(new Vec3(startX + cell % side - CHART_BLOCKS + 1, surface, startZ + cell / side - CHART_BLOCKS + 1));
        }
        Collections.reverse(way);
        List<Vec3> marks = new ArrayList<>();
        for (int index = CHART_STEP - 1; index < way.size(); index += CHART_STEP) {
            marks.add(way.get(index));
        }
        marks.add(way.getLast());
        return marks;
    }

    private static boolean afloat(Minecraft minecraft, int x, int y, int z) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos water = new BlockPos(x + dx, y, z + dz);
                if (!minecraft.level.getChunkSource().hasChunk(water.getX() >> 4, water.getZ() >> 4) || !minecraft.level.getFluidState(water).is(FluidTags.WATER)
                        || !minecraft.level.getBlockState(water).getCollisionShape(minecraft.level, water).isEmpty()
                        || !minecraft.level.getBlockState(water.above()).getCollisionShape(minecraft.level, water.above()).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean open(Minecraft minecraft, Boat boat, float yaw) {
        double x = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double z = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (int along = 1; along <= DETOUR_REACH; along++) {
            BlockPos water = BlockPos.containing(boat.getX() + x * along, boat.getY() - HALF_BLOCK, boat.getZ() + z * along);
            if (!minecraft.level.getFluidState(water).is(FluidTags.WATER) || !minecraft.level.getBlockState(water.above()).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static int openWater(Minecraft minecraft, Action go) {
        LocalPlayer player = minecraft.player;
        double dx = go.target.getX() + 0.5 - player.getX();
        double dz = go.target.getZ() + 0.5 - player.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        int surface = player.getBlockY();
        int open = 0;
        for (int along = WATER_STEP; along <= Math.min(far, FIND_BLOCKS); along += WATER_STEP) {
            BlockPos column = BlockPos.containing(player.getX() + dx / far * along, surface, player.getZ() + dz / far * along);
            if (!minecraft.level.getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)
                    || !minecraft.level.getFluidState(column).is(FluidTags.WATER) || !minecraft.level.getFluidState(column.below()).is(FluidTags.WATER)) {
                break;
            }
            open = along;
        }
        return open;
    }

    private static Goal around(GoalXZ target) {
        return new GoalComposite(Stream.concat(Stream.of(target), Direction.Plane.HORIZONTAL.stream()
                .map(side -> new GoalXZ(target.getX() + side.getStepX(), target.getZ() + side.getStepZ()))).toArray(Goal[]::new));
    }

    static void walkTo(BlockPos pos, int range) {
        configure();
        BaritoneAPI.getSettings().allowBreak.value = false;
        BaritoneAPI.getSettings().allowPlace.value = false;
        baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, range));
    }

    static void halt() {
        baritone().getPathingBehavior().cancelEverything();
    }

    private static Tick fight(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        Entity target = current.foe;
        if (target != null && Senses.gone(target) && current.wanted != null) {
            told.add(current.what + " went out of sight, still alive; waiting for it to come back");
            current.foe = null;
            current.sought = current.ticks;
            return new Tick(true, null, null);
        }
        if (target == null) {
            Optional<Entity> found = nearest(minecraft, current.wanted, LOST_BLOCKS);
            if (found.isPresent()) {
                current.foe = found.get();
                current.what = Senses.describe(found.get());
                return new Tick(true, null, null);
            }
            return current.ticks - current.sought > FIGHT_WAIT_TICKS
                    ? stop(minecraft, null, "fight failed: no " + current.what + " came in sight within " + FIGHT_WAIT_TICKS / TICKS_PER_SECOND + " s")
                    : new Tick(true, null, null);
        }
        if (Senses.gone(target)) {
            return stop(minecraft, current.what + " went out of sight, still alive", null);
        }
        if (!target.isAlive() || target.isRemoved()) {
            return stop(minecraft, current.what + " is dead", null);
        }
        double distance = target.distanceTo(player);
        if (distance > LOST_BLOCKS) {
            return stop(minecraft, current.what + " is out of reach, " + (int) distance + " blocks away", null);
        }
        if (Body.deep(target) && !Body.deep(player) && !(player.getVehicle() instanceof Boat && distance <= STRIKE_BACK_BLOCKS)) {
            return stop(minecraft, "stopped: " + current.what + " is in deep water", null);
        }
        Body.strike(minecraft, target, current.ticks, current.stand || player.isPassenger());
        return new Tick(true, null, null);
    }

    private static Optional<String> interrupted(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        if (player == null || player.isDeadOrDying()) {
            return Optional.of("stopped: you died");
        }
        if (current.handedBack != null) {
            return Optional.of("stopped: " + current.handedBack);
        }
        if (current.kind == Kind.EAT) {
            return opened(minecraft, current) ? Optional.of("stopped: a screen opened, " + minecraft.screen.getTitle().getString()) : Optional.empty();
        }
        for (Conversation.Logged line : Conversation.since(current.heard)) {
            current.heard = line.seq();
            boolean answers = line.text().startsWith("answers: ");
            boolean fresh = current.ticks - current.lastLine >= QUIET_TICKS;
            current.lastLine = current.ticks;
            current.quiet = Math.max(LISTEN_QUIET_TICKS, afterLine(line.text()));
            if (answers) {
                return Optional.of(current.kind == Kind.LISTEN ? "answers stand beside the crosshair" : "stopped: answers stand beside the crosshair");
            }
            if (fresh && current.kind != Kind.LISTEN && current.kind != Kind.FIGHT) {
                return Optional.of("stopped: something was said");
            }
        }
        if (current.kind == Kind.LISTEN) {
            return dangers(minecraft, current, player);
        }
        Set<Banners.Payload> banners = shown();
        banners.removeAll(current.banners);
        if (!banners.isEmpty()) {
            return Optional.of("stopped: a banner came");
        }
        Tasks.Panel panel = HudState.panel;
        if (current.panel == Tasks.Panel.EMPTY) {
            current.panel = panel;
        }
        if (!plain(panel.title()).equals(plain(current.panel.title())) || !plain(panel.objective()).equals(plain(current.panel.objective()))) {
            return Optional.of("stopped: the task panel has a new objective");
        }
        current.panel.open().stream().map(Legs::plain).forEach(shownTasks::add);
        if (panel.open().stream().map(Legs::plain).filter(shownTasks::add).count() > 0) {
            return Optional.of("stopped: the task panel has a new task");
        }
        return dangers(minecraft, current, player);
    }

    private static long afterLine(String text) {
        Balance.Talk talk = Balance.get().talk();
        return (long) (Math.max(talk.leastSeconds(), Math.ceilDiv(text.length(), talk.charactersPerSecond())) + talk.pauseSeconds() + talk.leastSeconds())
                * TICKS_PER_SECOND;
    }

    private static Optional<String> dangers(Minecraft minecraft, Action current, LocalPlayer player) {
        if (opened(minecraft, current)) {
            return Optional.of("stopped: a screen opened, " + minecraft.screen.getTitle().getString());
        }
        if (player.isUnderWater() && player.getAirSupply() * AIR_STEPS / player.getMaxAirSupply() <= LOW_AIR) {
            return Optional.of("stopped: under water, air " + Math.max(0, player.getAirSupply()) * AIR_STEPS / player.getMaxAirSupply() + " of " + AIR_STEPS);
        }
        if (player.getHealth() < current.health) {
            boolean answered = Senses.lastAttacker() != null && (Body.answering() || current.kind == Kind.ROW);
            boolean shrugged = (Senses.lastKind().equals(Senses.FALL) || Senses.lastKind().equals(Senses.BERRY_BUSH)) && current.health - player.getHealth() < HURT
                    || Senses.lastKind().equals("poison") && player.hasEffect(MobEffects.POISON)
                    || Senses.lastKind().equals(Senses.HUNGER) && hungry;
            current.health = player.getHealth();
            if (!answered && !shrugged && current.kind != Kind.FIGHT) {
                return Optional.of("stopped: you were hurt, health " + health(player) + ": " + Senses.lastKind());
            }
        }
        current.health = Math.max(current.health, player.getHealth());
        boolean low = player.getHealth() <= player.getMaxHealth() * LOW_HEALTH_SHARE;
        if (low && !weak) {
            weak = true;
            return Optional.of("stopped: health is low, " + health(player));
        }
        weak &= player.getHealth() <= player.getMaxHealth() * WELL_SHARE;
        boolean empty = player.getFoodData().getFoodLevel() <= LOW_FOOD;
        if (empty && !hungry && !Body.hasFood(player)) {
            hungry = true;
            return Optional.of("stopped: you are hungry, food " + player.getFoodData().getFoodLevel() + " of 20, and nothing to eat in the hotbar");
        }
        hungry &= player.getFoodData().getFoodLevel() <= FED;
        return Optional.empty();
    }

    private static boolean opened(Minecraft minecraft, Action current) {
        return !current.screen && minecraft.screen != null && !(minecraft.screen instanceof InBedChatScreen);
    }

    private static Goal segment(Minecraft minecraft, Action go) {
        LocalPlayer player = minecraft.player;
        double dx = go.target.getX() + 0.5 - player.getX();
        double dz = go.target.getZ() + 0.5 - player.getZ();
        double far = Math.sqrt(dx * dx + dz * dz);
        if (!go.surface || go.spot != null) {
            return go.arrival;
        }
        if (far <= SEGMENT_BLOCKS) {
            Goal ground = surface(minecraft, go);
            go.ground = ground == go.arrival ? null : ground;
            return ground;
        }
        int x = Mth.floor(player.getX() + dx / far * SEGMENT_BLOCKS);
        int z = Mth.floor(player.getZ() + dz / far * SEGMENT_BLOCKS);
        if (!minecraft.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return go.arrival;
        }
        Optional<BlockPos> floor = floor(minecraft, x, z).map(found -> level(minecraft, found));
        return floor.<Goal>map(found -> new GoalNear(found.above(), SEGMENT_NEAR)).orElse(go.arrival);
    }

    private static Optional<String> leap(LocalPlayer player, IBaritone baritone) {
        IPathExecutor executing = baritone.getPathingBehavior().getCurrent();
        if (executing == null) {
            return Optional.empty();
        }
        List<BetterBlockPos> way = executing.getPath().positions();
        for (int step = Math.max(executing.getPosition(), 0); step + 1 < way.size(); step++) {
            BetterBlockPos from = way.get(step);
            if (Vec3.atBottomCenterOf(from).distanceTo(player.position()) > LEAP_NEAR) {
                return Optional.empty();
            }
            BetterBlockPos to = way.get(step + 1);
            if (from.getY() - to.getY() > SAFE_FALL) {
                return Optional.of("the way jumps " + (from.getY() - to.getY()) + " blocks down into water at " + to.getX() + ", " + to.getY() + ", " + to.getZ());
            }
        }
        return Optional.empty();
    }

    private static String stalled(String line) {
        IPathExecutor executing = baritone().getPathingBehavior().getCurrent();
        if (!line.contains("This movement has taken too long") || executing == null) {
            return line;
        }
        List<IMovement> movements = executing.getPath().movements();
        int step = executing.getPosition();
        if (step < 0 || step >= movements.size()) {
            return line;
        }
        IMovement movement = movements.get(step);
        BetterBlockPos to = movement.getDest();
        Minecraft minecraft = Minecraft.getInstance();
        String blocks = Stream.of(to.below(), to, to.above())
                .map(pos -> BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(pos).getBlock()).getPath())
                .collect(Collectors.joining(", "));
        String move = line + " The move: from " + movement.getSrc().toShortString() + " to " + to.toShortString() + ", below, at and above it " + blocks + ".";
        if (Math.max(Math.abs(to.getX() - movement.getSrc().getX()), Math.abs(to.getZ() - movement.getSrc().getZ())) < 2) {
            return move;
        }
        BaritoneAPI.getSettings().allowParkour.value = false;
        return move + " No more jumps across gaps on this walk.";
    }

    private static Optional<String> edge(Minecraft minecraft, LocalPlayer player) {
        if (!player.onGround() || player.isInWater()) {
            return Optional.empty();
        }
        Vec3 ahead = player.position().add(player.getDeltaMovement().multiply(EDGE_TICKS, 0, EDGE_TICKS));
        BlockPos column = BlockPos.containing(ahead.x, player.getY() - HALF_BLOCK, ahead.z);
        if (column.getX() == player.getBlockX() && column.getZ() == player.getBlockZ()) {
            return Optional.empty();
        }
        for (int down = 0; down <= SAFE_FALL + 1; down++) {
            BlockPos below = column.below(down);
            if (!minecraft.level.getBlockState(below).getCollisionShape(minecraft.level, below).isEmpty() || !minecraft.level.getFluidState(below).isEmpty()) {
                return Optional.empty();
            }
        }
        int depth = SAFE_FALL + 1;
        while (column.below(depth).getY() > minecraft.level.getMinBuildHeight()
                && minecraft.level.getBlockState(column.below(depth)).getCollisionShape(minecraft.level, column.below(depth)).isEmpty()
                && minecraft.level.getFluidState(column.below(depth)).isEmpty()) {
            depth++;
        }
        return Optional.of("the way steps off a drop of " + depth + " blocks at " + column.toShortString());
    }

    private static Optional<BlockPos> floor(Minecraft minecraft, int x, int z) {
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos(x, minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        while (below.getY() > minecraft.level.getMinBuildHeight() && canopy(minecraft.level.getBlockState(below))) {
            below.move(Direction.DOWN);
        }
        BlockState floor = minecraft.level.getBlockState(below);
        if (below.getY() <= minecraft.level.getMinBuildHeight() || floor.is(Blocks.LAVA) || floor.is(BlockTags.FIRE)) {
            return Optional.empty();
        }
        return Optional.of(below.immutable());
    }

    private static BlockPos level(Minecraft minecraft, BlockPos floor) {
        List<BlockPos> ring = new ArrayList<>();
        for (int dx = -PIT_RADIUS; dx <= PIT_RADIUS; dx += PIT_STEP) {
            for (int dz = -PIT_RADIUS; dz <= PIT_RADIUS; dz += PIT_STEP) {
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                if ((dx != 0 || dz != 0) && minecraft.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    floor(minecraft, x, z).ifPresent(ring::add);
                }
            }
        }
        if (ring.size() < PIT_SAMPLES) {
            return floor;
        }
        int median = ring.stream().mapToInt(BlockPos::getY).sorted().skip(ring.size() / 2).findFirst().orElse(floor.getY());
        if (floor.getY() >= median - PIT_BLOCKS) {
            return floor;
        }
        return ring.stream().filter(spot -> Math.abs(spot.getY() - median) <= PIT_STEP)
                .min(Comparator.comparingDouble(spot -> spot.distSqr(floor))).orElse(floor);
    }

    private static Goal surface(Minecraft minecraft, Action go) {
        if (go.loose) {
            return floor(minecraft, go.target.getX(), go.target.getZ()).map(found -> level(minecraft, found))
                    .<Goal>map(found -> new GoalNear(found.above(), SEGMENT_NEAR)).orElse(go.arrival);
        }
        if (!minecraft.level.getChunkSource().hasChunk(go.target.getX() >> 4, go.target.getZ() >> 4)) {
            return go.arrival;
        }
        Optional<BlockPos> bottom = floor(minecraft, go.target.getX(), go.target.getZ());
        Optional<BlockPos> land = bottom.map(found -> level(minecraft, found)).filter(found -> !found.equals(bottom.get()));
        if (land.isPresent()) {
            told.add("the column lies at the bottom of a pit, at " + bottom.get().above().toShortString() + "; ending on the land above it instead");
            return new GoalNear(land.get().above(), SEGMENT_NEAR);
        }
        List<Goal> spots = new ArrayList<>();
        for (BlockPos column : Stream.concat(Stream.of(BlockPos.ZERO), Direction.Plane.HORIZONTAL.stream().map(side -> BlockPos.ZERO.relative(side)))
                .map(step -> new BlockPos(go.target.getX() + step.getX(), 0, go.target.getZ() + step.getZ())).toList()) {
            if (!minecraft.level.getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)) {
                return go.arrival;
            }
            BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos(column.getX(),
                    minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) - 1, column.getZ());
            while (below.getY() > minecraft.level.getMinBuildHeight() && canopy(minecraft.level.getBlockState(below))) {
                below.move(Direction.DOWN);
            }
            BlockState floor = minecraft.level.getBlockState(below);
            if (below.getY() > minecraft.level.getMinBuildHeight() && !floor.is(Blocks.LAVA) && !floor.is(BlockTags.FIRE)) {
                spots.add(new GoalBlock(below.above()));
            }
        }
        return spots.isEmpty() ? go.arrival : new GoalComposite(spots.toArray(Goal[]::new));
    }

    private static int roof(Minecraft minecraft, LocalPlayer player) {
        BlockPos.MutableBlockPos at = player.blockPosition().above(2).mutable();
        int top = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        int solid = 0;
        for (; at.getY() < top; at.move(Direction.UP)) {
            BlockState state = minecraft.level.getBlockState(at);
            if (state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.getCollisionShape(minecraft.level, at).isEmpty()) {
                solid++;
            }
        }
        return solid;
    }

    private static void hollows(Minecraft minecraft, BlockPos at, Set<BlockPos> hollow) {
        if (hollow.size() > HOLLOW_MOST) {
            hollow.clear();
        }
        BlockPos.betweenClosedStream(at.offset(-2, -2, -2), at.offset(2, 0, 2))
                .filter(pos -> minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos).isEmpty())
                .forEach(pos -> hollow.add(pos.immutable()));
    }

    private static boolean bridging(Minecraft minecraft, LocalPlayer player, Set<BlockPos> hollow) {
        if (!player.onGround()) {
            return false;
        }
        BlockPos under = player.getOnPos();
        if (!hollow.contains(under) || minecraft.level.getBlockState(under).getCollisionShape(minecraft.level, under).isEmpty()) {
            return false;
        }
        for (int down = 1; under.below(down).getY() >= minecraft.level.getMinBuildHeight(); down++) {
            BlockPos below = under.below(down);
            if (minecraft.level.getFluidState(below).is(FluidTags.WATER)) {
                return false;
            }
            if (!minecraft.level.getBlockState(below).getCollisionShape(minecraft.level, below).isEmpty() || !minecraft.level.getFluidState(below).isEmpty()) {
                return down > DROP_BLOCKS || !minecraft.level.getFluidState(below).isEmpty();
            }
        }
        return true;
    }

    private static boolean buildable(Block block) {
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !state.hasBlockEntity() && !(block instanceof FallingBlock)
                && !state.is(Tags.Blocks.ORES) && !state.is(Tags.Blocks.STORAGE_BLOCKS) && !state.is(BlockTags.LEAVES) && state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0
                && block.defaultDestroyTime() >= 0 && !(block instanceof SlimeBlock) && !state.is(Blocks.TNT);
    }

    private static boolean restock(Minecraft minecraft, LocalPlayer player) {
        List<Item> throwaway = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        Inventory inventory = player.getInventory();
        int empty = -1;
        int carried = 0;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack held = inventory.getItem(slot);
            if (throwaway.contains(held.getItem())) {
                carried += held.getCount();
                if (empty < 0 || !inventory.getItem(empty).isEmpty() && held.getCount() < inventory.getItem(empty).getCount()) {
                    empty = slot;
                }
            } else if (held.isEmpty() && (empty < 0 || !inventory.getItem(empty).isEmpty())) {
                empty = slot;
            }
        }
        if (empty < 0 || carried >= CLIMB_BLOCKS) {
            return false;
        }
        int found = -1;
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (throwaway.contains(stack.getItem()) && stack.getCount() > inventory.getItem(empty).getCount()
                    && (found < 0 || inventory.getItem(found).is(ItemTags.PLANKS) && !stack.is(ItemTags.PLANKS))) {
                found = slot;
            }
        }
        if (found < 0) {
            return false;
        }
        ItemStack stack = inventory.getItem(found);
        told.add("took " + stack.getCount() + " " + stack.getHoverName().getString() + " into the hotbar to climb with");
        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, found, empty, ClickType.SWAP, player);
        return true;
    }

    private static String climbWith(LocalPlayer player) {
        List<Item> throwaway = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        boolean blocks = IntStream.range(0, Inventory.getSelectionSize()).anyMatch(slot -> throwaway.contains(player.getInventory().getItem(slot).getItem()));
        return blocks ? "" : "; nothing in the hotbar to climb with: no stone, earth, planks or other plain building block";
    }

    private static boolean canopy(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM);
    }

    private static boolean closer(Action current, double distance) {
        if (distance < current.nearest - CLOSER) {
            current.nearest = distance;
            current.closer = current.ticks;
            return true;
        }
        return false;
    }

    private static Tick dig(Minecraft minecraft, Action current) {
        BlockPos at = minecraft.player.blockPosition();
        boolean camp = HudState.smoke.filter(fire -> Math.hypot(fire.getX() - at.getX(), fire.getZ() - at.getZ()) <= CAMP_BLOCKS).isPresent();
        if (minecraft.level.dimension().location().getNamespace().equals(TENTS)) {
            return stop(minecraft, null, "go failed: no way on by walking from " + at.toShortString() + ", and the tent is not for digging");
        }
        current.dug = true;
        current.digging = true;
        current.closer = current.ticks;
        BaritoneAPI.getSettings().allowBreak.value = !camp;
        BaritoneAPI.getSettings().allowPlace.value = true;
        if (current.goal == current.arrival) {
            current.surface = true;
            current.goal = segment(minecraft, current);
        }
        baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
        told.add("no way on by walking from " + minecraft.player.blockPosition().toShortString() + (camp ? ": climbing on blocks, breaking nothing at the caravan's camp"
                : ": digging and climbing, as a player with a pickaxe and blocks does"));
        return new Tick(true, null, null);
    }

    private static boolean startWade(Minecraft minecraft, Action current) {
        BlockPos at = minecraft.player.blockPosition();
        if (current.wadeEnds.size() >= WADES || !Wading.boxed(minecraft, at)) {
            return false;
        }
        List<BlockPos> way = Wading.way(minecraft, at, current.goal, current.wadeEnds);
        if (way.isEmpty()) {
            return false;
        }
        baritone().getPathingBehavior().cancelEverything();
        current.wadeEnds.add(way.getLast());
        current.wade = way;
        current.waded = 0;
        current.wadeSince = current.ticks;
        told.add("flowing water all round at " + at.toShortString() + ", where the walk keeps out of it: wading through to " + way.getLast().toShortString() + ", "
                + way.size() + " blocks");
        return true;
    }

    private static Tick wade(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        if (Wading.steer(minecraft, current.wade.get(current.waded))) {
            current.waded++;
            current.wadeSince = current.ticks;
            if (current.waded == current.wade.size()) {
                Wading.release(minecraft);
                told.add("waded through to " + current.wade.getLast().toShortString() + ", walking on");
                current.wade = List.of();
                current.closer = current.ticks;
                baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
            }
            return new Tick(true, null, null);
        }
        if (current.ticks - current.wadeSince > WADE_STEP_TICKS) {
            Wading.release(minecraft);
            BlockPos at = player.blockPosition();
            told.add("wading got no further than " + at.toShortString() + " towards " + current.wade.get(current.waded).toShortString());
            current.wade = List.of();
            return current.dug && !restock(minecraft, player) ? stop(minecraft, null, "go failed: no way there from " + at.toShortString() + climbWith(player)) : dig(minecraft, current);
        }
        return new Tick(true, null, null);
    }

    private static boolean crowded(Minecraft minecraft, AABB room) {
        return !minecraft.level.getEntities(minecraft.player, room, entity -> entity instanceof LivingEntity living && living.isAlive()).isEmpty();
    }

    private static Goal aside(Minecraft minecraft, BlockPos at) {
        List<Goal> free = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = at.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && !crowded(minecraft, new AABB(column).expandTowards(0, 1, 0))) {
                    free.add(new GoalXZ(column.getX(), column.getZ()));
                }
            }
        }
        return free.isEmpty() ? new GoalRunAway(2, at) : new GoalComposite(free.toArray(Goal[]::new));
    }

    private static Tick stuck(Minecraft minecraft, Action current, boolean closer) {
        if (current.ticks - current.closer > STUCK_TICKS) {
            if (current.kind == Kind.GO && !current.dug) {
                return dig(minecraft, current);
            }
            BlockPos at = minecraft.player.blockPosition();
            return stop(minecraft, null, current.kind.name().toLowerCase(Locale.ROOT) + " failed: no closer for " + STUCK_TICKS / 20 + " s, standing at "
                    + at.toShortString());
        }
        return new Tick(closer, null, null);
    }

    private static Tick stop(Minecraft minecraft, @Nullable String done, @Nullable String failed) {
        Action current = action;
        action = null;
        minecraft.options.keyUse.setDown(false);
        if (current != null && (current.kind == Kind.ROW || current.clearOf != null || !current.wade.isEmpty())) {
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
        }
        baritone().getPathingBehavior().cancelEverything();
        LocalPlayer player = minecraft.player;
        if (current != null && player != null && (current.kind == Kind.GATHER || current.kind == Kind.GO || current.kind == Kind.FOLLOW)) {
            player.getInventory().selected = current.slot;
        }
        if (current != null && current.digging) {
            BaritoneAPI.getSettings().allowBreak.value = false;
            BaritoneAPI.getSettings().allowPlace.value = false;
        }
        if (failed != null) {
            return new Tick(false, null, failed);
        }
        if (current != null && player != null && current.kind != Kind.FIGHT) {
            player.setXRot(current.pitch);
            player.xRotO = current.pitch;
        }
        String walked = current == null || player == null ? "" : ", " + (int) current.from.distanceTo(player.position()) + " blocks from where it began, at "
                + player.blockPosition().toShortString() + " facing " + Math.round(Mth.wrapDegrees(player.getYRot()));
        return new Tick(false, done + walked, null);
    }

    static void cancel(Minecraft minecraft) {
        if (action != null) {
            stop(minecraft, "cancelled", null);
        }
    }

    private static Set<Banners.Payload> shown() {
        Set<Banners.Payload> shown = Collections.newSetFromMap(new IdentityHashMap<>());
        shown.addAll(HudState.banners);
        return shown;
    }

    private static String plain(Component text) {
        return DIGITS.matcher(text.getString()).replaceAll("");
    }

    private static String health(LocalPlayer player) {
        return Math.round(player.getHealth()) + " of " + Math.round(player.getMaxHealth());
    }

    private static int held(LocalPlayer player, String block) {
        return player.getInventory().items.stream().filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(block)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(block)).mapToInt(ItemStack::getCount).sum();
    }

    private static Predicate<Entity> matching(String wanted) {
        String name = wanted.strip().toLowerCase(Locale.ROOT);
        return entity -> entity != Minecraft.getInstance().player && (entity.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(name)
                || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(name)
                || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(name));
    }

    private static Optional<Entity> nearest(Minecraft minecraft, Predicate<Entity> wanted, double within) {
        LocalPlayer player = minecraft.player;
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(wanted).filter(entity -> entity.distanceTo(player) <= within)
                .min(Comparator.comparingDouble(player::distanceTo));
    }

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    private static void configure() {
        if (configured) {
            return;
        }
        configured = true;
        Settings settings = BaritoneAPI.getSettings();
        settings.allowBreakAnyway.value = new ArrayList<>(StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.LEAVES).spliterator(), false)
                .map(holder -> holder.value()).toList());
        settings.acceptableThrowawayItems.value = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem placed && buildable(placed.getBlock()))
                .toList();
        settings.allowInventory.value = false;
        settings.allowParkour.value = true;
        settings.allowPlace.value = false;
        settings.allowSprint.value = true;
        settings.antiCheatCompatibility.value = true;
        settings.blockFreeLook.value = false;
        settings.chatControl.value = false;
        settings.chatDebug.value = true;
        settings.desktopNotifications.value = false;
        settings.echoCommands.value = false;
        settings.freeLook.value = false;
        settings.itemSaver.value = true;
        settings.itemSaverThreshold.value = 1;
        settings.maxFallHeightNoWater.value = SAFE_FALL;
        settings.blocksToAvoid.value = List.of(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.FIRE, Blocks.MAGMA_BLOCK);
        settings.logger.value = message -> {
            String line = stalled(message.getString());
            chatter.addLast(line.length() > CHATTER_CHARS ? line.substring(0, CHATTER_CHARS) + " …" : line);
            while (chatter.size() > CHATTER) {
                chatter.removeFirst();
            }
        };
        settings.prefixControl.value = false;
        settings.renderGoal.value = false;
        settings.renderPath.value = false;
        settings.renderSelectionBoxes.value = false;
        settings.toaster.value = (title, message) -> {};
        baritone().getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onPathEvent(PathEvent event) {
                if (event != PathEvent.CALC_FAILED || action == null || action.kind == Kind.FIGHT || Body.acting()) {
                    return;
                }
                if (action.kind == Kind.GO && action.goal != action.arrival && !action.digging) {
                    action.segmentFailed = true;
                } else if (action.kind == Kind.GO) {
                    action.blocked = true;
                } else {
                    action.failed = "no way there from " + Minecraft.getInstance().player.blockPosition().toShortString();
                }
            }
        });
    }
}
