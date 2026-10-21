package io.github.roman16.wayfarer;

import baritone.api.pathing.goals.Goal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

final class Wading {
    private static final int REACH = 16;
    private static final int RISE = 4;
    private static final int NEAR_WATER = 2;
    private static final double ARRIVED = 0.35;
    private static final double STEP_UP = 0.5;
    private static final double CENTRE = 0.5;
    private static final int[] RISES = {1, 0, -1, -2};
    private static final Set<Block> HOT = Set.of(Blocks.CAMPFIRE, Blocks.FIRE, Blocks.LAVA, Blocks.MAGMA_BLOCK,
            Blocks.SOUL_CAMPFIRE, Blocks.SOUL_FIRE);

    private Wading() {}

    static boolean boxed(Minecraft minecraft, BlockPos at) {
        for (BlockPos near : BlockPos.betweenClosed(at.offset(-NEAR_WATER, -1, -NEAR_WATER), at.offset(NEAR_WATER, 1, NEAR_WATER))) {
            FluidState fluid = minecraft.level.getFluidState(near);
            if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
                return true;
            }
        }
        return false;
    }

    static List<BlockPos> way(Minecraft minecraft, BlockPos start, Goal goal, Set<BlockPos> tried) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> crossed = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        parent.put(start, start);
        frontier.add(start);
        if (wet(minecraft, start)) {
            crossed.add(start);
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        while (!frontier.isEmpty()) {
            BlockPos cell = frontier.poll();
            boolean across = crossed.contains(cell);
            if (across && !cell.equals(start) && !wet(minecraft, cell)) {
                double score = goal.heuristic(cell.getX(), cell.getY(), cell.getZ());
                if (!tried.contains(cell) && score < bestScore) {
                    bestScore = score;
                    best = cell;
                }
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int rise : RISES) {
                        BlockPos next = cell.offset(dx, rise, dz);
                        if (parent.containsKey(next) || Math.abs(next.getX() - start.getX()) > REACH || Math.abs(next.getZ() - start.getZ()) > REACH
                                || Math.abs(next.getY() - start.getY()) > RISE || !standable(minecraft, next) || !passage(minecraft, cell, next, dx, dz)) {
                            continue;
                        }
                        parent.put(next, cell);
                        if (across || wet(minecraft, next)) {
                            crossed.add(next);
                        }
                        frontier.add(next);
                        break;
                    }
                }
            }
        }
        if (best == null) {
            return List.of();
        }
        List<BlockPos> way = new ArrayList<>();
        for (BlockPos cell = best; !cell.equals(start); cell = parent.get(cell)) {
            way.add(cell);
        }
        Collections.reverse(way);
        return way;
    }

    static boolean steer(Minecraft minecraft, BlockPos mark) {
        LocalPlayer player = minecraft.player;
        double dx = mark.getX() + CENTRE - player.getX();
        double dz = mark.getZ() + CENTRE - player.getZ();
        if (Math.hypot(dx, dz) < ARRIVED && player.getY() > mark.getY() - STEP_UP) {
            return true;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYRot(yaw);
        player.yRotO = yaw;
        minecraft.options.keyUp.setDown(true);
        minecraft.options.keyJump.setDown(mark.getY() > player.getY() + STEP_UP || player.horizontalCollision);
        return false;
    }

    static void release(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyJump.setDown(false);
    }

    private static boolean passage(Minecraft minecraft, BlockPos cell, BlockPos next, int dx, int dz) {
        int top = Math.max(cell.getY(), next.getY());
        if (next.getY() > cell.getY() && !clear(minecraft, cell.above(2))) {
            return false;
        }
        for (int y = next.getY(); y < cell.getY(); y++) {
            if (!clear(minecraft, new BlockPos(next.getX(), y + 2, next.getZ()))) {
                return false;
            }
        }
        if (dx != 0 && dz != 0) {
            for (BlockPos side : List.of(new BlockPos(cell.getX() + dx, top, cell.getZ()), new BlockPos(cell.getX(), top, cell.getZ() + dz))) {
                if (!clear(minecraft, side) || !clear(minecraft, side.above())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean standable(Minecraft minecraft, BlockPos at) {
        BlockState under = minecraft.level.getBlockState(at.below());
        return clear(minecraft, at) && clear(minecraft, at.above()) && !under.getCollisionShape(minecraft.level, at.below()).isEmpty()
                && !HOT.contains(under.getBlock());
    }

    private static boolean clear(Minecraft minecraft, BlockPos at) {
        if (!minecraft.level.getChunkSource().hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(at);
        return state.getCollisionShape(minecraft.level, at).isEmpty() && !state.getFluidState().is(FluidTags.LAVA) && !HOT.contains(state.getBlock())
                && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.COBWEB) && !state.is(Blocks.POWDER_SNOW);
    }

    private static boolean wet(Minecraft minecraft, BlockPos at) {
        return minecraft.level.getFluidState(at).is(FluidTags.WATER) || minecraft.level.getFluidState(at.above()).is(FluidTags.WATER);
    }
}
