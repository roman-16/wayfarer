package io.github.roman16.wayfarer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public final class Camps {
    private static final int CLEARANCE = 10;

    private static final int SAMPLE = 2;
    private static final int HALF_TURN = 180;
    private static final int TURN_STEP = 30;
    private static final int DRY_STEP = 3;
    private static final int FARTHER = 4;

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
        List<Integer> grounds = new ArrayList<>();
        for (int dx = 0; dx < size.getX(); dx += SAMPLE) {
            for (int dz = 0; dz < size.getZ(); dz += SAMPLE) {
                grounds.add(ground(level, x + dx, z + dz));
            }
        }
        Collections.sort(grounds);
        return build(level, template, new BlockPos(x, grounds.get(grounds.size() / 2), z), rotation);
    }

    public static BlockPos dryBeside(ServerLevel level, BlockPos top, Vec3 out, double distance, int reach) {
        BlockPos driest = BlockPos.containing(Vec3.atLowerCornerOf(top).add(out.scale(distance)));
        int least = Integer.MAX_VALUE;
        for (int further = 0; further < FARTHER; further++) {
            for (int turned = 0; turned <= HALF_TURN; turned += TURN_STEP) {
                for (int sign : turned == 0 || turned == HALF_TURN ? new int[] {1} : new int[] {1, -1}) {
                    Vec3 way = out.yRot((float) Math.toRadians(turned * sign));
                    BlockPos centre = BlockPos.containing(Vec3.atLowerCornerOf(top).add(way.scale(distance + further * reach)));
                    int wet = wet(level, centre, reach);
                    if (wet == 0) {
                        return centre;
                    }
                    if (wet < least) {
                        least = wet;
                        driest = centre;
                    }
                }
            }
        }
        return driest;
    }

    private static int wet(ServerLevel level, BlockPos centre, int reach) {
        int wet = 0;
        for (int dx = -reach; dx <= reach; dx += DRY_STEP) {
            for (int dz = -reach; dz <= reach; dz += DRY_STEP) {
                BlockPos at = centre.offset(dx, 0, dz);
                level.getChunk(at);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                if (!level.getFluidState(surface.below()).isEmpty() || !level.getFluidState(surface).isEmpty()) {
                    wet++;
                }
            }
        }
        return wet;
    }

    private static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        while (ground.getY() > level.getMinBuildHeight() && level.getFluidState(ground).isEmpty()
                && (tree(level.getBlockState(ground)) || level.getBlockState(ground).canBeReplaced())) {
            ground.move(0, -1, 0);
        }
        return ground.getY();
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

    public static void clearing(ServerLevel level, BlockPos centre, int radius, boolean stumps) {
        int top = centre.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                int crown = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                top = Math.max(top, crown);
                BlockPos stump = null;
                for (BlockPos at = new BlockPos(x, crown - 1, z); at.getY() > level.getMinBuildHeight(); at = at.below()) {
                    BlockState state = level.getBlockState(at);
                    if (state.is(BlockTags.LOGS)) {
                        stump = at.immutable();
                    } else if (!tree(state) && !state.isAir() && !state.canBeReplaced()) {
                        break;
                    }
                }
                BlockState wood = stump == null || !stumps ? null : level.getBlockState(stump);
                fell(level, new BlockPos(x, crown, z));
                if (wood != null) {
                    level.setBlock(stump, wood, Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos low = centre.offset(-radius - LeavesBlock.DECAY_DISTANCE, 0, -radius - LeavesBlock.DECAY_DISTANCE);
        BlockPos high = new BlockPos(centre.getX() + radius + LeavesBlock.DECAY_DISTANCE, top, centre.getZ() + radius + LeavesBlock.DECAY_DISTANCE);
        prune(level, low, high);
        for (BlockPos at : BlockPos.betweenClosed(low, high)) {
            if (level.getBlockState(at).is(Blocks.SNOW) && level.getBlockState(at.below()).isAir()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        snow(level, centre, radius);
    }

    public static void snow(ServerLevel level, BlockPos centre, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos open = new BlockPos(centre.getX() + dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + dx, centre.getZ() + dz),
                        centre.getZ() + dz);
                BlockState below = level.getBlockState(open.below());
                if ((below.hasProperty(SnowyDirtBlock.SNOWY) || below.is(BlockTags.LOGS)) && level.getBiome(open).value().shouldSnow(level, open)) {
                    level.setBlock(open, Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (below.hasProperty(SnowyDirtBlock.SNOWY)) {
                        level.setBlock(open.below(), below.setValue(SnowyDirtBlock.SNOWY, true), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
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
