package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Ground;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public final class Camps {
    private static final int HALF_TURN = 180;
    private static final int TURN_STEP = 30;
    private static final int DRY_STEP = 3;
    private static final int FARTHER = 4;
    private static final int RISE = 4;

    private Camps() {}

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre) {
        return place(level, template, centre, Rotation.NONE);
    }

    public static StructurePlaceSettings settings(ServerLevel level, ResourceLocation template, Rotation rotation) {
        return Footing.settings(rotation, pivot(level, template));
    }

    public static BlockPos pivot(ServerLevel level, ResourceLocation template) {
        Vec3i size = level.getStructureManager().getOrCreate(template).getSize();
        return new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
    }

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre, Rotation rotation) {
        return place(level, template, centre, rotation, 0);
    }

    public static BlockPos place(ServerLevel level, ResourceLocation template, BlockPos centre, Rotation rotation, int floor) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(template);
        StructurePlaceSettings settings = settings(level, template, rotation);
        Vec3i size = camp.getSize();
        int shift = Balance.get().footing().shiftBlocks();
        BlockPos best = null;
        int least = Integer.MAX_VALUE;
        for (int reach = 0; reach <= shift; reach += shift / 2) {
            for (Direction way : reach == 0 ? List.of(Direction.NORTH) : Direction.Plane.HORIZONTAL.stream().toList()) {
                for (int aside : reach == 0 ? new int[] {0} : new int[] {0, reach}) {
                    BlockPos spot = centre.relative(way, reach).relative(way.getClockWise(), aside);
                    level.getChunk(spot);
                    BlockPos corner = new BlockPos(spot.getX() - size.getX() / 2, 0, spot.getZ() - size.getZ() / 2);
                    BlockPos origin = Footing.settle(level, camp, settings, corner, floor);
                    Optional<Integer> earth = Footing.earth(level, camp, settings, origin, floor);
                    if (best == null || earth.isPresent() && earth.get() < least) {
                        best = origin;
                        least = earth.orElse(Integer.MAX_VALUE);
                    }
                }
            }
        }
        return build(level, template, best, rotation, floor);
    }

    public static BlockPos dryBeside(ServerLevel level, BlockPos top, Vec3 out, double distance, int reach) {
        BlockPos driest = BlockPos.containing(Vec3.atLowerCornerOf(top).add(out.scale(distance)));
        int least = Integer.MAX_VALUE;
        for (int further = 0; further < FARTHER; further++) {
            for (int turned = 0; turned <= HALF_TURN; turned += TURN_STEP) {
                for (int sign : turned == 0 || turned == HALF_TURN ? new int[] {1} : new int[] {1, -1}) {
                    Vec3 way = out.yRot((float) Math.toRadians(turned * sign));
                    BlockPos centre = BlockPos.containing(Vec3.atLowerCornerOf(top).add(way.scale(distance + further * reach)));
                    int wet = unfit(level, centre, reach, top.getY());
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

    private static int unfit(ServerLevel level, BlockPos centre, int reach, int mouth) {
        int wet = 0;
        for (int dx = -reach; dx <= reach; dx += DRY_STEP) {
            for (int dz = -reach; dz <= reach; dz += DRY_STEP) {
                BlockPos at = centre.offset(dx, 0, dz);
                level.getChunk(at);
                BlockPos surface = Ground.above(level, at);
                if (!level.getFluidState(surface.below()).isEmpty() || !level.getFluidState(surface).isEmpty() || Math.abs(ground(level, at.getX(), at.getZ()) - mouth) > RISE) {
                    wet++;
                }
            }
        }
        return wet;
    }

    private static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(x, Ground.land(level, x, z), z);
        while (ground.getY() > level.getMinBuildHeight() && level.getFluidState(ground).isEmpty()
                && (tree(level.getBlockState(ground)) || level.getBlockState(ground).canBeReplaced())) {
            ground.move(0, -1, 0);
        }
        return ground.getY();
    }

    public static BlockPos build(ServerLevel level, ResourceLocation template, BlockPos origin, Rotation rotation) {
        return build(level, template, origin, rotation, 0);
    }

    public static BlockPos build(ServerLevel level, ResourceLocation template, BlockPos origin, Rotation rotation, int floor) {
        Footing.build(level, level.getStructureManager().getOrCreate(template), settings(level, template, rotation), origin, floor);
        return origin;
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
                int crown = Ground.crown(level, x, z) + 1;
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
        Footing.prune(level, low, high);
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
                BlockPos open = new BlockPos(centre.getX() + dx, Ground.crown(level, centre.getX() + dx, centre.getZ() + dz) + 1,
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

    private static boolean tree(BlockState state) {
        return state.is(BlockTags.LOGS) || Footing.leaf(state);
    }
}
