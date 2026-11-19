package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Climate;
import io.github.roman16.wayfarer.HallStructure;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.WorldShape;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

final class HallSearch {
    private static final int AVOID_BLOCKS = 512;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_hall", Comparator.comparingLong(ChunkPos::toLong));
    private static final int RING_STEP = 128;
    private static final int[] WIDEST_DEGREES = {90, 180};
    private static final int ABOVE_SEA = 8;
    private static final int LOAD_RADIUS = 3;

    private HallSearch() {}

    record Found(BlockPos entrance, BoundingBox box, List<BoundingBox> rooms) {}

    private static Holder<Structure> holder(ServerLevel level, ResourceLocation hall) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, hall));
    }

    static CompletableFuture<Optional<BlockPos>> ground(ServerLevel level, ResourceLocation hall, BlockPos from, Vec3 way, int ahead, WorldShape.Coasts coasts,
            List<BlockPos> avoid) {
        HolderSet<Biome> fits = holder(level, hall).value().biomes();
        Climate climate = ((HallStructure) holder(level, hall).value()).climate();
        Unseen.Heights heights = new Unseen.Heights(level);
        List<Unseen.Viewer> viewers = Unseen.viewers(level);
        int view = level.getServer().getPlayerList().getViewDistance();
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        int reach = Balance.get().halls().reachBlocks();
        List<BlockPos> refused = List.copyOf(avoid);
        Predicate<BlockPos> clear = at -> refused.stream().allMatch(failed -> failed.distSqr(at.atY(failed.getY())) > (double) AVOID_BLOCKS * AVOID_BLOCKS);
        List<Predicate<Holder<Biome>>> passes = List.of(biome -> fits.contains(biome) && climate.fits(biome), climate::fits,
                biome -> fits.contains(biome) && climate.bears(biome), climate::bears);
        Vec3 flat = new Vec3(way.x, 0, way.z).normalize();
        return CompletableFuture.supplyAsync(() -> passes.stream().map(pass -> ahead(heights, from, flat, ahead, reach, coasts, sea, pass, clear, viewers, view))
                .flatMap(Optional::stream).findFirst(), Util.backgroundExecutor());
    }

    private static Optional<BlockPos> ahead(Unseen.Heights heights, BlockPos from, Vec3 way, int ahead, int reach, WorldShape.Coasts coasts, int sea,
            Predicate<Holder<Biome>> fits, Predicate<BlockPos> clear, List<Unseen.Viewer> viewers, int view) {
        int narrower = -1;
        for (int widest : WIDEST_DEGREES) {
            for (int distance = ahead; distance <= reach; distance += RING_STEP) {
                double turn = Math.toDegrees((double) RING_STEP / distance);
                for (double turned = 0; turned <= widest; turned += turn) {
                    for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                        if (turned <= narrower) {
                            continue;
                        }
                        Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                        int x = from.getX() + (int) Math.round(bent.x * distance);
                        int z = from.getZ() + (int) Math.round(bent.z * distance);
                        double angle = WorldShape.Coasts.angle(x, z);
                        double out = Math.sqrt((double) x * x + (double) z * z);
                        if (out < coasts.far(angle) || out > coasts.last(angle) || !clear.test(new BlockPos(x, 0, z))
                                || !fits.test(heights.biome(x, sea + ABOVE_SEA, z)) || heights.wet(x, z)) {
                            continue;
                        }
                        BlockPos site = new BlockPos(x, heights.surface(x, z), z);
                        if (Unseen.beyond(viewers, Vec3.atCenterOf(site), view)) {
                            return Optional.of(site);
                        }
                    }
                }
            }
            narrower = widest;
        }
        return Optional.empty();
    }

    static Found raise(ServerLevel level, ResourceLocation id, BlockPos site) {
        Places.Hall hall = Places.raise(level, id, site, false);
        return new Found(hall.threshold(), hall.box(), hall.rooms());
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
