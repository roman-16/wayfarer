package io.github.roman16.wayfarer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class Camps {
    private static final int CLEARANCE = 10;

    private Camps() {}

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre) {
        return place(level, template, centre, Rotation.NONE);
    }

    public static StructurePlaceSettings settings(ServerLevel level, ResourceLocation template, Rotation rotation) {
        Vec3i size = level.getStructureManager().getOrCreate(template).getSize();
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
    }

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre, Rotation rotation) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(template);
        Vec3i size = camp.getSize();
        int x = centre.getX() - size.getX() / 2;
        int z = centre.getZ() - size.getZ() / 2;
        level.getChunk(centre);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        return build(level, template, new BlockPos(x, y - 1, z), rotation);
    }

    public static BlockPos build(ServerLevel level, ResourceLocation template, BlockPos origin, Rotation rotation) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(template);
        Vec3i size = camp.getSize();
        int x = origin.getX();
        int z = origin.getZ();
        Set<BlockPos> ground = ground(camp, settings(level, template, rotation));
        int top = origin.getY();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int crown = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
                if (ground.contains(new BlockPos(dx, 0, dz))) {
                    level(level, origin.offset(dx, 0, dz), crown);
                } else {
                    fell(level, new BlockPos(x + dx, crown, z + dz));
                }
                top = Math.max(top, crown);
            }
        }
        BlockPos low = origin.offset(-LeavesBlock.DECAY_DISTANCE, 1, -LeavesBlock.DECAY_DISTANCE);
        BlockPos high = origin.offset(size.getX() + LeavesBlock.DECAY_DISTANCE, top - origin.getY(), size.getZ() + LeavesBlock.DECAY_DISTANCE);
        prune(level, low, high);
        camp.placeInWorld(level, origin, origin, settings(level, template, rotation), level.random, Block.UPDATE_CLIENTS);
        return origin;
    }

    private static Set<BlockPos> ground(StructureTemplate camp, StructurePlaceSettings settings) {
        Set<BlockPos> ground = new HashSet<>();
        for (Tag block : camp.save(new CompoundTag()).getList("blocks", Tag.TAG_COMPOUND)) {
            ListTag pos = ((CompoundTag) block).getList("pos", Tag.TAG_INT);
            if (pos.getInt(1) == 0) {
                ground.add(StructureTemplate.calculateRelativePosition(settings, new BlockPos(pos.getInt(0), 0, pos.getInt(2))));
            }
        }
        return ground;
    }

    private static void level(ServerLevel level, BlockPos foot, int crown) {
        int column = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.getX(), foot.getZ());
        for (int fill = column; fill < foot.getY(); fill++) {
            level.setBlock(foot.atY(fill), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int clear = foot.getY() + 1; clear <= foot.getY() + CLEARANCE; clear++) {
            level.setBlock(foot.atY(clear), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (int above = foot.getY() + CLEARANCE + 1; above < crown; above++) {
            if (tree(level.getBlockState(foot.atY(above)))) {
                level.setBlock(foot.atY(above), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void fell(ServerLevel level, BlockPos crown) {
        for (BlockPos at = crown.below(); at.getY() > level.getMinBuildHeight(); at = at.below()) {
            BlockState state = level.getBlockState(at);
            if (tree(state)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (!state.isAir() && !state.canBeReplaced()) {
                return;
            }
        }
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
