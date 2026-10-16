package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class StoryTests {
    private static final Map<String, List<String>> OUTCOMES = Map.of("chase", List.of("caught"), "duel", List.of("won"), "escort", List.of("arrived", "late"),
            "sea", List.of("held", "sunk"), "worm", List.of("killed", "spared"));

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
}
