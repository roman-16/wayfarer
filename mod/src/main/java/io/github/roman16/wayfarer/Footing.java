package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Ground;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class Footing {
    private static final String ENTRANCE = "wayfarer:entrance";
    private static final String NAME = "name";
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int REACH = Beardifier.BEARD_KERNEL_RADIUS;
    private static final double SPREAD = 16;
    private static final int SAMPLE = 2;
    private static final int HEADROOM = 3;
    private static final Consumer<BlockPos> UNRECORDED = pos -> {};

    private Footing() {}

    public record Entrance(BlockPos at, Direction front) {
        BlockPos doorstep() {
            return at.relative(front);
        }
    }

    public static StructurePlaceSettings settings(Rotation rotation, BlockPos pivot) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).addProcessor(JigsawReplacementProcessor.INSTANCE);
    }

    public static List<Entrance> entrances(StructureTemplate template, StructurePlaceSettings settings, BlockPos origin) {
        return template.filterBlocks(origin, settings, Blocks.JIGSAW).stream()
                .filter(info -> info.nbt() != null && info.nbt().getString(NAME).equals(ENTRANCE))
                .map(info -> new Entrance(info.pos(), JigsawBlock.getFrontFacing(info.state())))
                .toList();
    }

    public static int ground(StructureTemplate template, StructurePlaceSettings settings, int layer) {
        return entrances(template, settings, BlockPos.ZERO).stream().findFirst().map(way -> way.at().getY()).orElse(layer);
    }

    public static BlockPos settle(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos corner, int layer) {
        List<Entrance> ways = entrances(template, settings, corner.atY(0));
        List<Integer> heights = new ArrayList<>();
        if (ways.isEmpty()) {
            columns(template.getBoundingBox(settings, corner.atY(0)), SAMPLE).forEach(column -> heights.add(Ground.land(level, column.getX(), column.getZ()) - layer));
        } else {
            ways.forEach(way -> heights.add(Ground.land(level, way.doorstep().getX(), way.doorstep().getZ()) - way.at().getY()));
        }
        heights.sort(Integer::compare);
        return corner.atY(heights.get(heights.size() / 2));
    }

    public static Optional<Integer> earth(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, int layer) {
        int floor = origin.getY() + ground(template, settings, layer);
        int moved = 0;
        for (BlockPos column : columns(template.getBoundingBox(settings, origin), SAMPLE)) {
            int land = Ground.land(level, column.getX(), column.getZ());
            if (!level.getFluidState(column.atY(land)).isEmpty()) {
                return Optional.empty();
            }
            moved += Math.abs(land - floor);
        }
        return Optional.of(moved);
    }

    public static void build(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, int layer) {
        build(level, template, settings, origin, layer, UNRECORDED);
    }

    public static void build(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, int layer, Consumer<BlockPos> before) {
        BoundingBox box = template.getBoundingBox(settings, origin);
        int floor = origin.getY() + ground(template, settings, layer);
        Changes changes = new Changes(level, before);
        int top = box.maxY();
        for (BlockPos column : columns(box, 1)) {
            int land = Ground.land(level, column.getX(), column.getZ());
            BlockState under = under(level, column.atY(land));
            for (int y = land + 1; y < floor; y++) {
                changes.set(column.atY(y), under);
            }
            int crown = Ground.crown(level, column.getX(), column.getZ());
            top = Math.max(top, crown);
            for (int y = floor + 1; y <= Math.max(box.maxY(), crown); y++) {
                if (loose(level.getBlockState(column.atY(y)))) {
                    changes.set(column.atY(y), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(changes::record);
        template.placeInWorld(level, origin, origin, settings, level.random, FLAGS);
        for (int x = box.minX() - REACH; x <= box.maxX() + REACH; x++) {
            for (int z = box.minZ() - REACH; z <= box.maxZ() + REACH; z++) {
                int dx = Math.max(Math.max(box.minX() - x, x - box.maxX()), 0);
                int dz = Math.max(Math.max(box.minZ() - z, z - box.maxZ()), 0);
                int distance = dx * dx + dz * dz;
                if (distance == 0 || distance > REACH * REACH) {
                    continue;
                }
                int land = Ground.land(level, x, z);
                double weight = Math.exp(-distance / SPREAD);
                reshape(level, changes, new BlockPos(x, land, z), (int) Math.round(land + (floor - land) * weight));
            }
        }
        for (Entrance way : entrances(template, settings, origin)) {
            approach(level, changes, box, way.at().atY(floor), way.front());
        }
        prune(level, changes, new BlockPos(box.minX() - REACH, floor - REACH, box.minZ() - REACH), new BlockPos(box.maxX() + REACH, top, box.maxZ() + REACH));
    }

    public static void approach(ServerLevel level, BlockPos threshold, Direction front) {
        approach(level, new Changes(level, UNRECORDED), null, threshold, front);
    }

    private static void approach(ServerLevel level, Changes changes, BoundingBox box, BlockPos threshold, Direction front) {
        int floor = threshold.getY();
        int last = floor;
        for (int step = 1; step <= Balance.get().footing().approachBlocks(); step++) {
            BlockPos centre = threshold.relative(front, step);
            if (box != null && box.isInside(centre.atY(box.minY()))) {
                continue;
            }
            int land = Ground.land(level, centre.getX(), centre.getZ());
            if (!level.getFluidState(centre.atY(land)).isEmpty()) {
                return;
            }
            int target = Mth.clamp(land, last - 1, last + 1);
            for (int side = -1; side <= 1; side++) {
                BlockPos column = centre.relative(front.getClockWise(), side);
                if (box != null && box.isInside(column.atY(box.minY()))) {
                    continue;
                }
                reshape(level, changes, column.atY(Ground.land(level, column.getX(), column.getZ())), target);
                for (int y = target + 1; y <= target + HEADROOM; y++) {
                    BlockPos head = column.atY(y);
                    if (natural(level.getBlockState(head)) || loose(level.getBlockState(head))) {
                        changes.set(head, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            last = target;
        }
    }

    private static void reshape(ServerLevel level, Changes changes, BlockPos land, int target) {
        BlockState surface = level.getBlockState(land);
        if (target == land.getY() || !natural(surface) || !level.getFluidState(land).isEmpty()) {
            return;
        }
        for (int y = land.getY() + 1; y <= Ground.crown(level, land.getX(), land.getZ()); y++) {
            if (loose(level.getBlockState(land.atY(y)))) {
                changes.set(land.atY(y), Blocks.AIR.defaultBlockState());
            }
        }
        if (target > land.getY()) {
            BlockState under = under(level, land);
            for (int y = land.getY(); y < target; y++) {
                changes.set(land.atY(y), under);
            }
            changes.set(land.atY(target), surface);
            return;
        }
        for (int y = target + 1; y < land.getY(); y++) {
            if (!natural(level.getBlockState(land.atY(y)))) {
                return;
            }
        }
        for (int y = target + 1; y <= land.getY(); y++) {
            changes.set(land.atY(y), Blocks.AIR.defaultBlockState());
        }
        changes.set(land.atY(target), surface);
    }

    private static BlockState under(ServerLevel level, BlockPos land) {
        BlockState below = level.getBlockState(land.below());
        if (natural(below)) {
            return below;
        }
        BlockState surface = level.getBlockState(land);
        return natural(surface) && !surface.is(BlockTags.DIRT) ? surface : Blocks.DIRT.defaultBlockState();
    }

    private static List<BlockPos> columns(BoundingBox box, int step) {
        List<BlockPos> columns = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x += step) {
            for (int z = box.minZ(); z <= box.maxZ(); z += step) {
                columns.add(new BlockPos(x, 0, z));
            }
        }
        return columns;
    }

    public static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CLAY)
                || state.is(Blocks.DIRT_PATH);
    }

    private static boolean loose(BlockState state) {
        return !state.isAir() && (Ground.cover(state) || natural(state));
    }

    public static void prune(ServerLevel level, BlockPos low, BlockPos high) {
        prune(level, new Changes(level, UNRECORDED), low, high);
    }

    private static void prune(ServerLevel level, Changes changes, BlockPos low, BlockPos high) {
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
                if (!held.containsKey(next) && inside(next, outerLow, outerHigh) && leaf(level.getBlockState(next))) {
                    held.put(next, distance);
                    frontier.add(next);
                }
            }
        }
        for (BlockPos at : BlockPos.betweenClosed(low, high)) {
            if (leaf(level.getBlockState(at)) && !held.containsKey(at)) {
                changes.set(at.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
    }

    public static boolean leaf(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean inside(BlockPos at, BlockPos low, BlockPos high) {
        return at.getX() >= low.getX() && at.getX() <= high.getX() && at.getY() >= low.getY() && at.getY() <= high.getY()
                && at.getZ() >= low.getZ() && at.getZ() <= high.getZ();
    }

    private static final class Changes {
        private final ServerLevel level;
        private final Consumer<BlockPos> before;
        private final Set<BlockPos> recorded = new HashSet<>();

        private Changes(ServerLevel level, Consumer<BlockPos> before) {
            this.level = level;
            this.before = before;
        }

        void record(BlockPos pos) {
            if (recorded.add(pos.immutable())) {
                before.accept(pos.immutable());
            }
        }

        void set(BlockPos pos, BlockState state) {
            if (level.getBlockState(pos).equals(state)) {
                return;
            }
            record(pos);
            level.setBlock(pos, state, FLAGS);
        }
    }
}
