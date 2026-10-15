package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Tents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

final class Unseen {
    private static final int CELL = 8;
    private static final double EYE_HEIGHT = 1.62;
    private static final int SIGHT_NEAR = 4;
    private static final int SIGHT_OWN = 16;
    private static final int SIGHT_STEP = 8;
    private static final int WARM_FROM = 256;
    private static final int WARM_STEP = 64;
    private static final float SNOWING = 0.15f;

    private Unseen() {}

    record Viewer(Vec3 eye, Vec3 look, ChunkPos chunk) {}

    static List<Viewer> viewers(ServerLevel level) {
        List<Viewer> viewers = new ArrayList<>(level.players().stream().filter(player -> !player.isSpectator())
                .map(player -> new Viewer(player.getEyePosition(), Vec3.directionFromRotation(0, player.getYRot()), player.chunkPosition())).toList());
        level.getServer().getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator())
                .flatMap(player -> Tents.outside(player).filter(outside -> outside.level() == level).stream())
                .forEach(outside -> viewers.add(new Viewer(outside.at().add(0, EYE_HEIGHT, 0), Vec3.directionFromRotation(0, outside.yaw()),
                        new ChunkPos(BlockPos.containing(outside.at())))));
        return viewers;
    }

    static boolean beyond(List<Viewer> viewers, Vec3 target, int view) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(target));
        return viewers.stream().noneMatch(viewer -> ChunkTrackingView.isWithinDistance(viewer.chunk().x, viewer.chunk().z, view, chunk.x, chunk.z, true));
    }

    static boolean hidden(Heights heights, List<Viewer> viewers, List<Vec3> targets, int view, boolean beyondView) {
        for (Viewer viewer : viewers) {
            for (Vec3 target : targets) {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(target));
                boolean within = ChunkTrackingView.isWithinDistance(viewer.chunk().x, viewer.chunk().z, view, chunk.x, chunk.z, true);
                if (beyondView && within) {
                    return false;
                }
                Vec3 towards = new Vec3(target.x - viewer.eye().x, 0, target.z - viewer.eye().z).normalize();
                if (towards.dot(viewer.look()) >= 0 && !behindLand(heights, viewer.eye(), target)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean behindLand(Heights heights, Vec3 eye, Vec3 target) {
        Vec3 line = target.subtract(eye);
        double length = line.length();
        Vec3 step = line.scale(1 / length);
        for (double travelled = SIGHT_NEAR; travelled < length - SIGHT_OWN; travelled += SIGHT_STEP) {
            Vec3 point = eye.add(step.scale(travelled));
            if (heights.surface(Mth.floor(point.x), Mth.floor(point.z)) > point.y) {
                return true;
            }
        }
        return false;
    }

    static boolean warm(Heights heights, BlockPos from, Vec3 way, int length) {
        for (int along = WARM_FROM; along <= length; along += WARM_STEP) {
            int x = (int) Math.round(from.getX() + way.x * along);
            int z = (int) Math.round(from.getZ() + way.z * along);
            int y = heights.surface(x, z);
            if (snowy(heights.biome(x, y, z), new BlockPos(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    static boolean snowy(Holder<Biome> biome, BlockPos at) {
        return biome.is(Tags.Biomes.IS_SNOWY) || biome.is(Tags.Biomes.IS_ICY) || biome.value().getBaseTemperature() < SNOWING || biome.value().coldEnoughToSnow(at);
    }

    static final class Heights {
        private final ChunkGenerator generator;
        private final LevelHeightAccessor height;
        private final RandomState random;
        private final Map<Long, Integer> surfaces = new HashMap<>();
        private final Map<Long, Boolean> wet = new HashMap<>();

        Heights(ServerLevel level) {
            this.generator = level.getChunkSource().getGenerator();
            this.height = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
            this.random = level.getChunkSource().randomState();
        }

        synchronized int surface(int x, int z) {
            int cellX = Math.floorDiv(x, CELL) * CELL;
            int cellZ = Math.floorDiv(z, CELL) * CELL;
            return surfaces.computeIfAbsent(BlockPos.asLong(cellX, 0, cellZ),
                    key -> generator.getBaseHeight(cellX, cellZ, Heightmap.Types.WORLD_SURFACE_WG, height, random) - 1);
        }

        Holder<Biome> biome(int x, int y, int z) {
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
        }

        synchronized boolean wet(int x, int z) {
            int cellX = Math.floorDiv(x, CELL) * CELL;
            int cellZ = Math.floorDiv(z, CELL) * CELL;
            return wet.computeIfAbsent(BlockPos.asLong(cellX, 0, cellZ),
                    key -> generator.getBaseHeight(cellX, cellZ, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1 < surface(cellX, cellZ));
        }
    }
}
