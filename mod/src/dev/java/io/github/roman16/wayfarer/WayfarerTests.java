package io.github.roman16.wayfarer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
        check(helper, progress.slay(team) && !progress.slay(team), "a team slays the dragon once");
        helper.succeed();
    }
}
