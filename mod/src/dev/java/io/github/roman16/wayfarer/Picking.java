package io.github.roman16.wayfarer;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class Picking {
    private static final int PICK_BLOCKS = 24;
    private static final int FLOOR_BLOCKS = 5;
    private static final int RIPE_AGE = 2;
    private static final int TAKEN_TICKS = 20;
    private static final int COLLECT_BLOCKS = 8;
    private static final int COLLECT_TICKS = 80;

    private record Round(Item item, int wanted, int start, Set<BlockPos> plants, Set<Integer> drops) {}

    private Picking() {}

    static boolean picked(Item item) {
        return item == Items.GLOW_BERRIES || item == Items.SWEET_BERRIES;
    }

    static void pick(Minecraft minecraft, Item item, int wanted) {
        next(minecraft, new Round(item, wanted, minecraft.player.getInventory().countItem(item), new HashSet<>(), new HashSet<>()));
    }

    private static void next(Minecraft minecraft, Round round) {
        LocalPlayer player = minecraft.player;
        String name = round.item().getDescription().getString();
        int picked = Math.max(0, player.getInventory().countItem(round.item()) - round.start());
        if (picked >= round.wanted()) {
            Driver.note("gather: picked " + picked + " " + name);
            return;
        }
        Optional<ItemEntity> dropped = drops(minecraft, round.item()).filter(drop -> !round.drops().contains(drop.getId())).findFirst();
        if (dropped.isPresent()) {
            round.drops().add(dropped.get().getId());
            long from = minecraft.level.getGameTime();
            Legs.walkTo(dropped.get().blockPosition(), 0);
            Driver.after(() -> dropped.get().isRemoved() || minecraft.level.getGameTime() - from > COLLECT_TICKS, "the " + name + " on the ground", () -> {
                Legs.halt();
                next(minecraft, round);
            });
            return;
        }
        BlockPos here = player.blockPosition();
        Optional<BlockPos> bearing = BlockPos.betweenClosedStream(here.offset(-PICK_BLOCKS, -PICK_BLOCKS, -PICK_BLOCKS), here.offset(PICK_BLOCKS, PICK_BLOCKS, PICK_BLOCKS))
                .filter(pos -> !round.plants().contains(pos) && bearing(minecraft.level.getBlockState(pos), round.item()) && footing(minecraft, pos))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(here)));
        if (bearing.isEmpty()) {
            if (picked == 0) {
                Driver.fail("gather failed: no " + name + " to pick within " + PICK_BLOCKS + " blocks that a player could reach from the ground");
            } else {
                Driver.note("gather: picked " + picked + " " + name + ", and none left to pick within " + PICK_BLOCKS + " blocks");
            }
            return;
        }
        BlockPos pos = bearing.get();
        round.plants().add(pos);
        Driver.within(minecraft, pos, at -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
            Driver.drawn("the crosshair to settle on the " + name, () -> {
                if (!(minecraft.hitResult instanceof BlockHitResult aimed) || aimed.getType() != HitResult.Type.BLOCK || !aimed.getBlockPos().equals(pos)) {
                    next(minecraft, round);
                    return;
                }
                KeyMapping.click(minecraft.options.keyUse.getKey());
                Driver.unsynced();
                long from = minecraft.level.getGameTime();
                Driver.after(() -> !bearing(minecraft.level.getBlockState(pos), round.item()) || minecraft.level.getGameTime() - from > TAKEN_TICKS,
                        "the " + name + " to come off", () -> next(minecraft, round));
            });
        });
    }

    private static Stream<ItemEntity> drops(Minecraft minecraft, Item item) {
        LocalPlayer player = minecraft.player;
        return minecraft.level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(COLLECT_BLOCKS), drop -> drop.getItem().is(item)).stream()
                .sorted(Comparator.comparingDouble(player::distanceTo));
    }

    private static boolean bearing(BlockState state, Item item) {
        if (item == Items.GLOW_BERRIES) {
            return state.getBlock() instanceof CaveVines && CaveVines.hasGlowBerries(state);
        }
        return state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= RIPE_AGE;
    }

    private static boolean footing(Minecraft minecraft, BlockPos pos) {
        for (int down = 1; down <= FLOOR_BLOCKS; down++) {
            BlockPos floor = pos.below(down);
            if (!minecraft.level.getFluidState(floor).isEmpty()) {
                return false;
            }
            if (!minecraft.level.getBlockState(floor).getCollisionShape(minecraft.level, floor).isEmpty()) {
                BlockPos feet = floor.above();
                return minecraft.level.getBlockState(feet).getCollisionShape(minecraft.level, feet).isEmpty() && minecraft.level.getFluidState(feet).isEmpty()
                        && minecraft.level.getBlockState(feet.above()).getCollisionShape(minecraft.level, feet.above()).isEmpty();
            }
        }
        return false;
    }
}
