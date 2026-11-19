package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.HallRoom;
import io.github.roman16.wayfarer.Halls;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class StoryTests {
    private static final Set<String> BUILT_ON_ENTERING = Set.of("clues", "dusk", "escort", "reach", "sail", "say");
    private static final Map<String, List<String>> OUTCOMES = Map.of("chase", List.of("caught"), "escort", List.of("arrived", "late"),
            "sea", List.of("held", "sunk"), "worm", List.of("killed", "spared"));
    private static final Set<String> PUT_DOWN = Set.of(After.HOME, Prologue.LAST_CAMP, Scenes.CACHE);

    private static final List<String> NIGHT_WORDS = List.of("Dusk", "Night", "Torches", "dark", "the night", "tonight");


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
    public static void theDiggersHallIsAHallAndTheirTunnelsAreNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        BoundingBox room = BoundingBox.fromCorners(corner, corner.offset(4, 3, 4));
        Places.register(level, level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(Diggers.LOST), new ChunkPos(room.getCenter()), 0,
                List.of(new HallRoom(room)));
        check(helper, Halls.inside(level, room.getCenter()).filter(Diggers.LOST.location()::equals).isPresent(), "the Diggers' hall room is no hall to the game");
        check(helper, Halls.inside(level, corner.offset(6, 0, 0)).isEmpty(), "the Diggers' tunnels outside their hall room count as the hall");
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
        progress.trust(Map.of("tide_folk", 1));
        check(helper, progress.trust("tide_folk") == 1, "a people's trust rises to what a step gives");
        progress.shun("tide_folk");
        progress.trust(Map.of("tide_folk", 2));
        check(helper, progress.trust("tide_folk") == -1, "trust lost by forcing the way never comes back");
        check(helper, progress.trust("nobody") == 0, "a people never met trusts nobody yet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keepTheOathNeedsBothLivingPeoples(GameTestHelper helper) {
        Progress progress = new Progress();
        progress.trust(Map.of("tide_folk", 1));
        check(helper, Doors.trusting(progress) == 1, "the Tide Folk alone are one people of two");
        progress.choices.put("the_worm/the_worm", "spared");
        check(helper, Doors.trusting(progress) == 2, "a spared worm keeps the Sand Singers' trust");
        progress.choices.put("the_worm/the_worm", "killed");
        check(helper, Doors.trusting(progress) == 1, "a killed worm loses it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theHeirBringsTheFiveTheRoadNeverReaches(GameTestHelper helper) {
        List<String> heirs = Doors.heirs();
        check(helper, heirs.size() == 5, "the heir carries five shards to the frame, not " + heirs.size());
        Set<String> onTheRoad = new HashSet<>();
        Story.get().quests().stream().flatMap(quest -> quest.steps().stream()).filter(step -> step.kind().equals("shard")).forEach(step -> onTheRoad.addAll(Steps.eyes(step)));
        onTheRoad.add("old");
        check(helper, heirs.stream().noneMatch(onTheRoad::contains), "the heir's five are none of the shards the road reaches: " + heirs);
        check(helper, onTheRoad.size() == Doors.road(), "the road holds " + onTheRoad.size() + " shards, the frame counts " + Doors.road());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actsTwoAndThreeRunInOrder(GameTestHelper helper) {
        Map<String, String> after = Map.of("the_worm", "your_eyes", "the_hollow", "malgvens_warning", "the_dreaming", "morning_after", "the_scribes", "the_dreaming",
                "the_rider", "the_scribes", "keepers_door", "the_rider");
        after.forEach((quest, before) -> check(helper, Story.get().quest(quest).orElseThrow().after().equals(List.of(before)), quest + " opens after " + before));
        check(helper, Story.get().quest("your_eyes").orElseThrow().halls() == 1, "Your Eyes opens after Act 2's first hall");
        check(helper, Story.get().quest("malgvens_warning").orElseThrow().halls() == 2, "Malgven's warning opens after Act 2's second hall");
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
                if (BUILT_ON_ENTERING.contains(step.kind()) && step.spec().has("place")) {
                    built.add(step.text("place"));
                }
                if (step.kind().equals("vision")) {
                    built.add(step.text("at"));
                }
                built.forEach(place -> check(helper, Places.buildable(place) || PUT_DOWN.contains(place),
                        quest.id() + "/" + step.id() + " builds " + place + " on entering, which the story has no way to build"));
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
}
