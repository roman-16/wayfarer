package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.NightEndedEvent;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class StoryChecks {
    private static final String OUTPOST = "outpost:";
    private static final String FIRE = "fire:";
    private static final String HALL = "hall";
    private static final String NAMES = "names";
    private static final int CHECK_TICKS = 20;
    private static final int STALL_SECONDS = 60;
    private static final int TICKS_PER_SECOND = 20;
    private static final double CLUE_REACH = 1.5;
    private static final double UNDERFOOT = 0.05;
    private static final Map<String, Long> waiting = new HashMap<>();
    private static final Map<UUID, Long> aimless = new HashMap<>();
    private static final Map<UUID, String> misplaced = new HashMap<>();
    private static final Map<String, Placed> placed = new HashMap<>();
    private static final Set<String> reported = new HashSet<>();
    private static final Set<String> stuck = new HashSet<>();
    private static final double JUMP = 8;
    private static final String DOOR = "door:";
    private static final Set<String> DOORS = Set.of(Locks.NAME, Mansion.NAME);
    private static final String ENTRANCE = "_entrance";
    private static final int DOOR_SEEN = 64;
    private static final String OVERLAP = "overlap:";
    private static final double OVERLAP_SLACK = 0.1;
    private static final int PEOPLE_SEEN = 64;
    private static final int WARD_NEAR = 8;
    private static final int WAY_CELLS = 16384;
    private static final int WAY_REACH = 64;
    private static final List<Entity> clues = new ArrayList<>();
    private static String lastSpeaker = "";
    private static boolean settled;

    private StoryChecks() {}

    private record Placed(UUID body, Vec3 at, boolean seen) {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Engine.running(server)) {
            return;
        }
        clues(server);
        speaker(server);
        people(server);
        aims(server);
        talks(server);
        jumps(server);
        doors(server);
        outposts(server);
        fires(server);
        wards(server);
        names(server);
        long now = server.getTickCount();
        for (Engine.Active active : Engine.active(server)) {
            Optional<String> stall = stalled(server, active);
            if (stall.isEmpty()) {
                waiting.remove(active.key());
                continue;
            }
            long since = waiting.computeIfAbsent(active.key(), key -> now);
            if (now - since >= (long) STALL_SECONDS * TICKS_PER_SECOND) {
                waiting.put(active.key(), now);
                fail(server, active.key() + " stands still for " + STALL_SECONDS + " s though " + stall.get());
            }
        }
    }

    private static Optional<String> stalled(MinecraftServer server, Engine.Active active) {
        if (!Steps.entered(active) || Talk.busy()) {
            return Optional.empty();
        }
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "caravan" -> Clock.evening() && Places.ready(server, Caravan.NAME) && Steps.home(server) ? Optional.of("the evening has come at the caravan")
                    : Clock.evening() && Steps.camped(server) && !active.quest().act().equals(Engine.PROLOGUE) ? Optional.of("the evening has come at the team's own camp")
                    : Optional.empty();
            case "dawn" -> Clock.passed() ? Optional.of("the night has passed") : Optional.empty();
            case "visit" -> Clock.evening() && Caravan.fire(server).isPresent() && Engine.players(server).stream().anyMatch(Fire::resting)
                    ? Optional.of("the evening has come and the team rests at the fire") : Optional.empty();
            case "dusk" -> Clock.passed() && Engine.players(server).stream().anyMatch(Fire::resting) ? Optional.of("the dusk has come and someone rests")
                    : Optional.empty();
            case "talk" -> !Steps.started(active) && step.with() != null && Engine.pursued(server, active.quest())
                    && People.find(server, step.with()).filter(body -> Engine.players(server).stream().anyMatch(player -> player.level() == body.level()
                    && player.distanceToSqr(body) <= (double) Balance.get().talk().startBlocks() * Balance.get().talk().startBlocks()
                    && player.hasLineOfSight(body))).isPresent()
                    ? Optional.of("a player stands with " + step.with()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static void speaker(MinecraftServer server) {
        Optional<String> speaker = Talk.speaker();
        if (speaker.isEmpty() || StoryDev.playing()) {
            lastSpeaker = "";
            return;
        }
        if (speaker.get().equals(lastSpeaker)) {
            return;
        }
        lastSpeaker = speaker.get();
        int reach = Balance.get().talk().reachBlocks();
        People.find(server, speaker.get()).ifPresent(body -> {
            List<ServerPlayer> near = Engine.players(server).stream()
                    .filter(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) reach * reach).toList();
            if (!near.isEmpty() && near.stream().noneMatch(player -> player.hasLineOfSight(body))) {
                fail(server, speaker.get() + " speaks out of sight of " + near.getFirst().getScoreboardName());
            }
            if (!near.isEmpty() && near.stream().noneMatch(Arrivals::there)) {
                fail(server, speaker.get() + " spoke to " + near.getFirst().getScoreboardName() + " before their land was drawn");
            }
            int present = Balance.get().talk().presentBlocks();
            Engine.active(server).stream().filter(active -> Steps.entered(active) && active.quest().id().equals(Talk.speakingIn())).map(active -> active.step().with())
                    .filter(with -> with != null && !with.equals(speaker.get())).flatMap(with -> People.find(server, with).stream())
                    .filter(with -> with.level() == body.level() && with.distanceTo(body) > present).findFirst()
                    .ifPresent(with -> fail(server, speaker.get() + " speaks " + (int) with.distanceTo(body) + " blocks from the talk with "
                            + People.who(with).orElse("?")));
        });
    }

    private static void people(MinecraftServer server) {
        Set<Entity> bodies = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            level.players().forEach(player -> bodies.addAll(level.getEntities((Entity) null, player.getBoundingBox().inflate(PEOPLE_SEEN),
                    body -> body.getTags().contains(People.TAG) && body.isAlive())));
        }
        Progress.of(server).bodies.keySet().forEach(person -> People.find(server, person).filter(Entity::isAlive).ifPresent(bodies::add));
        for (Entity body : bodies) {
            if (!(body.level() instanceof ServerLevel level)) {
                continue;
            }
            String person = People.who(body).orElseGet(() -> body.getName().getString());
            AABB box = extent(body);
            BlockPos under = BlockPos.containing(body.getX(), box.minY - UNDERFOOT, body.getZ());
            String pose = body.getTags().stream().filter(tag -> tag.startsWith(People.POSE)).map(tag -> " (" + tag.substring(People.POSE.length()) + ")").findFirst()
                    .orElse("");
            String wrong = !level.noBlockCollision(body, box.deflate(UNDERFOOT)) ? "stands inside " + inside(level, box)
                    : level.getBlockState(under).is(BlockTags.LEAVES) ? "stands on " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(under).getBlock()) : null;
            if (wrong == null) {
                misplaced.remove(body.getUUID());
            } else if (!wrong.equals(misplaced.put(body.getUUID(), wrong))) {
                fail(server, person + pose + " " + wrong + " at " + body.blockPosition().toShortString());
            }
            for (Entity other : bodies) {
                String pair = OVERLAP + body.getUUID() + other.getUUID();
                if (other != body && other.level() == level && body.getUUID().compareTo(other.getUUID()) < 0
                        && extent(other).deflate(OVERLAP_SLACK).intersects(box.deflate(OVERLAP_SLACK)) && reported.add(pair)) {
                    fail(server, person + " overlaps " + People.who(other).orElseGet(() -> other.getName().getString()) + " at " + body.blockPosition().toShortString());
                }
            }
        }
    }

    private static AABB extent(Entity body) {
        return body.getTags().stream().filter(tag -> tag.startsWith(People.POSE)).map(tag -> tag.substring(People.POSE.length())).filter(People::lying).findFirst()
                .map(pose -> People.length(body.position(), body.getYRot(), pose)).orElse(body.getBoundingBox());
    }

    private static String inside(ServerLevel level, AABB box) {
        return BlockPos.betweenClosedStream(box.deflate(UNDERFOOT)).filter(pos -> !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                .map(pos -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()).findFirst().orElse("a block");
    }

    private static void aims(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : Engine.players(server)) {
            Optional<Engine.Active> tracked = Engine.tracked(player).filter(Steps::entered);
            Optional<String> goal = tracked.flatMap(Steps::goal);
            if (goal.isEmpty() || goal.get().equals(Caravan.NAME) && Caravan.standing(server).isEmpty() || Places.aim(Progress.of(server), player, goal.get()).isPresent()) {
                aimless.remove(player.getUUID());
                continue;
            }
            long since = aimless.computeIfAbsent(player.getUUID(), uuid -> now);
            if (now - since >= (long) STALL_SECONDS * TICKS_PER_SECOND) {
                aimless.put(player.getUUID(), now);
                fail(server, tracked.get().key() + " is tracked, but the compass has nowhere to point for " + STALL_SECONDS + " s");
            }
        }
    }

    private static void talks(MinecraftServer server) {
        List<String> now = Talk.stuck(server, (long) STALL_SECONDS * TICKS_PER_SECOND);
        stuck.retainAll(now);
        now.stream().filter(stuck::add).forEach(why -> fail(server, why + " for " + STALL_SECONDS + " s"));
    }

    public static Optional<String> wayIn(MinecraftServer server, BlockPos near) {
        Progress progress = Progress.of(server);
        Set<String> names = new HashSet<>(ways(progress));
        Story.get().quests().stream().flatMap(quest -> quest.steps().stream()).flatMap(step -> Stream.of("at", "place").filter(step.spec()::has).map(step::text))
                .forEach(names::add);
        return names.stream().flatMap(name -> progress.place(name).map(at -> Map.entry(name, at)).stream())
                .filter(way -> way.getValue().closerThan(near, WAY_REACH * 2))
                .min(Comparator.comparingDouble(way -> way.getValue().distSqr(near)))
                .map(way -> "the way into " + way.getKey() + " is at " + way.getValue().toShortString());
    }

    private static Set<String> ways(Progress progress) {
        Set<String> ways = new HashSet<>(DOORS);
        Story.get().quests().stream().flatMap(quest -> quest.steps().stream()).filter(step -> step.kind().equals("reach") && step.spec().has("place"))
                .map(step -> step.text("place"))
                .filter(name -> progress.place(name).filter(at -> progress.wards.values().stream().anyMatch(box -> box.inflatedBy(WARD_NEAR).isInside(at))
                        && progress.wards.values().stream().noneMatch(box -> box.isInside(at))).isPresent())
                .forEach(ways::add);
        progress.places.keySet().stream().filter(name -> name.endsWith(ENTRANCE)).forEach(ways::add);
        return ways;
    }

    private static void doors(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Set<String> ways = ways(progress);
        progress.places.forEach((name, at) -> {
            if (!ways.contains(name) || reported.contains(DOOR + name) || !level.isLoaded(at)
                    || level.players().stream().noneMatch(player -> player.blockPosition().closerThan(at, DOOR_SEEN))) {
                return;
            }
            reported.add(DOOR + name);
            if (!open(level, progress, at)) {
                fail(server, "the way into " + name + " at " + at.toShortString() + " is not reached from open sky within " + WAY_REACH + " blocks");
                return;
            }
            for (BlockPos pos : List.of(at, at.above())) {
                var state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    fail(server, "the way into " + name + " at " + pos.toShortString() + " stands in " + BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()));
                    return;
                }
                if (!state.is(BlockTags.DOORS) && !state.getCollisionShape(level, pos).isEmpty()) {
                    fail(server, "the way into " + name + " at " + pos.toShortString() + " is shut in by " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                    return;
                }
            }
        });
    }

    private static boolean open(ServerLevel level, Progress progress, BlockPos from) {
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(from));
        Set<BlockPos> seen = new HashSet<>(queue);
        while (!queue.isEmpty() && seen.size() < WAY_CELLS) {
            BlockPos at = queue.poll();
            if (level.canSeeSky(at) && progress.wards.values().stream().noneMatch(box -> box.isInside(at))) {
                return true;
            }
            for (Direction side : Direction.values()) {
                BlockPos to = at.relative(side);
                if (to.distManhattan(from) <= WAY_REACH && passable(level, to) && seen.add(to)) {
                    queue.add(to);
                }
            }
        }
        return false;
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty() || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.getBlock() instanceof LadderBlock;
    }

    private static void jumps(MinecraftServer server) {
        if (StoryDev.playing()) {
            placed.clear();
            settled = false;
            return;
        }
        boolean checking = settled;
        settled = true;
        for (String person : Progress.of(server).bodies.keySet()) {
            Optional<Entity> found = People.find(server, person).filter(body -> body.level() instanceof ServerLevel);
            Placed before = placed.remove(person);
            if (found.isEmpty()) {
                continue;
            }
            Entity body = found.get();
            ServerLevel level = (ServerLevel) body.level();
            boolean seen = People.seen(level, body.position());
            placed.put(person, new Placed(body.getUUID(), body.position(), seen));
            boolean jumped = before != null && before.body().equals(body.getUUID()) && !body.isPassenger() && before.at().distanceTo(body.position()) > JUMP;
            if (checking && jumped && watched(level) && (seen || before.seen())) {
                fail(server, person + " jumped " + (int) before.at().distanceTo(body.position()) + " blocks in sight of a player to " + body.blockPosition().toShortString());
            }
        }
    }

    @SubscribeEvent
    public static void onNightEnded(NightEndedEvent event) {
        if (!event.reached()) {
            fail(event.server(), "a sleepless night ended with no wave reaching the team");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCompass(CompassEvent event) {
        ServerPlayer player = event.player();
        MinecraftServer server = player.server;
        if (!Engine.running(server)) {
            return;
        }
        int exact = Balance.get().compass().exactBlocks();
        for (Engine.Active active : Engine.active(server)) {
            if (!Steps.entered(active) || !Steps.felt(active)) {
                continue;
            }
            Steps.goal(active).flatMap(name -> Places.aim(Progress.of(server), name)).ifPresent(pos -> {
                boolean marked = event.markers().stream().anyMatch(marker -> marker.x() == pos.getX() && marker.z() == pos.getZ());
                double away = Math.sqrt(player.blockPosition().distToCenterSqr(pos.getX(), player.getY(), pos.getZ()));
                if (marked && away > exact && reported.add("felt " + active.key() + " " + player.getUUID())) {
                    fail(server, player.getScoreboardName() + "'s compass marks a felt goal " + (int) away + " blocks away");
                }
            });
        }
    }

    private static boolean watched(ServerLevel level) {
        return level.players().stream().anyMatch(player -> player instanceof ServerPlayer arrived && Arrivals.there(arrived));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level && event.getEntity().getTags().contains(People.TAG)
                && !event.getEntity().getTags().contains(People.APPEARS) && settled
                && !StoryDev.playing() && watched(level) && People.seen(level, event.getEntity().position())) {
            fail(level.getServer(), event.getEntity().getName().getString() + " appeared in sight of a player at " + event.getEntity().blockPosition().toShortString());
        }
        if (!event.loadedFromDisk() && event.getEntity() instanceof Display.BlockDisplay
                && event.getEntity().getTags().stream().anyMatch(tag -> tag.startsWith(Steps.CLUE))) {
            clues.add(event.getEntity());
        }
    }

    private static void clues(MinecraftServer server) {
        for (Entity display : List.copyOf(clues)) {
            clues.remove(display);
            if (display.isRemoved() || !(display.level() instanceof ServerLevel level)) {
                continue;
            }
            String clue = display.getTags().stream().filter(tag -> tag.startsWith(Steps.CLUE)).findFirst().orElseThrow();
            boolean asked = Story.get().quests().stream().flatMap(quest -> quest.steps().stream()).flatMap(step -> step.clues().stream())
                    .anyMatch(step -> clue.equals(Steps.CLUE + step.id()));
            if (asked && level.getEntitiesOfClass(Interaction.class, display.getBoundingBox().inflate(CLUE_REACH), box -> box.getTags().contains(clue)).isEmpty()) {
                fail(server, "the clue " + clue.substring(Steps.CLUE.length()) + " at " + display.blockPosition().toShortString() + " cannot be clicked");
            }
        }
    }

    private static void outposts(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        for (String name : Errands.seatedOutposts(progress)) {
            Optional<BlockPos> place = progress.place(name);
            if (place.isEmpty() || !level.isLoaded(place.get()) || reported.contains(OUTPOST + name)) {
                continue;
            }
            if (!progress.rooms.containsKey(name) && reported.add(OUTPOST + name)) {
                fail(server, name + " stands at " + place.get().toShortString() + " with no outpost raised");
            }
        }
    }

    private static void fires(MinecraftServer server) {
        Optional<BlockPos> camp = Caravan.standing(server);
        if (camp.isPresent() && Engine.overworld(server).isLoaded(camp.get()) && Caravan.fire(server).isEmpty() && reported.add(FIRE + camp.get())) {
            fail(server, "caravan camp at " + camp.get().toShortString() + " has no lit fire");
        }
    }

    private static void wards(MinecraftServer server) {
        Set<String> places = new HashSet<>(Progress.of(server).wards.keySet());
        places.add(HALL);
        for (String place : places) {
            for (String key : List.of("wayfarer.ward." + place, "wayfarer.ward." + place + ".build")) {
                if (!Language.getInstance().has(key) && reported.add(key)) {
                    fail(server, "ward " + place + " has no line " + key);
                }
            }
        }
    }

    private static void names(MinecraftServer server) {
        if (!reported.add(NAMES)) {
            return;
        }
        Map<String, Set<ResourceLocation>> creatures = new HashMap<>();
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> creatures.computeIfAbsent(type.getDescription().getString().toLowerCase(Locale.ROOT), name -> new HashSet<>())
                .add(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
        Story.get().people().forEach((id, person) -> Stream.of(person.name(), person.unnamed()).filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT).replaceFirst("^(a|an|the) ", ""))
                .filter(name -> creatures.containsKey(name) && !creatures.get(name).contains(person.creature()))
                .forEach(name -> fail(server, id + " is called " + name + ", the name of a creature of the game")));
    }

    private static void fail(MinecraftServer server, String why) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("story check: " + why).withStyle(ChatFormatting.RED), false);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        waiting.clear();
        aimless.clear();
        misplaced.clear();
        placed.clear();
        settled = false;
        reported.clear();
        stuck.clear();
        clues.clear();
        lastSpeaker = "";
    }
}
