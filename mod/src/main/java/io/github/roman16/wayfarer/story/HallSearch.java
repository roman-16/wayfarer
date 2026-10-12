package io.github.roman16.wayfarer.story;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.EntrancePiece;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class HallSearch {
    private static final int AVOID_BLOCKS = 512;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_hall", Comparator.comparingLong(ChunkPos::toLong));
    private static final int RINGS = 4;
    private static final int RING_STEP = 256;
    private static final int ABOVE_SEA = 8;
    private static final int LOAD_RADIUS = 3;

    private HallSearch() {}

    record Found(BlockPos entrance, BoundingBox box) {}

    static Optional<Found> nearest(ServerLevel level, ResourceLocation id, BlockPos from) {
        Holder<Structure> hall = holder(level, id);
        long started = System.nanoTime();
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(hall), from, RINGS,
                false);
        LOGGER.info("the search for the nearest {} from {} took {} ms", hall, from.toShortString(), (System.nanoTime() - started) / 1_000_000);
        int reach = Balance.get().halls().reachBlocks();
        if (found == null || found.getFirst().atY(from.getY()).distSqr(from) > (double) reach * reach) {
            return Optional.empty();
        }
        ChunkPos chunk = new ChunkPos(found.getFirst());
        StructureStart start = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(hall.value());
        if (start == null || !start.isValid()) {
            return Optional.empty();
        }
        BoundingBox entrance = start.getPieces().stream().filter(piece -> piece instanceof EntrancePiece).map(StructurePiece::getBoundingBox).findFirst()
                .orElse(start.getBoundingBox());
        return Optional.of(new Found(new BlockPos(entrance.getCenter().getX(), entrance.maxY(), entrance.getCenter().getZ()), start.getBoundingBox()));
    }

    private static Holder<Structure> holder(ServerLevel level, ResourceLocation hall) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, hall));
    }

    static CompletableFuture<Optional<BlockPos>> ground(ServerLevel level, ResourceLocation hall, BlockPos from, int inner, int outer, Optional<BlockPos> avoid) {
        HolderSet<Biome> fits = holder(level, hall).value().biomes();
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        Balance.Halls halls = Balance.get().halls();
        Vec3 flat = new Vec3(from.getX(), 0, from.getZ());
        Vec3 origin = flat.length() >= inner || flat.lengthSqr() < 1 ? flat : flat.normalize().scale(inner);
        Predicate<BlockPos> clear = at -> avoid.map(failed -> failed.distSqr(at.atY(failed.getY())) > (double) AVOID_BLOCKS * AVOID_BLOCKS).orElse(true);
        return CompletableFuture.supplyAsync(() -> sample(heights, origin, halls, inner, outer, sea, biome -> fits.contains(biome), clear)
                .or(() -> sample(heights, origin, halls, inner, outer, sea, biome -> true, clear)), Util.backgroundExecutor());
    }

    private static Optional<BlockPos> sample(Unseen.Heights heights, Vec3 origin, Balance.Halls halls, int inner, int outer, int sea,
            Predicate<Holder<Biome>> fits, Predicate<BlockPos> clear) {
        for (int distance = halls.nearestBlocks(); distance <= halls.reachBlocks(); distance += RING_STEP) {
            int around = (int) Math.ceil(2 * Math.PI * distance / RING_STEP);
            for (int index = 0; index < around; index++) {
                double angle = 2 * Math.PI * index / around;
                int x = (int) Math.round(origin.x + Math.cos(angle) * distance);
                int z = (int) Math.round(origin.z + Math.sin(angle) * distance);
                double out = Math.sqrt((double) x * x + (double) z * z);
                if (out >= inner && out <= outer && clear.test(new BlockPos(x, 0, z)) && fits.test(heights.biome(x, sea + ABOVE_SEA, z)) && !heights.wet(x, z)) {
                    return Optional.of(new BlockPos(x, heights.surface(x, z), z));
                }
            }
        }
        return Optional.empty();
    }

    static Found raise(ServerLevel level, ResourceLocation id, BlockPos site) {
        Places.Hall hall = Places.raise(level, id, site);
        return new Found(hall.stair(), hall.box());
    }

    static boolean loaded(ServerLevel level, BlockPos site) {
        ChunkPos chunk = new ChunkPos(site);
        level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        for (int x = chunk.x - LOAD_RADIUS + 1; x < chunk.x + LOAD_RADIUS; x++) {
            for (int z = chunk.z - LOAD_RADIUS + 1; z < chunk.z + LOAD_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    static void release(ServerLevel level, BlockPos site) {
        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(site), LOAD_RADIUS, new ChunkPos(site));
    }

    static Optional<BlockPos> stone(ServerLevel level, BlockPos low, BlockPos high) {
        return BlockPos.betweenClosedStream(low, high).filter(pos -> level.getBlockState(pos).is(Wayfarer.CALLING_STONE)).findFirst().map(BlockPos::immutable);
    }
}
