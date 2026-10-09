package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class StoryTests {
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
        check(helper, "tamsin".equals(Story.get().quest("ash").orElseThrow().steps().stream()
                .filter(step -> step.id().equals("the_bell")).findFirst().orElseThrow().with()), "the bell is Tamsin's talk, though Agna speaks first");
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
