package io.github.roman16.wayfarer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class Camps {
    private static final int CLEARANCE = 10;

    private Camps() {}

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(template);
        Vec3i size = camp.getSize();
        int x = centre.getX() - size.getX() / 2;
        int z = centre.getZ() - size.getZ() / 2;
        level.getChunk(centre);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos origin = new BlockPos(x, y - 1, z);
        int top = origin.getY();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int column = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                for (int fill = column; fill < origin.getY(); fill++) {
                    level.setBlock(new BlockPos(x + dx, fill, z + dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int clear = origin.getY() + 1; clear <= origin.getY() + CLEARANCE; clear++) {
                    level.setBlock(new BlockPos(x + dx, clear, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                int crown = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
                for (int above = origin.getY() + CLEARANCE + 1; above < crown; above++) {
                    BlockPos at = new BlockPos(x + dx, above, z + dz);
                    if (tree(level.getBlockState(at))) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                top = Math.max(top, crown);
            }
        }
        BlockPos low = origin.offset(-LeavesBlock.DECAY_DISTANCE, 1, -LeavesBlock.DECAY_DISTANCE);
        BlockPos high = origin.offset(size.getX() + LeavesBlock.DECAY_DISTANCE, top - origin.getY(), size.getZ() + LeavesBlock.DECAY_DISTANCE);
        prune(level, low, high);
        camp.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        return origin;
    }

    private static void prune(ServerLevel level, BlockPos low, BlockPos high) {
        BlockPos outerLow = low.offset(-LeavesBlock.DECAY_DISTANCE, -LeavesBlock.DECAY_DISTANCE, -LeavesBlock.DECAY_DISTANCE);
        BlockPos outerHigh = high.offset(LeavesBlock.DECAY_DISTANCE, LeavesBlock.DECAY_DISTANCE, LeavesBlock.DECAY_DISTANCE);
        Map<BlockPos, Integer> held = new HashMap<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        for (BlockPos at : BlockPos.betweenClosed(outerLow, outerHigh)) {
            if (level.getBlockState(at).is(BlockTags.LOGS)) {
                held.put(at.immutable(), 0);
                frontier.add(at.immutable());
            }
        }
        while (!frontier.isEmpty()) {
            BlockPos at = frontier.poll();
            int distance = held.get(at) + 1;
            if (distance >= LeavesBlock.DECAY_DISTANCE) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (!held.containsKey(next) && inside(next, outerLow, outerHigh) && loose(level.getBlockState(next))) {
                    held.put(next, distance);
                    frontier.add(next);
                }
            }
        }
        for (BlockPos at : BlockPos.betweenClosed(low, high)) {
            if (loose(level.getBlockState(at)) && !held.containsKey(at)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static boolean tree(BlockState state) {
        return state.is(BlockTags.LOGS) || loose(state);
    }

    private static boolean loose(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean inside(BlockPos at, BlockPos low, BlockPos high) {
        return at.getX() >= low.getX() && at.getX() <= high.getX() && at.getY() >= low.getY() && at.getY() <= high.getY()
                && at.getZ() >= low.getZ() && at.getZ() <= high.getZ();
    }
}
