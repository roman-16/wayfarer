package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class StoryTests {
    private static final Map<String, List<String>> OUTCOMES = Map.of("chase", List.of("caught"), "duel", List.of("won"), "escort", List.of("arrived", "late"),
            "sea", List.of("held", "sunk"), "worm", List.of("killed", "spared"));

    private static final List<String> NIGHT_WORDS = List.of("Dusk", "dark", "night", "Torches");

    private static final int MARSH_AWAY = 2048;
    private static final int MARSH_DROP = 3;
    private static final int MARSH_HEIGHT = 64;
    private static final int MARSH_LANDING = 8;
    private static final int MARSH_REACH_UP = 5;
    private static final int MARSH_ROOF = 32;
    private static final int MARSH_SETTLE_TICKS = 64;
    private static final int MARSH_TIMEOUT_TICKS = 256;

    private StoryTests() {}

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    @GameTest(template = "empty")
    public static void everyTalkHasSomeoneToTalkTo(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            for (Story.Step step : quest.steps()) {
                if (step.kind().equals("talk")) {
                    String with = step.with();
                    check(helper, with != null && Story.get().people().containsKey(with), quest.id() + "/" + step.id() + " has nobody to talk to");
                }
            }
        }
        check(helper, "maud".equals(Story.get().quest("ash").orElseThrow().steps().stream()
                .filter(step -> step.id().equals("the_bell")).findFirst().orElseThrow().with()), "the bell is Maud's talk, though Agna speaks first");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actOnePlacesStayOnTheHomeLand(GameTestHelper helper) {
        int reach = Home.reach();
        int ahead = 3072;
        Home.Aim seaward = Home.aim(new BlockPos(reach - 1024, 64, 0), new Vec3(1, 0, 0), ahead);
        check(helper, Vec3.atLowerCornerOf(seaward.from()).add(seaward.way().scale(ahead)).multiply(1, 0, 1).length() <= reach,
                "a place put down from near the coast, facing the sea, lies past the home land");
        Home.Aim abroad = Home.aim(new BlockPos(reach * 2, 64, 0), new Vec3(1, 0, 0), ahead);
        check(helper, Vec3.atLowerCornerOf(abroad.from()).add(abroad.way().scale(ahead)).multiply(1, 0, 1).length() <= reach,
                "a place put down for a team off the home land lies past it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theEmberFindsTheSwampBeforeItsHallStands(GameTestHelper helper) {
        Progress progress = new Progress();
        BlockPos site = new BlockPos(4096, 64, 0);
        progress.place(Marsh.SITE, site);
        check(helper, Places.aim(progress, Marsh.NAME).filter(site::equals).isPresent(), "the ember has nothing to aim at while the swamp's hall waits to go up");
        BlockPos porch = new BlockPos(4104, 72, 8);
        progress.place(Marsh.NAME, porch);
        check(helper, Places.aim(progress, Marsh.NAME).filter(porch::equals).isPresent(), "once the hall stands, the ember aims at the witches' porch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyFootprintCanBeClicked(GameTestHelper helper) {
        List<Entity> print = Clues.print(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), "test");
        check(helper, print.stream().anyMatch(entity -> entity instanceof Interaction && entity.getTags().contains(Steps.CLUE + "test")),
                "a footprint has nothing to click");
        print.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aClueOnABlockTakesTheClickBeforeTheBlock(GameTestHelper helper) {
        BlockPos barrel = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
        List<Entity> marker = Clues.marker(helper.getLevel(), barrel, "test");
        AABB block = new AABB(barrel);
        check(helper, marker.stream().filter(entity -> entity instanceof Interaction).anyMatch(box -> box.getBoundingBox().minX < block.minX
                        && box.getBoundingBox().minY < block.minY && box.getBoundingBox().minZ < block.minZ && box.getBoundingBox().maxX > block.maxX
                        && box.getBoundingBox().maxY > block.maxY && box.getBoundingBox().maxZ > block.maxZ),
                "a clue's box is no bigger than the barrel it marks, so the barrel takes every click");
        marker.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theClockReadsHoursFromDawn(GameTestHelper helper) {
        check(helper, Clock.ticks("06:00") == 0, "a day begins at six in the morning");
        check(helper, Clock.ticks("19:00") == 13000, "dusk is thirteen hours after it");
        check(helper, Clock.ticks("05:30") == 23500, "half past five is the day's last half hour");
        check(helper, Clock.next(13000, "06:00") == Clock.DAY, "the next dawn after dusk is tomorrow's");
        check(helper, Clock.next(0, "06:00") == Clock.DAY, "an hour that has come is the next day's");
        check(helper, Clock.next(Clock.DAY + 100, "19:00") == Clock.DAY + 13000, "a later hour is the same day's");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPassResumedAfterItsHourNeverRunsADay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long until = Math.floorDiv(level.getDayTime(), Clock.DAY) * Clock.DAY + Clock.ticks("19:00");
        long now = until + Clock.ticks("06:30");
        level.setDayTime(now);
        Clock.until(level, until, 30, "00:00");
        check(helper, Clock.passed(), "a pass whose hour has come is over at once");
        check(helper, level.getDayTime() == now, "a pass whose hour has come leaves the time where it stands");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyQuestOpensOnALine(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            boolean told = !quest.road().isEmpty() || quest.found() != null || quest.read() != null || quest.steps().getFirst().kind().equals("wait");
            check(helper, told || quest.kind().equals("main") && quest.halls() == 0,
                    quest.id() + "'s banner can come before anyone speaks of it: give it road lines, said as it opens");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everySpeakerIsInTheScene(GameTestHelper helper) {
        JsonArray households = Caravan.households();
        Set<String> fire = new HashSet<>();
        Set<String> camp = new HashSet<>();
        for (JsonElement household : households) {
            if (household.getAsJsonObject().has("people")) {
                for (JsonElement person : household.getAsJsonObject().getAsJsonArray("people")) {
                    String id = person.getAsJsonObject().get("person").getAsString();
                    camp.add(id);
                    if (person.getAsJsonObject().get("at").getAsString().equals("fire")) {
                        fire.add(id);
                    }
                }
            }
        }
        for (Story.Quest quest : Story.get().quests()) {
            for (Story.Step step : quest.steps()) {
                String with = step.with();
                if (with == null || !camp.contains(with)) {
                    continue;
                }
                List<Story.Line> said = new ArrayList<>(step.lines());
                said.addAll(step.done());
                Map<String, String> moved = new HashMap<>();
                List<JsonObject> moves = new ArrayList<>(step.spec().has("moves") ? List.of(step.spec().getAsJsonObject("moves")) : List.of());
                said.stream().filter(line -> line.stage() != null && line.stage().has("moves")).forEach(line -> moves.add(line.stage().getAsJsonObject("moves")));
                moves.forEach(move -> move.entrySet().forEach(entry -> moved.put(entry.getKey(), entry.getValue().getAsJsonObject().get("place").getAsString())));
                Predicate<String> atFire = person -> fire.contains(person) || moved.getOrDefault(person, "").startsWith("fire_");
                for (Story.Line line : said) {
                    String by = line.by();
                    boolean there = by == null || by.equals(with) || Story.get().person(by).voice() || atFire.test(by) && atFire.test(with)
                            || moved.containsKey(by) && !atFire.test(with);
                    check(helper, there, quest.id() + "/" + step.id() + ": " + by + " speaks, but nothing brings them to " + with);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nobodySpeaksAtTheFireFromElsewhere(GameTestHelper helper) {
        Set<String> fire = new HashSet<>();
        for (JsonElement household : Caravan.households()) {
            if (household.getAsJsonObject().has("people")) {
                for (JsonElement person : household.getAsJsonObject().getAsJsonArray("people")) {
                    if (person.getAsJsonObject().get("at").getAsString().equals("fire")) {
                        fire.add(person.getAsJsonObject().get("person").getAsString());
                    }
                }
            }
        }
        Map<String, Story.Quest> quests = new HashMap<>();
        Story.get().quests().forEach(quest -> quests.put(quest.id(), quest));
        for (Story.Quest quest : Story.get().quests()) {
            for (int index = 0; index < quest.steps().size(); index++) {
                Story.Step step = quest.steps().get(index);
                Optional<Story.Step> before = index > 0 ? Optional.of(quest.steps().get(index - 1))
                        : quest.after().stream().map(quests::get).filter(found -> found != null && !found.steps().isEmpty()).map(found -> found.steps().getLast()).findFirst();
                if (before.filter(previous -> previous.kind().equals(Caravan.KIND)).isEmpty()) {
                    continue;
                }
                for (Story.Line line : step.lines()) {
                    String by = line.by();
                    if (by == null || by.equals(step.with()) || Story.get().person(by).voice()) {
                        continue;
                    }
                    Optional<String> own = lastPlace(quest, index + 1, by);
                    Optional<String> last = own.or(() -> quest.after().stream().map(quests::get).filter(found -> found != null).findFirst()
                            .flatMap(previous -> lastPlace(previous, previous.steps().size(), by)));
                    boolean there = fire.contains(by) || last.map(place -> place.startsWith("fire_")).orElse(true);
                    check(helper, there, quest.id() + "/" + step.id() + ": " + by + " speaks at the caravan's fire, but was last sent to " + last.orElse(""));
                }
            }
        }
        helper.succeed();
    }

    private static Optional<String> lastPlace(Story.Quest quest, int before, String person) {
        for (int earlier = before - 1; earlier >= 0; earlier--) {
            Story.Step step = quest.steps().get(earlier);
            if (step.spec().has("moves") && step.spec().getAsJsonObject("moves").has(person)) {
                return Optional.of(step.spec().getAsJsonObject("moves").getAsJsonObject(person).get("place").getAsString());
            }
        }
        return Optional.empty();
    }

    @GameTest(template = "empty")
    public static void everyStepThatWaitsOnAChoiceNamesOneOffered(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            for (Story.Step step : quest.steps()) {
                if (step.spec().has("when") && !step.text("when").startsWith("!") && !step.text("when").contains("/") && !step.text("when").contains(":")) {
                    String[] when = step.text("when").split("=", -1);
                    Story.Step chooser = quest.steps().stream().filter(earlier -> earlier.id().equals(when[0])).findFirst().orElse(null);
                    check(helper, chooser != null && quest.steps().indexOf(chooser) < quest.steps().indexOf(step),
                            quest.id() + "/" + step.id() + " waits on " + when[0] + ", which comes no earlier in the quest");
                    check(helper, when[1].isEmpty() || chooser.choices().stream().anyMatch(choice -> choice.id().equals(when[1]))
                            || OUTCOMES.getOrDefault(chooser.kind(), List.of()).contains(when[1]),
                            quest.id() + "/" + step.id() + " waits on the choice " + when[1] + ", which " + when[0] + " does not offer");
                }
                if (step.spec().has("asks")) {
                    check(helper, Story.get().quest(step.text("asks")).isPresent(), quest.id() + "/" + step.id() + " asks after " + step.text("asks")
                            + ", which is no quest");
                }
                if (step.spec().has("closes")) {
                    step.spec().getAsJsonObject("closes").keySet().forEach(closed -> check(helper, Story.get().quest(closed).isPresent(),
                            quest.id() + "/" + step.id() + " closes " + closed + ", which is no quest"));
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lostTrustStaysLost(GameTestHelper helper) {
        Progress progress = new Progress();
        progress.trust(Map.of("witches", 1));
        check(helper, progress.trust("witches") == 1, "a people's trust rises to what a step gives");
        progress.shun("witches");
        progress.trust(Map.of("witches", 2));
        check(helper, progress.trust("witches") == -1, "trust lost by forcing the way never comes back");
        check(helper, progress.trust("nobody") == 0, "a people never met trusts nobody yet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyStepIsReachedInOrder(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            for (String after : quest.after()) {
                check(helper, Story.get().quests().indexOf(Story.get().quest(after).orElseThrow()) < Story.get().quests().indexOf(quest),
                        quest.id() + " comes after " + after + ", which the story lists later");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyStepHasANameOfItsOwnInItsQuest(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            Set<String> named = new HashSet<>();
            quest.steps().forEach(step -> check(helper, named.add(step.id()) && !step.id().equals("done"),
                    quest.id() + "/" + step.id() + " shares its name, which a save names the step by"));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyPlaceAStepBuildsOnEnteringCanBeBuilt(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            for (Story.Step step : quest.steps()) {
                List<String> built = new ArrayList<>();
                if (step.spec().has(Steps.BUILDS)) {
                    built.add(step.text(Steps.BUILDS));
                }
                if (step.kind().equals("escort")) {
                    built.add(step.text("place"));
                }
                built.forEach(place -> check(helper, Places.buildable(place), quest.id() + "/" + step.id() + " builds " + place + " on entering, which the story has no way to build"));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachActsFoesWaitForTheirAct(GameTestHelper helper) {
        check(helper, !Foes.arrived(ResourceLocation.parse("naturalist:alligator"), 1), "alligators come in Act 1");
        check(helper, Foes.arrived(ResourceLocation.parse("naturalist:alligator"), 2), "alligators stay away in Act 2");
        check(helper, !Foes.arrived(ResourceLocation.parse("minecraft:ravager"), 2), "ravagers come before Act 3");
        check(helper, Foes.arrived(ResourceLocation.parse("minecraft:zombie"), 1), "zombies wait for an act");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyHoldAtNightComesAfterDusk(GameTestHelper helper) {
        for (Story.Quest quest : Story.get().quests()) {
            for (int index = 0; index < quest.steps().size(); index++) {
                Story.Step step = quest.steps().get(index);
                boolean night = step.kind().equals("hold") && step.lines().stream().anyMatch(line -> NIGHT_WORDS.stream().anyMatch(word -> line.text().contains(word)));
                boolean dusk = quest.steps().subList(0, index).stream().anyMatch(earlier -> earlier.kind().equals("dusk"));
                check(helper, !night || dusk || step.spec().has("moon"), quest.id() + "/" + step.id() + " speaks of the night, but no dusk comes before it");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = MARSH_TIMEOUT_TICKS)
    public static void theMarshMothersWayIsDryAndOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        JsonObject plan = Marsh.plan();
        BlockPos hall = helper.absolutePos(BlockPos.ZERO).offset(MARSH_AWAY, 0, MARSH_AWAY).atY(MARSH_HEIGHT);
        BlockPos low = hall.offset(Prologue.vector(plan.getAsJsonArray("origin")));
        BlockPos high = hall.offset(Prologue.vector(plan.getAsJsonObject("ward").getAsJsonArray("to")));
        for (BlockPos pos : BlockPos.betweenClosed(low.offset(-1, -1, -1), high.offset(1, 0, 1))) {
            boolean rim = pos.getX() < low.getX() || pos.getX() > high.getX() || pos.getZ() < low.getZ() || pos.getZ() > high.getZ() || pos.getY() < low.getY();
            level.setBlock(pos, rim ? Blocks.MUD.defaultBlockState() : Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        Marsh.build(level, hall, Rotation.NONE);
        BoundingBox within = BoundingBox.fromCorners(low, high.atY(hall.getY() + MARSH_ROOF));
        BlockPos stone = hall.offset(Prologue.vector(plan.getAsJsonArray("stone")));
        BlockPos porch = hall.offset(Prologue.vector(plan.getAsJsonObject("places").getAsJsonArray("rue")));
        BlockPos pool = hall.offset(Prologue.vector(plan.getAsJsonObject("places").getAsJsonArray("pool")));
        helper.runAfterDelay(MARSH_SETTLE_TICKS, () -> {
            BlockPos start = Direction.Plane.HORIZONTAL.stream().flatMap(side -> Stream.of(stone.relative(side), stone.relative(side).above()))
                    .filter(pos -> dry(level, pos) && dry(level, pos.above()) && !dry(level, pos.below())).findFirst().orElse(null);
            check(helper, start != null, "nobody can stand beside the Marsh Mothers' calling stone");
            List<BlockPos> walked = walk(level, start, porch::equals, within);
            check(helper, !walked.isEmpty(), "no dry way leads from the Marsh Mothers' calling stone up the stair, through the cellar and the hut to the porch");
            List<BlockPos> swum = swim(level, pool, pos -> pos.distSqr(stone) <= MARSH_LANDING * MARSH_LANDING && level.getBlockState(pos).isAir(), within);
            check(helper, !swum.isEmpty(), "no way under water leads from the black pool to the Marsh Mothers' hall");
            int held = 0;
            int longest = 0;
            for (BlockPos pos : swum) {
                held = breathes(level, pos) ? 0 : held + 1;
                longest = Math.max(longest, held);
            }
            check(helper, longest <= Balance.get().marsh().pocketEveryBlocks(),
                    "the drowned way runs " + longest + " blocks without air, more than the " + Balance.get().marsh().pocketEveryBlocks() + " between its pockets");
            helper.succeed();
        });
    }

    private static boolean dry(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getFluidState(pos).isEmpty() && (state.getCollisionShape(level, pos).isEmpty() || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof LadderBlock);
    }

    private static boolean ladder(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof LadderBlock;
    }

    private static List<BlockPos> walk(ServerLevel level, BlockPos from, Predicate<BlockPos> goal, BoundingBox within) {
        Map<BlockPos, BlockPos> came = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(from));
        came.put(from, from);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (goal.test(at)) {
                return trail(came, at);
            }
            List<BlockPos> moves = new ArrayList<>(List.of(at.above(), at.below()));
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int rise = 1; rise >= -MARSH_DROP; rise--) {
                    moves.add(at.relative(side).above(rise));
                }
            }
            for (BlockPos to : moves) {
                boolean climbing = ladder(level, at) || ladder(level, to);
                boolean up = to.getY() > at.getY();
                boolean straightUp = up && to.getX() == at.getX() && to.getZ() == at.getZ();
                boolean standing = !dry(level, to.below()) || ladder(level, to) || ladder(level, to.below());
                if (came.containsKey(to) || !within.isInside(to) || !dry(level, to) || !dry(level, to.above())
                        || up && !climbing && (straightUp || !dry(level, at.above(2))) || !standing && !(to.getY() < at.getY())) {
                    continue;
                }
                came.put(to, at);
                queue.add(to);
            }
        }
        return List.of();
    }

    private static List<BlockPos> swim(ServerLevel level, BlockPos from, Predicate<BlockPos> goal, BoundingBox within) {
        Map<BlockPos, BlockPos> came = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(from));
        came.put(from, from);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (goal.test(at)) {
                return trail(came, at);
            }
            for (Direction side : Direction.values()) {
                BlockPos to = at.relative(side);
                if (!came.containsKey(to) && within.isInside(to) && to.getY() <= from.getY() && level.getBlockState(to).getCollisionShape(level, to).isEmpty()) {
                    came.put(to, at);
                    queue.add(to);
                }
            }
        }
        return List.of();
    }

    private static boolean breathes(ServerLevel level, BlockPos pos) {
        return BlockPos.betweenClosedStream(pos.offset(-1, 0, -1), pos.offset(1, MARSH_REACH_UP, 1)).anyMatch(near -> level.getBlockState(near).isAir());
    }

    private static List<BlockPos> trail(Map<BlockPos, BlockPos> came, BlockPos end) {
        List<BlockPos> trail = new ArrayList<>();
        for (BlockPos at = end; !came.get(at).equals(at); at = came.get(at)) {
            trail.addFirst(at);
        }
        return trail;
    }
}
