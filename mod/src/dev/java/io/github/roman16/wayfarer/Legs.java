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
import io.github.roman16.wayfarer.story.StoryChecks;
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
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.lwjgl.glfw.GLFW;

final class Legs {
    private static final int CAVERN_CELLS = 32;
    private static final int SHORE_SEARCH = 64;
    private static final int HOTBAR_SLOTS = 9;
    private static final float SOFT_HARDNESS = 1;
    private static final double FIGHT_BLOCKS = 16;
    private static final long FIGHT_WAIT_TICKS = 600;
    private static final double LOST_BLOCKS = 24;
    private static final double FOLLOW_BLOCKS = 4;
    private static final double FIND_BLOCKS = 64;
    private static final double CAMP_BLOCKS = 16;
    private static final String TENTS = "nomadictents";
    private static final int SEGMENT_BLOCKS = 48;
    private static final int SEGMENT_NEAR = 6;
    private static final int LOOSE_SHARE = 4;
    private static final double FIGHT_REACH = 4;
    private static final int STRUCK_TICKS = 5;
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
    private static final int ASK_DROP_BLOCKS = 16;
    private static final double STEP_UP = 0.625;
    private static final long WAY_IN_SECONDS = 1;
    private static final Set<Block> HARMFUL = Set.of(Blocks.CACTUS, Blocks.MAGMA_BLOCK, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH, Blocks.WITHER_ROSE);
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
    private static final double SAIL_SLOW_BLOCKS = 128;
    private static final double SAIL_COAST_BLOCKS = 32;
    private static final int SAIL_PRESS_TICKS = 20;
    private static final String SAIL_KEY = "key.smallships.ship_sail";
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
    private static final int COVER_AHEAD = 4;
    private static final int DETOUR_REACH = 6;
    private static final double HALF_BLOCK = 0.5;
    private static final double LEAP_NEAR = 2;
    private static final double SAME_WAY = 0.875;
    private static final double CENTRE_SLACK = 0.125;
    private static final int CENTRE_TICKS = 32;
    private static final int CLIMB_BLOCKS = 16;
    static final float LOOK_UP = -90;
    private static final int TREE_BLOCKS = 32;
    private static final int TREE_REACH = 2;
    private static final int TREE_DEPTH = 8;
    private static final Map<Item, List<Block>> MINED_FROM = Map.of(Items.COAL, List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), Items.COBBLED_DEEPSLATE,
            List.of(Blocks.DEEPSLATE), Items.COBBLESTONE, List.of(Blocks.STONE), Items.DIAMOND, List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), Items.RAW_COPPER,
            List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE), Items.RAW_GOLD, List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE), Items.RAW_IRON,
            List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE));
    private static final int CHATTER_CHARS = 256;
    private static final int WADES = 4;
    private static final int LADDER_RUNGS = 2;
    private static final int PERSON_HURT_BLOCKS = 32;
    private static final int CHOPS = 16;
    private static final double CHOP_REACH = 4.5;
    private static final long CHOP_TICKS = 100;
    private static final double STRIKE_REACH = 4;
    private static final long LADDER_STILL_TICKS = 64;
    private static final double STRIKE_BACK_BLOCKS = 4;
    private static final int STRIKE_BACK_TICKS = 64;
    private static final float SEAT_TURN = 96;
    private static final double SHORE_STOP_BLOCKS = 32;
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
    @Nullable
    private static String exposure;
    private static boolean weak;
    @Nullable
    private static Vec3 shore;
    @Nullable
    private static Vec3 cave;
    @Nullable
    private static Halt brink;
    private static boolean configured;
    private static int miningFloor;

    private record Covered(Goal columns, int ceiling) implements Goal {
        @Override
        public boolean isInGoal(int x, int y, int z) {
            return y <= ceiling && columns.isInGoal(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            return columns.heuristic(x, y, z);
        }
    }

    private record Halt(Vec3 at, GoalXZ goal) {
        boolean resumes(LocalPlayer player, GoalXZ next) {
            Vec3 before = new Vec3(goal.getX() + 0.5 - at.x, 0, goal.getZ() + 0.5 - at.z);
            Vec3 after = new Vec3(next.getX() + 0.5 - player.getX(), 0, next.getZ() + 0.5 - player.getZ());
            return at.distanceTo(player.position()) <= SHORE_BLOCKS && before.lengthSqr() > 0 && after.lengthSqr() > 0
                    && before.normalize().dot(after.normalize()) >= SAME_WAY;
        }
    }
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
        boolean here;
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
        long backTicks;
        @Nullable
        Vec3 lastBoat;
        float detour;
        List<Vec3> chart = List.of();
        int charted;
        long sailedAt = Long.MIN_VALUE / 2;
        long unchartedUntil;
        long markedAt;
        List<BlockPos> wade = List.of();
        int waded;
        long wadeSince;
        final Set<BlockPos> wadeEnds = new HashSet<>();
        @Nullable
        BlockPos ladder;
        double ladderHigh;
        long ladderSince;
        final Set<BlockPos> ladders = new HashSet<>();
        final Set<UUID> struckPeople = new HashSet<>();
        @Nullable
        Float heading;
        @Nullable
        BlockPos chop;
        int chops;
        long chopSince;
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
        int stage = -1;
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
        BaritoneAPI.getSettings().allowDownward.value = true;
        String[] words = argument.isBlank() ? new String[0] : argument.strip().split(" ");
        switch (verb) {
            case "go" -> {
                Action go = new Action(Kind.GO, argument, player);
                BaritoneAPI.getSettings().allowBreak.value = false;
                BaritoneAPI.getSettings().allowPlace.value = false;
                if (words.length == 2 && words[1].equals(HERE)) {
                    go.here = true;
                    int blocks = Integer.parseInt(words[0]);
                    go.heading = Body.facing(player);
                    float yaw = go.heading * Mth.DEG_TO_RAD;
                    Body.faced();
                    go.target = new GoalXZ(Mth.floor(player.getX() - Mth.sin(yaw) * blocks), Mth.floor(player.getZ() + Mth.cos(yaw) * blocks));
                } else if (words.length == 1) {
                    go.loose = true;
                    int blocks = Integer.parseInt(words[0]);
                    go.heading = Body.facing(player);
                    float yaw = go.heading * Mth.DEG_TO_RAD;
                    Body.faced();
                    go.target = new GoalXZ(Mth.floor(player.getX() - Mth.sin(yaw) * blocks), Mth.floor(player.getZ() + Mth.cos(yaw) * blocks));
                } else if (words.length == 2) {
                    BlockPos column = Driver.block(minecraft, words[0], "~", words[1]);
                    go.target = new GoalXZ(column.getX(), column.getZ());
                } else if (words.length == 3) {
                    go.spot = Driver.block(minecraft, words[0], words[1], words[2]);
                    go.target = new GoalXZ(go.spot.getX(), go.spot.getZ());
                } else {
                    throw new IllegalArgumentException("go takes <blocks>, <blocks> here, <x> <z> or <x> <y> <z>, not " + argument);
                }
                go.swims = shore != null && shore.distanceTo(player.position()) <= SHORE_BLOCKS;
                go.burrows = cave != null && cave.distanceTo(player.position()) <= SHORE_BLOCKS;
                go.leaps = brink != null && brink.resumes(player, go.target);
                shore = null;
                cave = null;
                brink = null;
                go.arrival = go.spot != null ? new GoalBlock(go.spot) : go.here ? underground(minecraft, go.target) : around(go.target);
                go.surface = go.spot == null && !go.here;
                go.burrows |= go.here;
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
                boolean logs = blocks.stream().anyMatch(block -> block.defaultBlockState().is(BlockTags.LOGS));
                if (logs) {
                    BuiltInRegistries.BLOCK.getOptional(CHOPPED_LOG).ifPresent(blocks::add);
                }
                BaritoneAPI.getSettings().minYLevelWhileMining.value = logs ? player.getBlockY() - TREE_DEPTH : miningFloor;
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
                    idle.dayTime = Senses.dayTime(minecraft);
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
        player.setYRot(Body.facing(player) + Float.parseFloat(words[0]));
        Body.faced();
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
                if (current.ladder != null) {
                    current.ladderSince = current.ticks;
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
                if (current.ladder != null) {
                    yield climbLadder(minecraft, current);
                }
                if (current.chop != null) {
                    yield chop(minecraft, current);
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
                if (roof >= UNDERGROUND_BLOCKS && current.skyTicks >= LAND_TICKS && !current.burrows && (!current.digging || cavern(minecraft, at))) {
                    cave = player.position();
                    yield stop(minecraft, "stopped: the way goes underground here, under " + roof + " blocks of rock and earth; go again to take it", null);
                }
                current.skyTicks = roof >= UNDERGROUND_BLOCKS ? 0 : current.skyTicks + 1;
                Optional<Footing> footing = player.onGround() || player.isInWater() ? footing(minecraft, baritone, current) : Optional.empty();
                if (footing.isPresent() && footing.get().detour() != null) {
                    baritone.getPathingBehavior().cancelEverything();
                    told.add(footing.get().what() + ": round by " + footing.get().detour().toShortString());
                    current.goal = new GoalBlock(footing.get().detour());
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                    yield new Tick(true, null, null);
                }
                if (footing.isPresent() && footing.get().bough() != null && current.chops < CHOPS
                        && player.getEyePosition().distanceTo(Vec3.atCenterOf(footing.get().bough())) <= CHOP_REACH) {
                    baritone.getPathingBehavior().cancelEverything();
                    player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                    current.chops++;
                    current.chop = footing.get().bough();
                    current.chopSince = current.ticks;
                    told.add(footing.get().what() + ": cutting it away, as a player cuts through");
                    yield new Tick(true, null, null);
                }
                if (footing.isPresent() && footing.get().leap() && BaritoneAPI.getSettings().allowParkour.value) {
                    baritone.getPathingBehavior().cancelEverything();
                    BaritoneAPI.getSettings().allowParkour.value = false;
                    told.add(footing.get().what() + ": no more jumps across gaps on this walk, looking for a way round");
                    baritone.getCustomGoalProcess().setGoalAndPath(current.goal);
                    yield new Tick(true, null, null);
                }
                if (footing.isPresent()) {
                    baritone.getPathingBehavior().cancelEverything();
                    player.setSprinting(false);
                    player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                    if (footing.get().asks()) {
                        brink = new Halt(player.position(), current.target);
                        yield stop(minecraft, "stopped: " + footing.get().what() + "; go again the same way to take it", null);
                    }
                    yield stop(minecraft, null, "go failed: " + footing.get().what() + "; find another way");
                }
                Optional<String> refused = refused(minecraft, current);
                if (refused.isPresent() && startLadder(minecraft, current)) {
                    yield new Tick(true, null, null);
                }
                if (refused.isPresent()) {
                    yield stop(minecraft, "stopped: digging here, the bar says \"" + refused.get() + "\"; find another way" + wayIn(minecraft), null);
                }
                if (current.digging && climbing(player) == 0) {
                    restock(minecraft, player);
                }
                if (current.digging && bridging(minecraft, player, current.hollow)) {
                    yield stop(minecraft, null, "go failed: the way on builds out over a drop at " + at.toShortString() + "; step back and find another way");
                }
                if (current.digging) {
                    hollows(minecraft, at, current.hollow);
                }
                if (current.blocked) {
                    current.blocked = false;
                    if (startWade(minecraft, current) || startLadder(minecraft, current)) {
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
                Optional<String> barred = said(minecraft, current);
                if (barred.isPresent() && minecraft.hitResult instanceof BlockHitResult aimed && aimed.getType() == HitResult.Type.BLOCK
                        && current.blocks.contains(minecraft.level.getBlockState(aimed.getBlockPos()).getBlock())) {
                    yield stop(minecraft, null, "gather failed: the bar says \"" + barred.get() + "\" at " + aimed.getBlockPos().toShortString() + "; gather elsewhere, holding " + has);
                }
                if (minecraft.hitResult instanceof BlockHitResult chopping && chopping.getType() == HitResult.Type.BLOCK) {
                    BlockState chopped = minecraft.level.getBlockState(chopping.getBlockPos());
                    if (chopped.is(BlockTags.LOGS) && minecraft.gameMode.isDestroying() && !tree(minecraft, chopping.getBlockPos())) {
                        yield stop(minecraft, null, "gather failed: the " + chopped.getBlock().getName().getString() + " at " + chopping.getBlockPos().toShortString()
                                + " is part of something built, not a tree; gather elsewhere, holding " + has);
                    }
                    int stage = minecraft.gameMode.getDestroyStage();
                    boolean wood = chopped.is(BlockTags.LOGS) || BuiltInRegistries.BLOCK.getKey(chopped.getBlock()).equals(CHOPPED_LOG);
                    if (wood && stage < current.stage) {
                        current.closer = current.ticks;
                    }
                    current.stage = stage;
                }
                if (current.ticks > REPATH_TICKS && !baritone.getMineProcess().isActive()) {
                    yield stop(minecraft, null, "gather failed: no more " + current.what + " to be found, holding " + has);
                }
                if (climbing(player) == 0) {
                    restock(minecraft, player);
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
        long dayTime = Senses.dayTime(minecraft);
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
        OptionalInt sail = sail(boat);
        boolean coast = far <= SAIL_COAST_BLOCKS;
        if (sail.isPresent() && (sail.getAsInt() == 0) != coast && current.ticks - current.sailedAt > SAIL_PRESS_TICKS) {
            Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.getName().equals(SAIL_KEY) && !mapping.isUnbound()).findFirst().ifPresent(mapping -> {
                long window = minecraft.getWindow().getWindow();
                minecraft.keyboardHandler.keyPress(window, mapping.getKey().getValue(), 0, GLFW.GLFW_PRESS, 0);
                minecraft.keyboardHandler.keyPress(window, mapping.getKey().getValue(), 0, GLFW.GLFW_RELEASE, 0);
            });
            current.sailedAt = current.ticks;
            told.add(coast ? "took the sail in, " + Math.round(far) + " blocks from the goal" : "raised the sail");
        }
        if (sail.isPresent() && coast && sail.getAsInt() == 0 && boat.getDeltaMovement().horizontalDistance() < BOAT_STILL_SPEED) {
            return stop(minecraft, "arrived under bare masts, " + Math.round(far) + " blocks short of the goal", null);
        }
        current.ashore = boat.isInWater() || !boat.onGround() ? 0 : current.ashore + 1;
        if (current.ashore > ASHORE_TICKS) {
            return stop(minecraft, "stopped: reached land", null);
        }
        float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        Vec3 now = boat.position();
        boolean held = current.lastBoat != null && now.subtract(current.lastBoat).horizontalDistance() < BOAT_STILL_SPEED;
        current.lastBoat = now;
        current.still = held ? current.still + 1 : 0;
        boolean stalled = current.still > BOAT_STILL_TICKS || current.ticks - current.closer > BOAT_STILL_TICKS * CHART_PATIENCE
                || current.charted >= current.chart.size() && current.detourTicks <= 0 && underRockAhead(minecraft, boat, bearing);
        if (current.ticks - Math.max(current.closer, current.markedAt) > BOAT_STILL_TICKS * CHART_PATIENCE && far <= SHORE_STOP_BLOCKS && Driver.ashore(minecraft, boat)) {
            return stop(minecraft, "at the shore, " + Math.round(far) + " blocks short of the goal", null);
        }
        if (current.charted < current.chart.size()) {
            Vec3 mark = current.chart.get(current.charted);
            if (Math.hypot(mark.x - boat.getX(), mark.z - boat.getZ()) < CHART_NEAR) {
                current.charted++;
                current.markedAt = current.ticks;
            }
            if (current.still > BOAT_STILL_TICKS * CHART_PATIENCE || current.ticks - current.markedAt > BOAT_STILL_TICKS * CHART_PATIENCE * 2) {
                current.chart = List.of();
                current.still = 0;
                current.unchartedUntil = current.ticks + DETOUR_TICKS * 2;
            }
        } else if (stalled && current.detourTicks <= 0 && current.ticks >= current.unchartedUntil) {
            current.chart = chart(minecraft, boat, current.target);
            current.charted = 0;
            if (!current.chart.isEmpty()) {
                current.still = 0;
                current.markedAt = current.ticks;
                told.add("charted a way through open water round what blocks the boat, " + current.chart.size() * CHART_STEP + " blocks");
            }
        }
        if (current.charted < current.chart.size()) {
            Vec3 next = current.chart.get(current.charted);
            bearing = (float) (Mth.atan2(next.z - boat.getZ(), next.x - boat.getX()) * Mth.RAD_TO_DEG) - 90;
        } else if (current.detourTicks > 0) {
            current.detourTicks--;
            bearing = current.detour;
        } else if (stalled) {
            current.still = 0;
            float towards = bearing;
            Optional<Float> open = DETOURS.stream().map(offset -> Mth.wrapDegrees(towards + offset)).filter(way -> open(minecraft, boat, way)).findFirst();
            if (open.isPresent()) {
                current.detour = open.get();
                current.detourTicks = DETOUR_TICKS;
                told.add("rowed round a rock, heading " + Math.round(Mth.wrapDegrees(current.detour - towards)) + " degrees off the way");
                bearing = current.detour;
            } else {
                current.backTicks = DETOUR_TICKS;
                current.unchartedUntil = current.ticks;
                told.add("wedged against what blocks the boat: backed off from it");
            }
        }
        if (current.backTicks > 0) {
            current.backTicks--;
            current.still = 0;
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyDown.setDown(true);
            return stuck(minecraft, current, false);
        }
        Entity foe = Senses.lastAttacker();
        if (foe != null && foe.isAlive() && !foe.isRemoved() && foe.distanceTo(player) <= STRIKE_BACK_BLOCKS && Senses.hurtLately(minecraft, STRIKE_BACK_TICKS)
                && (current.still > BOAT_STILL_TICKS || current.struckAt == foe && current.ticks - current.struckTick < BOAT_STILL_TICKS)
                && Math.abs(Mth.wrapDegrees((float) (Mth.atan2(foe.getZ() - boat.getZ(), foe.getX() - boat.getX()) * Mth.RAD_TO_DEG) - 90 - boat.getYRot())) <= SEAT_TURN) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyDown.setDown(false);
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
        boolean slow = sail.isPresent() && far <= SAIL_SLOW_BLOCKS;
        minecraft.options.keyRight.setDown(turn > ROW_AIM);
        minecraft.options.keyLeft.setDown(turn < -ROW_AIM);
        minecraft.options.keyUp.setDown(Math.abs(turn) < ROW_AHEAD && !slow);
        minecraft.options.keyDown.setDown(slow);
        player.setYRot(bearing);
        return stuck(minecraft, current, closer(current, far));
    }

    private static OptionalInt sail(Entity boat) {
        try {
            return OptionalInt.of(((Number) boat.getClass().getMethod("getSailState").invoke(boat)).intValue());
        } catch (ReflectiveOperationException absent) {
            return OptionalInt.empty();
        }
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
        return said(minecraft, current).filter(line -> current.digging);
    }

    private static Optional<String> said(Minecraft minecraft, Action current) {
        GuiAccessor gui = (GuiAccessor) minecraft.gui;
        int shown = gui.wayfarer$bar() == null ? 0 : gui.wayfarer$barTime();
        boolean fresh = shown > current.barTime;
        current.barTime = shown;
        return fresh && current.ticks > 1 ? Optional.of(gui.wayfarer$bar().getString()) : Optional.empty();
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
        boolean sky = underSky(minecraft, boat);
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
                    boolean squeezed = dx != 0 && dz != 0 && (!afloat(minecraft, x - dx, surface, z, sky) || !afloat(minecraft, x, surface, z - dz, sky));
                    if (squeezed || !afloat(minecraft, x, surface, z, sky)) {
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

    private static boolean afloat(Minecraft minecraft, int x, int y, int z, boolean sky) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos water = new BlockPos(x + dx, y, z + dz);
                if (!minecraft.level.getChunkSource().hasChunk(water.getX() >> 4, water.getZ() >> 4) || !minecraft.level.getFluidState(water).is(FluidTags.WATER)
                        || !minecraft.level.getBlockState(water).getCollisionShape(minecraft.level, water).isEmpty()
                        || !minecraft.level.getBlockState(water.above()).getCollisionShape(minecraft.level, water.above()).isEmpty()
                        || sky && !minecraft.level.canSeeSky(water.above())) {
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
            if (!minecraft.level.getFluidState(water).is(FluidTags.WATER) || !minecraft.level.getBlockState(water.above()).isAir()
                    || underSky(minecraft, boat) && !minecraft.level.canSeeSky(water.above())) {
                return false;
            }
        }
        return true;
    }

    private static boolean underSky(Minecraft minecraft, Boat boat) {
        return minecraft.level.canSeeSky(BlockPos.containing(boat.getX(), boat.getY() + HALF_BLOCK, boat.getZ()));
    }

    private static boolean underRockAhead(Minecraft minecraft, Boat boat, float yaw) {
        if (!underSky(minecraft, boat)) {
            return false;
        }
        double x = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double z = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (int along = 1; along <= COVER_AHEAD; along++) {
            BlockPos ahead = BlockPos.containing(boat.getX() + x * along, boat.getY() + HALF_BLOCK, boat.getZ() + z * along);
            if (minecraft.level.getFluidState(ahead.below()).is(FluidTags.WATER) && !minecraft.level.canSeeSky(ahead)) {
                return true;
            }
        }
        return false;
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
                    || !Senses.water(minecraft.level, column) || !Senses.water(minecraft.level, column.below())) {
                break;
            }
            open = along;
        }
        return open;
    }

    private static Goal underground(Minecraft minecraft, GoalXZ target) {
        int land = minecraft.level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4)
                ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, target.getX(), target.getZ())
                : minecraft.player.getBlockY() + UNDERGROUND_BLOCKS + 1;
        return new Covered(around(target), land - UNDERGROUND_BLOCKS - 1);
    }

    private static Goal around(GoalXZ target) {
        return new GoalComposite(Stream.concat(Stream.of(target), Direction.Plane.HORIZONTAL.stream()
                .map(side -> new GoalXZ(target.getX() + side.getStepX(), target.getZ() + side.getStepZ()))).toArray(Goal[]::new));
    }

    static String worn(LocalPlayer player, boolean digging) {
        return IntStream.range(0, HOTBAR_SLOTS).mapToObj(player.getInventory()::getItem).filter(LastUse::lastUse)
                .filter(stack -> digging ? stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.AXES) : stack.is(ItemTags.AXES)).findFirst()
                .map(stack -> "; the " + stack.getHoverName().getString() + " in the hotbar is worn bare, no better than a bare hand").orElse("");
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
        Entity striker = Senses.lastAttacker();
        if (striker != null && striker != target && Senses.hurtLately(minecraft, STRUCK_TICKS) && striker.distanceTo(player) <= LOST_BLOCKS) {
            told.add("turned to " + Senses.describe(striker) + ", which struck from " + Senses.side(player, striker.position()) + ", leaving " + current.what);
            current.foe = striker;
            current.what = Senses.describe(striker);
            current.nearest = Double.MAX_VALUE;
            current.closer = current.ticks;
            return new Tick(true, null, null);
        }
        double distance = target.distanceTo(player);
        if (distance > LOST_BLOCKS) {
            return stop(minecraft, current.what + " is out of reach, " + (int) distance + " blocks away", null);
        }
        if (Body.deep(target) && !Body.deep(player) && !(player.getVehicle() instanceof Boat && distance <= STRIKE_BACK_BLOCKS)) {
            return stop(minecraft, "stopped: " + current.what + " is in deep water", null);
        }
        Body.strike(minecraft, target, current.ticks, current.stand || player.isPassenger());
        boolean landed = target instanceof LivingEntity living && living.hurtTime > 0;
        if (landed || current.stand || distance > FIGHT_REACH && closer(current, distance)) {
            current.closer = current.ticks;
        }
        if (current.ticks - current.closer > STUCK_TICKS) {
            String why = distance <= FIGHT_REACH ? " took no blow for " : " stayed out of reach for ";
            return stop(minecraft, null, "fight failed: " + current.what + why + STUCK_TICKS / TICKS_PER_SECOND + " s, " + (int) distance + " blocks away");
        }
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
            boolean shrugged = (Senses.lastKind().equals(Senses.FALL) || Senses.lastKind().equals(Senses.BERRY_BUSH) || Senses.pricked()) && current.health - player.getHealth() < HURT
                    || Senses.lastKind().equals("poison") && player.hasEffect(MobEffects.POISON)
                    || Senses.lastKind().equals(Senses.HUNGER) && hungry
                    || Senses.lastKind().equals(exposure);
            current.health = player.getHealth();
            if (!answered && !shrugged && current.kind != Kind.FIGHT) {
                exposure = Senses.lastKind().equals(Senses.HEAT) || Senses.lastKind().equals(Senses.COLD) ? Senses.lastKind() : exposure;
                return Optional.of("stopped: you were hurt, health " + health(player) + ": " + Senses.lastKind());
            }
        }
        Optional<String> struck = struck(minecraft, current, player);
        if (struck.isPresent()) {
            return struck;
        }
        exposure = exposure != null && Senses.exposed(player, exposure) ? exposure : null;
        current.health = Math.max(current.health, player.getHealth());
        boolean low = player.getHealth() <= player.getMaxHealth() * LOW_HEALTH_SHARE;
        if (low && !weak && current.kind != Kind.IDLE) {
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

    private static Optional<String> struck(Minecraft minecraft, Action current, LocalPlayer player) {
        if (current.kind == Kind.FIGHT) {
            return Optional.empty();
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity person && Senses.story(entity) && entity.hasCustomName() && person.hurtTime > 0
                    && entity.distanceTo(player) <= PERSON_HURT_BLOCKS && player.hasLineOfSight(entity) && current.struckPeople.add(entity.getUUID())) {
                String by = minecraft.level.getEntities(entity, entity.getBoundingBox().inflate(STRIKE_REACH), Senses::hostile).stream()
                        .min(Comparator.comparingDouble(entity::distanceTo)).map(foe -> ": " + Senses.describe(foe)).orElse("");
                return Optional.of("stopped: " + entity.getName().getString() + " is hurt, " + Math.round(entity.distanceTo(player)) + " blocks "
                        + Senses.side(player, entity.position()) + by);
            }
        }
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

    private record Footing(String what, boolean asks, @Nullable BlockPos detour, boolean leap, @Nullable BlockPos bough) {
        Footing(String what, boolean asks, @Nullable BlockPos detour) {
            this(what, asks, detour, false, null);
        }
    }

    private static Optional<Footing> footing(Minecraft minecraft, IBaritone baritone, Action current) {
        IPathExecutor executing = baritone.getPathingBehavior().getCurrent();
        if (executing == null) {
            return Optional.empty();
        }
        List<BetterBlockPos> way = executing.getPath().positions();
        for (int step = Math.max(executing.getPosition(), 0); step + 1 < way.size(); step++) {
            BetterBlockPos from = way.get(step);
            if (Vec3.atBottomCenterOf(from).distanceTo(minecraft.player.position()) > LEAP_NEAR) {
                return Optional.empty();
            }
            Optional<Footing> found = footing(minecraft, current, from, way.get(step + 1));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Footing> footing(Minecraft minecraft, Action current, BlockPos from, BlockPos to) {
        Level level = minecraft.level;
        for (BlockPos body : List.of(to, to.above())) {
            BlockState state = level.getBlockState(body);
            if (!state.getFluidState().isEmpty() && !state.getCollisionShape(level, body).isEmpty()) {
                return Optional.of(new Footing("the way goes through " + name(state) + " at " + body.toShortString() + ", which the walk takes for water", false, null, false,
                        state.getDestroySpeed(level, body) >= 0 && state.getDestroySpeed(level, body) <= SOFT_HARDNESS ? body : null));
            }
        }
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx != 0 && dz != 0) {
            BlockPos alongX = new BlockPos(to.getX(), from.getY(), from.getZ());
            BlockPos alongZ = new BlockPos(from.getX(), from.getY(), to.getZ());
            Optional<BlockPos> harmX = Stream.of(alongX, alongX.above()).filter(pos -> harms(level.getBlockState(pos))).findFirst();
            Optional<BlockPos> harmZ = Stream.of(alongZ, alongZ.above()).filter(pos -> harms(level.getBlockState(pos))).findFirst();
            Optional<BlockPos> harm = harmX.or(() -> harmZ);
            if (harm.isPresent()) {
                String what = "the way brushes " + name(level.getBlockState(harm.get())) + " at " + harm.get().toShortString();
                return Optional.of(harmX.isPresent() && harmZ.isPresent() ? new Footing(what + " on both sides", false, null)
                        : new Footing(what, false, harmX.isPresent() ? alongZ : alongX));
            }
        }
        BlockPos foothold = to.below();
        BlockState under = level.getBlockState(foothold);
        if (aloft(level, foothold) && (to.getY() > from.getY() || !woody(level, from.below()))) {
            boolean soft = under.is(BlockTags.LEAVES) || under.is(Blocks.MANGROVE_ROOTS);
            return Optional.of(new Footing("the way climbs onto a tree, onto " + name(under) + " at " + foothold.toShortString() + ", "
                    + aboveGround(level, foothold) + " blocks above the ground", false, null, false, soft ? foothold : null));
        }
        if (current.digging || current.leaps) {
            return Optional.empty();
        }
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        for (int across = 1; across < span; across++) {
            BlockPos gap = new BlockPos(from.getX() + Math.round((float) dx * across / span), Math.min(from.getY(), to.getY()),
                    from.getZ() + Math.round((float) dz * across / span));
            int fall = from.getY() - landing(level, gap);
            if (level.getBlockState(gap.below()).getCollisionShape(level, gap.below()).isEmpty() && !Senses.water(level, gap) && fall > SAFE_FALL) {
                return Optional.of(new Footing("the way leaps a gap over a drop of " + fall + " blocks at " + gap.toShortString(), false, null, true, null));
            }
        }
        for (int down = 0; to.getY() - down > level.getMinBuildHeight(); down++) {
            BlockPos cell = to.below(down);
            boolean water = Senses.water(level, cell);
            if (water || down > 0 && !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                int fall = from.getY() - (water ? cell.getY() : cell.getY() + 1);
                if (fall <= SAFE_FALL) {
                    return Optional.empty();
                }
                String what = water ? "the way jumps " + fall + " blocks down into water at " + cell.toShortString()
                        : "the way steps off a drop of " + fall + " blocks at " + to.toShortString();
                return Optional.of(new Footing(what, fall <= ASK_DROP_BLOCKS || water && Senses.water(level, cell.below()), null));
            }
        }
        return Optional.of(new Footing("the way steps off into the void at " + to.toShortString(), false, null));
    }

    private static int landing(Level level, BlockPos gap) {
        BlockPos.MutableBlockPos cell = gap.mutable();
        while (cell.getY() > level.getMinBuildHeight() && !Senses.water(level, cell) && level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty()) {
            cell.move(Direction.DOWN);
        }
        return cell.getY();
    }

    private static BlockPos bearing(Level level, BlockPos foothold) {
        return level.getBlockState(foothold).getCollisionShape(level, foothold).isEmpty() ? foothold.below() : foothold;
    }

    private static boolean woody(Level level, BlockPos foothold) {
        BlockPos tree = bearing(level, foothold);
        BlockState state = level.getBlockState(tree);
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }

    private static boolean aloft(Level level, BlockPos foothold) {
        BlockPos tree = bearing(level, foothold);
        BlockState state = level.getBlockState(tree);
        return (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) && aboveGround(level, tree) > SAFE_FALL;
    }

    private static int aboveGround(Level level, BlockPos foothold) {
        int above = 0;
        BlockPos.MutableBlockPos below = foothold.below().mutable();
        while (below.getY() > level.getMinBuildHeight() && !Senses.water(level, below)
                && (canopy(level.getBlockState(below)) || !level.getBlockState(below).isCollisionShapeFullBlock(level, below))) {
            above++;
            below.move(Direction.DOWN);
        }
        return above;
    }

    private static boolean squat(BlockState state) {
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return !shape.isEmpty() && shape.max(Direction.Axis.Y) > STEP_UP && !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && state.isPathfindable(PathComputationType.LAND);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean harms(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT) || state.is(BlockTags.FIRE) || HARMFUL.contains(state.getBlock());
    }

    private static String name(BlockState state) {
        return state.getBlock().getName().getString();
    }

    private static String wayIn(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return "";
        }
        BlockPos at = minecraft.player.blockPosition();
        try {
            return server.submit(() -> StoryChecks.wayIn(server, at)).get(WAY_IN_SECONDS, TimeUnit.SECONDS).map(way -> "; " + way).orElse("");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException exception) {
            return "";
        }
    }

    private static String stalled(String line) {
        IPathExecutor executing = baritone().getPathingBehavior().getCurrent();
        if (!line.contains("This movement has taken too long") && !line.contains("Movement returns status UNREACHABLE") || executing == null) {
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
            LocalPlayer player = minecraft.player;
            double far = Math.hypot(go.target.getX() + 0.5 - player.getX(), go.target.getZ() + 0.5 - player.getZ());
            int near = Mth.clamp((int) (far / LOOSE_SHARE), 1, SEGMENT_NEAR);
            return floor(minecraft, go.target.getX(), go.target.getZ()).map(found -> level(minecraft, found))
                    .<Goal>map(found -> new GoalNear(found.above(), near)).orElse(go.arrival);
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
        int top = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
        int solid = 0;
        for (; at.getY() < top; at.move(Direction.UP)) {
            BlockState state = minecraft.level.getBlockState(at);
            if (state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.getCollisionShape(minecraft.level, at).isEmpty()) {
                solid++;
            }
        }
        return solid;
    }

    private static boolean cavern(Minecraft minecraft, BlockPos at) {
        return BlockPos.betweenClosedStream(at.offset(-2, 0, -2), at.offset(2, 2, 2))
                .filter(pos -> minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos).isEmpty() && !minecraft.level.canSeeSky(pos))
                .count() >= CAVERN_CELLS;
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
            if (Senses.water(minecraft.level, below)) {
                return false;
            }
            boolean ground = !minecraft.level.getBlockState(below).getCollisionShape(minecraft.level, below).isEmpty();
            if (ground || !minecraft.level.getFluidState(below).isEmpty()) {
                return down > DROP_BLOCKS || !ground;
            }
        }
        return true;
    }

    private static boolean buildable(Block block) {
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !state.hasBlockEntity()
                && !(block instanceof FallingBlock)
                && !state.is(Tags.Blocks.ORES) && !state.is(Tags.Blocks.STORAGE_BLOCKS) && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == 0
                && block.defaultDestroyTime() >= 0 && !(block instanceof SlimeBlock) && !state.is(Blocks.TNT);
    }

    private static boolean restock(Minecraft minecraft, LocalPlayer player) {
        List<Item> throwaway = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        Inventory inventory = player.getInventory();
        if (climbing(player) >= CLIMB_BLOCKS) {
            return false;
        }
        int found = -1;
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (throwaway.contains(stack.getItem()) && (found < 0 || inventory.getItem(found).is(ItemTags.PLANKS) && !stack.is(ItemTags.PLANKS)
                    || inventory.getItem(found).is(ItemTags.PLANKS) == stack.is(ItemTags.PLANKS) && stack.getCount() > inventory.getItem(found).getCount())) {
                found = slot;
            }
        }
        if (found < 0) {
            return false;
        }
        Comparator<Integer> spare = Comparator.<Integer>comparingInt(slot -> inventory.getItem(slot).isEmpty() ? 0 : throwaway.contains(inventory.getItem(slot).getItem()) ? 1 : 2)
                .thenComparingInt(slot -> inventory.getItem(slot).getCount());
        Optional<Integer> into = IntStream.range(0, Inventory.getSelectionSize()).boxed()
                .filter(slot -> slot != inventory.selected && !kept(player, inventory.getItem(slot)))
                .min(spare);
        if (into.isEmpty() || inventory.getItem(found).getCount() <= inventory.getItem(into.get()).getCount() && throwaway.contains(inventory.getItem(into.get()).getItem())) {
            return false;
        }
        ItemStack stack = inventory.getItem(found);
        ItemStack out = inventory.getItem(into.get());
        told.add("took " + stack.getCount() + " " + stack.getHoverName().getString() + " into the hotbar to climb with"
                + (out.isEmpty() ? "" : ", putting " + Hands.held(out) + " away"));
        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, found, into.get(), ClickType.SWAP, player);
        return true;
    }

    private static boolean kept(LocalPlayer player, ItemStack stack) {
        return stack.getMaxStackSize() == 1 || stack.getFoodProperties(player) != null || stack.getItem() instanceof BlockItem placed && placed.getBlock() instanceof CampfireBlock;
    }

    private static int climbing(LocalPlayer player) {
        List<Item> throwaway = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        return IntStream.range(0, Inventory.getSelectionSize()).mapToObj(player.getInventory()::getItem).filter(stack -> throwaway.contains(stack.getItem()))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static String climbWith(LocalPlayer player) {
        List<Item> throwaway = BaritoneAPI.getSettings().acceptableThrowawayItems.value;
        int hotbar = climbing(player);
        int stored = player.getInventory().items.stream().skip(Inventory.getSelectionSize()).filter(stack -> throwaway.contains(stack.getItem()))
                .mapToInt(ItemStack::getCount).sum();
        if (hotbar > 0) {
            return "; " + hotbar + " plain building blocks in the hotbar to climb with";
        }
        return stored > 0 ? "; " + stored + " plain building blocks in the inventory, and every hotbar slot holds a tool, food, a fire or a tent"
                : "; nothing left to climb with: no stone, earth, planks or other plain building block in the hotbar or the inventory";
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
        LocalPlayer player = minecraft.player;
        boolean blocks = climbing(player) > 0 || restock(minecraft, player);
        boolean pickaxe = IntStream.range(0, HOTBAR_SLOTS).mapToObj(player.getInventory()::getItem)
                .anyMatch(stack -> stack.is(ItemTags.PICKAXES) && !LastUse.lastUse(stack));
        if (!camp && !blocks && !pickaxe) {
            return stop(minecraft, null, "go failed: no way on by walking from " + at.toShortString() + ", and nothing to dig or climb with: no pickaxe and no plain building block");
        }
        current.dug = true;
        current.digging = true;
        current.closer = current.ticks;
        BaritoneAPI.getSettings().allowBreak.value = !camp;
        BaritoneAPI.getSettings().allowDownward.value = false;
        BaritoneAPI.getSettings().allowPlace.value = !camp;
        if (current.goal == current.arrival && !current.here) {
            current.surface = true;
            current.goal = segment(minecraft, current);
        }
        baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
        told.add("no way on by walking from " + minecraft.player.blockPosition().toShortString() + (camp ? ": climbing over their blocks, building and breaking nothing at the caravan's camp"
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
        told.add("water all round at " + at.toShortString() + ", which the walk keeps out of: wading through to " + way.getLast().toShortString() + ", "
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

    private static Tick chop(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        BlockPos bough = current.chop;
        BlockState state = minecraft.level.getBlockState(bough);
        if (!state.is(BlockTags.LEAVES) && !state.is(Blocks.MANGROVE_ROOTS)) {
            minecraft.gameMode.stopDestroyBlock();
            current.chop = null;
            current.closer = current.ticks;
            baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
            return new Tick(true, null, null);
        }
        if (current.ticks - current.chopSince > CHOP_TICKS) {
            minecraft.gameMode.stopDestroyBlock();
            current.chop = null;
            return stop(minecraft, null, "go failed: the " + name(state) + " at " + bough.toShortString() + " did not break; find another way");
        }
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(bough));
        Direction side = Direction.getNearest(player.getEyePosition().subtract(Vec3.atCenterOf(bough)));
        if (current.ticks == current.chopSince) {
            minecraft.gameMode.startDestroyBlock(bough, side);
        } else {
            minecraft.gameMode.continueDestroyBlock(bough, side);
        }
        player.swing(InteractionHand.MAIN_HAND);
        return new Tick(true, null, null);
    }

    private static boolean startLadder(Minecraft minecraft, Action current) {
        BlockPos at = minecraft.player.blockPosition();
        Optional<BlockPos> rung = BlockPos.betweenClosedStream(at.offset(-1, 0, -1), at.offset(1, 1, 1)).map(BlockPos::immutable)
                .filter(pos -> minecraft.level.getBlockState(pos).getBlock() instanceof LadderBlock && !current.ladders.contains(pos.atY(0))
                        && rungs(minecraft, pos) >= LADDER_RUNGS)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(at)));
        if (rung.isEmpty()) {
            return false;
        }
        baritone().getPathingBehavior().cancelEverything();
        current.ladders.add(rung.get().atY(0));
        current.ladder = rung.get();
        current.ladderHigh = minecraft.player.getY();
        current.ladderSince = current.ticks;
        told.add("no way on by walking from " + at.toShortString() + ": climbing the ladder at " + rung.get().toShortString() + " with the keys, as a player does");
        return true;
    }

    private static int rungs(Minecraft minecraft, BlockPos rung) {
        BlockPos.MutableBlockPos up = rung.mutable();
        while (minecraft.level.getBlockState(up).getBlock() instanceof LadderBlock) {
            up.move(Direction.UP);
        }
        return up.getY() - rung.getY();
    }

    private static Tick climbLadder(Minecraft minecraft, Action current) {
        LocalPlayer player = minecraft.player;
        BlockPos rung = current.ladder;
        BlockState state = minecraft.level.getBlockState(rung);
        BlockPos top = rung.above(rungs(minecraft, rung));
        if (player.onGround() && !player.onClimbable() && player.getY() >= top.getY() - 1) {
            Wading.release(minecraft);
            told.add("climbed the ladder up to " + player.blockPosition().toShortString() + ", walking on");
            current.ladder = null;
            current.closer = current.ticks;
            baritone().getCustomGoalProcess().setGoalAndPath(current.goal);
            return new Tick(true, null, null);
        }
        if (player.getY() > current.ladderHigh + CLOSER) {
            current.ladderHigh = player.getY();
            current.ladderSince = current.ticks;
        }
        if (current.ticks - current.ladderSince > LADDER_STILL_TICKS) {
            Wading.release(minecraft);
            told.add("the ladder at " + rung.toShortString() + " took me no higher than " + player.blockPosition().toShortString());
            current.ladder = null;
            return dig(minecraft, current);
        }
        BlockPos wall = state.getBlock() instanceof LadderBlock ? rung.relative(state.getValue(LadderBlock.FACING).getOpposite()) : rung;
        Vec3 aim = Vec3.atCenterOf(wall.atY(player.blockPosition().getY()));
        float yaw = (float) Math.toDegrees(Math.atan2(-(aim.x - player.getX()), aim.z - player.getZ()));
        player.setYRot(yaw);
        player.yRotO = yaw;
        minecraft.options.keyUp.setDown(true);
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
            if (current.kind == Kind.GO && startLadder(minecraft, current)) {
                return new Tick(true, null, null);
            }
            if (current.kind == Kind.GO && !current.dug) {
                return dig(minecraft, current);
            }
            BlockPos at = minecraft.player.blockPosition();
            return stop(minecraft, null, current.kind.name().toLowerCase(Locale.ROOT) + " failed: no closer for " + STUCK_TICKS / 20 + " s, standing at "
                    + at.toShortString() + (current.kind == Kind.GO || current.kind == Kind.GATHER ? worn(minecraft.player, current.digging || current.kind == Kind.GATHER) : "")
                    + (current.kind == Kind.ROW && current.target instanceof GoalXZ column ? ashore(minecraft, column.getX(), column.getZ()) : ""));
        }
        return new Tick(closer, null, null);
    }

    private static String ashore(Minecraft minecraft, int x, int z) {
        if (wet(minecraft, x, z)) {
            return "";
        }
        for (int reach = 1; reach <= SHORE_SEARCH; reach++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == reach && wet(minecraft, x + dx, z + dz)) {
                        return "; the column lies on land, and the nearest water to it is at " + (x + dx) + ", " + (z + dz);
                    }
                }
            }
        }
        return "; the column lies on land, with no water within " + SHORE_SEARCH + " blocks of it";
    }

    private static boolean wet(Minecraft minecraft, int x, int z) {
        if (!minecraft.level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        BlockPos top = new BlockPos(x, minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        return !minecraft.level.getFluidState(top).isEmpty();
    }

    private static Tick stop(Minecraft minecraft, @Nullable String done, @Nullable String failed) {
        Action current = action;
        action = null;
        minecraft.options.keyUse.setDown(false);
        if (current != null && (current.kind == Kind.ROW || current.clearOf != null || !current.wade.isEmpty() || current.ladder != null)) {
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyDown.setDown(false);
        }
        baritone().getPathingBehavior().cancelEverything();
        LocalPlayer player = minecraft.player;
        if (current != null && player != null && (current.kind == Kind.GATHER || current.kind == Kind.GO || current.kind == Kind.FOLLOW)) {
            player.getInventory().selected = current.slot;
        }
        if (current != null && current.chop != null) {
            minecraft.gameMode.stopDestroyBlock();
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
        if (current != null && player != null && current.heading != null) {
            player.setYRot(current.heading);
            player.yRotO = current.heading;
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
                || !Senses.story(entity) && (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(name)
                || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals(name)));
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
        miningFloor = settings.minYLevelWhileMining.value;
        settings.allowBreakAnyway.value = new ArrayList<>(Stream.concat(StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.LEAVES).spliterator(), false)
                .map(holder -> holder.value()), Stream.of(Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SNOW)).toList());
        settings.acceptableThrowawayItems.value = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem placed && buildable(placed.getBlock()))
                .toList();
        settings.allowInventory.value = false;
        settings.allowParkour.value = true;
        settings.allowPlace.value = false;
        settings.allowSprint.value = true;
        settings.antiCheatCompatibility.value = true;
        settings.blacklistClosestOnFailure.value = true;
        settings.blockFreeLook.value = false;
        settings.chatControl.value = false;
        settings.chatDebug.value = true;
        settings.desktopNotifications.value = false;
        settings.echoCommands.value = false;
        settings.freeLook.value = false;
        settings.itemSaver.value = true;
        settings.itemSaverThreshold.value = 1;
        settings.maxFallHeightNoWater.value = SAFE_FALL;
        settings.blocksToAvoid.value = BuiltInRegistries.BLOCK.stream().filter(block -> harms(block.defaultBlockState()) || block.defaultBlockState().is(BlockTags.CAMPFIRES)
                || block.defaultBlockState().is(BlockTags.CLIMBABLE) && !(block instanceof LadderBlock) || squat(block.defaultBlockState())).toList();
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
        settings.useSwordToMine.value = false;
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
