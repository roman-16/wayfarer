package io.github.roman16.wayfarer;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Wayfarer.MODID)
@PrefixGameTestTemplate(false)
public final class WayfarerTests {
    private WayfarerTests() {}

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    @GameTest(template = "empty")
    public static void obsidianTakesNoSpark(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            for (int y = 1; y <= 5; y++) {
                if (x == 1 || x == 4 || y == 1 || y == 5) {
                    helper.setBlock(new BlockPos(x, y, 2), Blocks.OBSIDIAN);
                }
            }
        }
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.FIRE);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockNotPresent(Blocks.NETHER_PORTAL, new BlockPos(2, 2, 2));
            helper.assertBlockNotPresent(Blocks.NETHER_PORTAL, new BlockPos(3, 4, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void heatFollowsTheWalk(GameTestHelper helper) {
        check(helper, Compass.heat(20, 20) == 2, "walking straight at the shard is much warmer");
        check(helper, Compass.heat(8, 20) == 1, "walking partly towards it is warmer");
        check(helper, Compass.heat(-8, 20) == -1, "walking partly away is colder");
        check(helper, Compass.heat(-20, 20) == -2, "walking straight away is much colder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gazeDrainsFourTimesSlower(GameTestHelper helper) {
        int capacity = Gaze.capacity(0);
        int flown = 0;
        int seconds = 60;
        for (int tick = 0; tick < seconds * 20; tick++) {
            flown = Gaze.step(flown, true, false, capacity);
        }
        int ground = 0;
        int drained = flown;
        while (drained > 0) {
            drained = Gaze.step(drained, false, false, capacity);
            ground++;
        }
        check(helper, ground == seconds * 20 * Balance.INSTANCE.gaze().drainFactor(), "a minute in the air needs four on the ground");
        check(helper, Gaze.step(0, true, true, capacity) == capacity, "at night the gaze finds you at once");
        check(helper, Gaze.capacity(3) > Gaze.capacity(0), "a veil lengthens the flight");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strongholdsKeepTheirRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExactRingPlacement ring = new ExactRingPlacement(
                Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1, 0, Optional.empty(), 12, 1000, HolderSet.direct(), 35000);
        List<ChunkPos> positions = ring.positions(
                42, level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState().sampler()).join();
        check(helper, positions.size() == 12, "twelve strongholds");
        for (ChunkPos pos : positions) {
            double distance = Math.sqrt((double) pos.getMinBlockX() * pos.getMinBlockX() + (double) pos.getMinBlockZ() * pos.getMinBlockZ());
            check(helper, distance > 35000 - 1000 - 32 && distance < 35000 + 1000 + 32, "stronghold at " + (int) distance + " blocks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eachEyeOncePerTeam(GameTestHelper helper) {
        Journey progress = Journey.of(helper.getLevel().getServer());
        UUID team = UUID.randomUUID();
        check(helper, progress.claim(team, Wayfarer.id("shrine/test")), "the first claim takes the eye");
        check(helper, !progress.claim(team, Wayfarer.id("shrine/test")), "the second claim finds the shrine spent");
        check(helper, progress.claim(UUID.randomUUID(), Wayfarer.id("shrine/test")), "another team still finds its eye");
        check(helper, progress.claim(team, Wayfarer.id("shrine/second")), "a second shrine adds a second shard");
        check(helper, progress.slay(team) && !progress.slay(team), "a team slays the dragon once");
        check(helper, progress.wake(team) && !progress.wake(team), "a team's ember wakes once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nightsFollowTheCalendar(GameTestHelper helper) {
        check(helper, !Nights.horde(1) && !Nights.bloodMoon(1), "the first night is calm");
        check(helper, Nights.horde(4) && !Nights.bloodMoon(4), "the fourth night brings a horde");
        check(helper, Nights.bloodMoon(5) && !Nights.horde(5), "the fifth night brings a blood moon");
        check(helper, Nights.horde(20) && Nights.bloodMoon(20), "the twentieth night brings both");
        helper.succeed();
    }

    private static Library.Step step(String id, int shards, List<String> after, List<String> found, boolean slain) {
        return new Library.Step(after, found, id, "", List.of(), "", shards, slain, Story.VOICE, Optional.empty());
    }

    @GameTest(template = "empty")
    public static void storyTakesItsTurn(GameTestHelper helper) {
        List<Library.Step> steps = List.of(
                step("first", 2, List.of(), List.of(), false),
                step("lead", 2, List.of("first"), List.of("camp"), false),
                step("second", 3, List.of("first"), List.of(), false),
                step("after", 0, List.of(), List.of(), true));
        check(helper, Story.due(steps, Set.of(), Set.of(), 1, false).isEmpty(), "nothing speaks before its shards");
        check(helper, Story.due(steps, Set.of(), Set.of(), 3, false).orElseThrow().id().equals("first"), "the first step comes first");
        check(helper, Story.due(steps, Set.of("first"), Set.of(), 3, false).orElseThrow().id().equals("second"), "a step that waits for a lead does not block the story");
        check(helper, Story.due(steps, Set.of("first"), Set.of("camp"), 3, false).orElseThrow().id().equals("lead"), "a found lead lets its step speak");
        check(helper, Story.due(steps, Set.of(), Set.of(), 12, true).orElseThrow().id().equals("after"), "after the dragon only its steps speak");
        check(helper, Story.speaksToday(-1, 0) && Story.speaksToday(4, 5) && !Story.speaksToday(5, 5), "the fire speaks once a day");
        check(helper, Story.name(Story.VOICE, Set.of()).equals("The stranger"), "the voice starts as the stranger");
        check(helper, Story.name(Story.VOICE, Set.of("maren_names")).equals("Wend"), "Maren names him Wend");
        check(helper, Story.name(Story.VOICE, Set.of("maren_names", "unmasked")).equals("The gaze"), "the mask drops");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireKeepsItsCircle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(helper, Fire.fears(EntityType.ZOMBIE.create(level)) && Fire.fears(EntityType.SKELETON.create(level)), "the gaze's creatures fear fire");
        check(helper, !Fire.fears(EntityType.PILLAGER.create(level)) && !Fire.fears(EntityType.WARDEN.create(level)), "people and the silence do not");
        BlockPos fire = new BlockPos(0, 0, 0);
        int radius = Balance.INSTANCE.fire().circleBlocks();
        check(helper, Fire.inside(fire, new Vec3(radius - 1, 0.5, 0.5), radius) && !Fire.inside(fire, new Vec3(radius + 1, 0.5, 0.5), radius), "the circle has its radius");
        check(helper, Fire.untilTomorrowsDusk(11000) == 25000, "resting at evening lasts to tomorrow's dusk");
        check(helper, Fire.untilTomorrowsDusk(15000) == 21000, "resting at night lasts to tomorrow's dusk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stillnessDimsTheEmber(GameTestHelper helper) {
        Balance.Stillness balance = Balance.INSTANCE.stillness();
        check(helper, Stillness.stage(0) == Stillness.MOVING, "the first day is free");
        check(helper, Stillness.stage(balance.dimmingDays()) == Stillness.DIMMING, "then the ember dims");
        check(helper, Stillness.stage(balance.hordeDays()) == Stillness.FOUND, "then the gaze finds you");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leadsGiveAWay(GameTestHelper helper) {
        check(helper, Leads.heading(0, -100).equals("north") && Leads.heading(100, 0).equals("east"), "north is -z and east is +x");
        check(helper, Leads.heading(100, 100).equals("south_east") && Leads.heading(-100, -100).equals("north_west"), "the diagonals");
        check(helper, Leads.round(1234) == 1200 && Leads.round(20) == 100, "distances round to a hundred");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wardHoldsWhileTheBandStands(GameTestHelper helper) {
        BlockPos frame = helper.absolutePos(new BlockPos(2, 1, 2));
        check(helper, !Maren.bandNear(helper.getLevel(), frame), "an empty hall lets the Eye in");
        Vindicator raider = helper.spawn(EntityType.VINDICATOR, new BlockPos(3, 1, 3));
        raider.addTag(Maren.BAND);
        check(helper, Maren.bandNear(helper.getLevel(), frame), "the ward holds while the band stands");
        raider.discard();
        check(helper, !Maren.bandNear(helper.getLevel(), frame), "a fallen band lifts the ward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theRoadEndsWithTheDragon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "wayfarer"), ClientInformation.createDefault());
        long before = level.getServer().overworld().getDayTime();
        level.getServer().overworld().setDayTime(3 * 24000L + 13000);
        boolean sleepless = Nights.sleepless(player) && Nights.fearless(player);
        Journey.of(level.getServer()).slay(Teams.id(player));
        boolean free = !Nights.sleepless(player) && !Nights.tentsShut(player) && !Stillness.found(player);
        level.getServer().overworld().setDayTime(before);
        check(helper, sleepless, "the fourth night is sleepless and the fire fails");
        check(helper, free, "once the dragon is dead the night is quiet");
        check(helper, !Nights.redMoon(level.getServer()), "and the moon stays white");
        helper.succeed();
    }
}
