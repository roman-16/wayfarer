package io.github.roman16.wayfarer;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;
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
    public static void theEveningArrivesWithinItsRun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Balance.Clock clock = Balance.get().clock();
        long from = Math.floorDiv(level.getDayTime(), Clock.DAY) * Clock.DAY + Clock.ticks(clock.runFrom());
        level.setDayTime(from);
        Clock.begin(level, from, clock.duskRunSeconds());
        long gap = Clock.ticks(clock.evening()) - Clock.ticks(clock.runFrom());
        check(helper, Clock.nightfall(), "the run home did not begin");
        check(helper, level.getDayTimePerTick() * clock.duskRunSeconds() * 20 >= gap - 1,
                "the run home runs " + level.getDayTimePerTick() + " a tick, so it takes longer than " + clock.duskRunSeconds() + " s to reach " + clock.evening());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void containersKeepHalfTheirLoot(GameTestHelper helper) {
        check(helper, ContainerLoot.filled(ResourceLocation.parse("ctov:chests/village/plains_house")), "a village chest is a container's loot");
        check(helper, ContainerLoot.filled(ResourceLocation.parse("supplementaries:loot/galleon/chest")), "a galleon's chest is a container's loot");
        check(helper, !ContainerLoot.filled(ResourceLocation.parse("minecraft:blocks/chest")), "a broken chest drops itself whole");
        check(helper, !ContainerLoot.filled(ResourceLocation.parse("wayfarer:chests/tollbridge/strongbox")), "the pack's own containers keep their numbers");
        helper.succeed();
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
    public static void dawnSparesPeopleAndNames(GameTestHelper helper) {
        Zombie named = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        named.setCustomName(Component.literal("Gerald"));
        Vindicator raider = helper.spawn(EntityType.VINDICATOR, new BlockPos(3, 2, 3));
        boolean spared = !Daylight.exposed(named) && !Daylight.exposed(raider);
        named.discard();
        raider.discard();
        double left = Math.pow(1 - Daylight.crumbleChance(1, 60), 60);
        check(helper, spared, "a named monster and an Illager outlast the dawn");
        check(helper, left > 0.049 && left < 0.051, "a minute of daylight leaves one monster in twenty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void decorationsFollowTheSpread(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1);
        int tries = 100_000;
        int kept = 0;
        for (int index = 0; index < tries; index++) {
            if (Spread.keeps(ResourceLocation.fromNamespaceAndPath("yungsextras", "desert/obelisk"), random)) {
                kept++;
            }
        }
        double factor = Balance.get().structures().spreadFactor();
        double expected = tries / (factor * factor);
        check(helper, Math.abs(kept - expected) < expected * 0.15, "a decoration is as much rarer as a spread structure");
        check(helper, Spread.keeps(ResourceLocation.withDefaultNamespace("ore_iron"), random), "an ore is left alone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gearStopsAtItsLastUse(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        BlockState ore = Blocks.IRON_ORE.defaultBlockState();
        float bare = player.getDigSpeed(ore, at);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(pick.getMaxDamage() - 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        check(helper, player.hasCorrectToolForDrops(ore, helper.getLevel(), at) && player.getDigSpeed(ore, at) > bare, "a sound pick mines iron ore");
        pick.hurtAndBreak(16, helper.getLevel(), player, item -> {});
        check(helper, !pick.isEmpty() && pick.getDamageValue() == pick.getMaxDamage() - 1, "a pick stops at its last use and never breaks");
        check(helper, LastUse.lastUse(pick), "the pick is at its last use");
        check(helper, !player.hasCorrectToolForDrops(ore, helper.getLevel(), at), "at its last use iron ore drops nothing, as for a bare hand");
        check(helper, player.getDigSpeed(ore, at) == bare, "at its last use it digs as a bare hand");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frostIronWantsIron(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(at, Wayfarer.FROST_IRON_ORE.get().defaultBlockState());
        BlockState ore = helper.getLevel().getBlockState(at);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        check(helper, ore.getDestroyProgress(player, helper.getLevel(), at) == 0, "a stone pick does not move frost iron");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        check(helper, ore.getDestroyProgress(player, helper.getLevel(), at) > 0 && player.hasCorrectToolForDrops(ore, helper.getLevel(), at),
                "an iron pick digs frost iron");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoriesSortByKind(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.DIRT, 5));
        inventory.setItem(9, new ItemStack(Items.OAK_LOG, 40));
        inventory.setItem(12, new ItemStack(Items.BREAD, 3));
        inventory.setItem(20, new ItemStack(Items.OAK_LOG, 40));
        inventory.setItem(30, new ItemStack(Items.IRON_AXE));
        inventory.setItem(31, new ItemStack(Items.LEATHER_HELMET));
        Sorting.tidy(inventory);
        check(helper, inventory.getItem(0).is(Items.DIRT) && inventory.getItem(0).getCount() == 5, "the hotbar stays as it was");
        check(helper, inventory.getItem(9).is(Items.IRON_AXE), "tools and weapons come first");
        check(helper, inventory.getItem(10).is(Items.LEATHER_HELMET), "armour comes next");
        check(helper, inventory.getItem(11).is(Items.BREAD), "food comes before blocks");
        check(helper, inventory.getItem(12).getCount() == 64 && inventory.getItem(13).getCount() == 16, "stacks merge, the fullest first");
        check(helper, inventory.getItem(14).isEmpty(), "the rest of the rows is empty");
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(5, new ItemStack(Items.COBBLESTONE, 12));
        chest.setItem(17, new ItemStack(Items.APPLE, 2));
        Sorting.tidy(Sorting.stored(ChestMenu.threeRows(0, inventory, chest)), player);
        check(helper, chest.getItem(0).is(Items.APPLE) && chest.getItem(1).is(Items.COBBLESTONE) && chest.getItem(2).isEmpty(), "a chest sorts the same way");
        check(helper, inventory.getItem(9).is(Items.IRON_AXE), "sorting a chest leaves the player's rows alone");
        helper.succeed();
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
        check(helper, ground == seconds * 20 * Balance.get().gaze().drainFactor(), "a minute in the air needs four on the ground");
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
    public static void eachEyeOnce(GameTestHelper helper) {
        Journey progress = new Journey();
        check(helper, progress.claim(Wayfarer.id("shrine/test")), "the first claim takes the eye");
        check(helper, !progress.claim(Wayfarer.id("shrine/test")), "the second claim finds the shrine spent");
        check(helper, progress.claim(Wayfarer.id("shrine/second")), "a second shrine adds a second shard");
        check(helper, progress.end() && !progress.end(), "the gaze ends once");
        check(helper, progress.wake() && !progress.wake(), "the world's ember wakes once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireKeepsItsCircle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        check(helper, Fire.fears(EntityType.ZOMBIE.create(level)) && Fire.fears(EntityType.SKELETON.create(level)), "the gaze's creatures fear fire");
        check(helper, !Fire.fears(EntityType.PILLAGER.create(level)) && !Fire.fears(EntityType.WARDEN.create(level)), "people and the silence do not");
        BlockPos fire = new BlockPos(0, 0, 0);
        int radius = Balance.get().fire().circleBlocks();
        check(helper, Fire.inside(fire, new Vec3(radius - 1, 0.5, 0.5), radius) && !Fire.inside(fire, new Vec3(radius + 1, 0.5, 0.5), radius), "the circle has its radius");
        check(helper, Fire.untilTomorrowsDusk(11000) == 25000, "resting at evening lasts to tomorrow's dusk");
        check(helper, Fire.untilTomorrowsDusk(15000) == 21000, "resting at night lasts to tomorrow's dusk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stillnessDimsTheEmber(GameTestHelper helper) {
        Balance.Stillness balance = Balance.get().stillness();
        check(helper, Stillness.stage(0) == Stillness.MOVING, "the first day is free");
        check(helper, Stillness.stage(balance.dimmingDays()) == Stillness.DIMMING, "then the ember dims");
        check(helper, Stillness.stage(balance.hordeDays()) == Stillness.FOUND, "then the gaze finds you");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frameRefusesWhileGazeboundStand(GameTestHelper helper) {
        BlockPos frame = helper.absolutePos(new BlockPos(2, 1, 2));
        check(helper, !Gate.guarded(helper.getLevel(), frame), "an empty hall lets the Eye in");
        Vindicator raider = helper.spawn(EntityType.VINDICATOR, new BlockPos(3, 1, 3));
        check(helper, Gate.guarded(helper.getLevel(), frame), "the ward holds while the Gazebound stand");
        raider.discard();
        check(helper, !Gate.guarded(helper.getLevel(), frame), "a fallen band lifts the ward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theRoadEndsWithTheDragon(GameTestHelper helper) {
        check(helper, Nights.sleepless(false, true) && !Nights.sleepless(false, false), "only the gaze finding those who stay makes a sleepless night");
        check(helper, !Nights.sleepless(true, true), "once the dragon is dead the night is quiet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theBellDrawsTheGaze(GameTestHelper helper) {
        check(helper, Nights.found(0, true) == 0, "a team that does not shine is never found by chance");
        check(helper, Nights.found(16, false) > 0 && Nights.found(16, false) < Nights.found(32, false), "the brighter the shine, the likelier the gaze finds the caravan");
        check(helper, Nights.found(16, true) == Balance.get().shine().bellFound() * Nights.found(16, false) && Nights.found(16, true) > Nights.found(16, false),
                "the bell travelling with the caravan makes the gaze find it more often");
        helper.succeed();
    }
}
