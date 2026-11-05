package io.github.roman16.wayfarer.story;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Ground {
    private static final int BELOW = 16;
    private static final int CLIMB = 2;
    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;
    private static final int REACH = 8;
    private static final double ROOM = 0.5;
    private static final int THICK = 2;
    private static final double LAYER = 2 / 16.0;

    private Ground() {}

    public static int land(Level level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        while (pos.getY() > level.getMinBuildHeight() && level.getFluidState(pos).isEmpty() && cover(level.getBlockState(pos))) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    public static BlockPos land(Level level, BlockPos column) {
        return column.atY(land(level, column.getX(), column.getZ()));
    }

    public static BlockPos above(Level level, BlockPos column) {
        return land(level, column).above();
    }

    public static double surface(Level level, int x, int z) {
        int land = land(level, x, z);
        return feet(level, new Vec3(x + 0.5, land + 1, z + 0.5)).map(feet -> feet.y).orElse(land + 1.0);
    }

    public static boolean wet(Level level, int x, int z) {
        return !level.getFluidState(new BlockPos(x, land(level, x, z), z)).isEmpty();
    }

    public static int crown(Level level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    public static int sky(Level level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    public static boolean cover(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.VINE) || state.canBeReplaced();
    }

    public static Vec3 stand(Level level, Vec3 near) {
        for (int reach = 0; reach <= REACH; reach++) {
            Vec3 best = null;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) {
                        continue;
                    }
                    Vec3 column = reach == 0 ? near : new Vec3(Math.floor(near.x) + dx + 0.5, near.y, Math.floor(near.z) + dz + 0.5);
                    Optional<Vec3> feet = feet(level, column).filter(spot -> standing(level, spot));
                    if (feet.isPresent() && (best == null || feet.get().distanceToSqr(near) < best.distanceToSqr(near))) {
                        best = feet.get();
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return dry(level, near);
    }

    public static Optional<Vec3> feet(Level level, Vec3 at) {
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        int from = (int) Math.floor(at.y) + CLIMB;
        Vec3 best = null;
        for (int y = from; y >= from - CLIMB - BELOW; y--) {
            BlockPos below = new BlockPos(x, y - 1, z);
            BlockState state = level.getBlockState(below);
            if (!level.getFluidState(below).isEmpty()) {
                break;
            }
            Optional<Double> top = top(level, below, state);
            if (top.isEmpty()) {
                continue;
            }
            Vec3 feet = new Vec3(at.x, top.get(), at.z);
            if (fits(level, feet) && (best == null || Math.abs(feet.y - at.y) < Math.abs(best.y - at.y))) {
                best = feet;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean standing(Level level, Vec3 feet) {
        return !roof(level, feet) && clearOfPlayers(level, feet);
    }

    public static boolean standable(Level level, Vec3 feet) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - LAYER / 2, feet.z);
        return level.getFluidState(below).isEmpty() && top(level, below, level.getBlockState(below)).filter(top -> Math.abs(top - feet.y) < LAYER).isPresent()
                && fits(level, feet);
    }

    private static Optional<Double> top(Level level, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(Blocks.POWDER_SNOW) || hazard(state)) {
            return Optional.empty();
        }
        if (state.is(Blocks.SNOW)) {
            return Optional.of(pos.getY() + state.getValue(SnowLayerBlock.LAYERS) * LAYER);
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? Optional.empty() : Optional.of(pos.getY() + shape.max(Direction.Axis.Y));
    }

    private static boolean fits(Level level, Vec3 feet) {
        AABB box = new AABB(feet.x - HALF_WIDTH, feet.y, feet.z - HALF_WIDTH, feet.x + HALF_WIDTH, feet.y + HEIGHT, feet.z + HALF_WIDTH);
        if (!level.noCollision(box)) {
            return false;
        }
        return BlockPos.betweenClosedStream(box.deflate(1e-3)).noneMatch(cell -> {
            BlockState state = level.getBlockState(cell);
            return !level.getFluidState(cell).isEmpty() || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.LEAVES) || hazard(state)
                    || state.is(Blocks.SNOW) && cell.getY() + state.getValue(SnowLayerBlock.LAYERS) * LAYER > feet.y + 1e-3;
        });
    }

    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB) || state.is(Blocks.LAVA);
    }

    private static boolean roof(Level level, Vec3 feet) {
        BlockPos ground = BlockPos.containing(feet.x, feet.y - LAYER / 2, feet.z);
        for (int depth = 1; depth <= THICK; depth++) {
            BlockPos under = ground.below(depth);
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty() && level.getFluidState(under).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean clearOfPlayers(Level level, Vec3 feet) {
        AABB box = new AABB(feet.x - HALF_WIDTH, feet.y, feet.z - HALF_WIDTH, feet.x + HALF_WIDTH, feet.y + HEIGHT, feet.z + HALF_WIDTH).inflate(ROOM);
        return level.players().stream().filter(Player::isAlive).noneMatch(player -> player.getBoundingBox().intersects(box));
    }

    private static Vec3 dry(Level level, Vec3 near) {
        for (int reach = 0; reach <= REACH; reach++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int x = (int) Math.floor(near.x) + dx;
                    int z = (int) Math.floor(near.z) + dz;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == reach && !wet(level, x, z)) {
                        return new Vec3(x + 0.5, land(level, x, z) + 1, z + 0.5);
                    }
                }
            }
        }
        return new Vec3(near.x, land(level, (int) Math.floor(near.x), (int) Math.floor(near.z)) + 1, near.z);
    }
}
