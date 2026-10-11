package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import java.util.Comparator;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Ford {
    static final String NAME = "ford";
    static final String FAR = "ford_far";
    static final String OUTPOST = "ford_outpost";
    static final String WOUNDED = "corin";
    private static final String NEAR_BANK = "ford_near_bank";
    private static final String FAR_BANK = "ford_far_bank";
    private static final String CREEK = "ford_creek";
    private static final String WAY = "ford_way";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_ford", Comparator.comparingLong(ChunkPos::toLong));
    private static final int STEP = 16;
    private static final int SAMPLE = 4;
    private static final int WIDEST_SCAN = 64;
    private static final int BANK_STEP = 2;
    private static final int BANK_INLAND = 8;
    private static final int SIDE_STEP = 128;
    private static final int LOAD_RADIUS = 2;
    private static final int INLAND = 3;
    private static final int PATH = 6;
    private static final int OUTPOST_SEARCH = 16;
    private static final int OUTPOST_GRID = 8;
    private static final int CREEK_HIGHEST = 3;
    private static final int TRUNK = 24;
    private static final double BEND = 3;
    private static final double BEND_LENGTH = 9;
    private static final int BEND_PHASE = 7;
    private static final int NARROW = 5;
    private static final int ARRIVED = 2;
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final String SITTING = "sitting";
    private static boolean crossing;

    private record Banks(BlockPos near, BlockPos far, boolean creek) {}

    private Ford() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(NEAR_BANK).isPresent()) {
            return;
        }
        long started = System.nanoTime();
        ServerLevel level = Engine.overworld(server);
        BlockPos from = Caravan.last(server);
        Balance.Ford balance = Balance.get().ford();
        Marsh.find(server);
        BlockPos hall = progress.place(Marsh.HALL).orElseThrow();
        Vec3 way = new Vec3(hall.getX() - from.getX(), 0, hall.getZ() - from.getZ()).normalize();
        Unseen.Heights heights = new Unseen.Heights(level);
        Vec3 start = Vec3.atLowerCornerOf(from);
        Banks banks = river(heights, level, start, way, balance, true)
                .or(() -> dry(heights, level, start, way, balance, true))
                .or(() -> river(heights, level, start, way, balance, false))
                .or(() -> dry(heights, level, start, way, balance, false))
                .orElseGet(() -> creek(heights, start.add(way.scale(balance.aheadBlocks())), way, balance));
        BlockPos near = banks.near();
        BlockPos far = banks.far();
        boolean creek = banks.creek();
        progress.place(NEAR_BANK, near);
        progress.place(FAR_BANK, far);
        progress.yaws.put(WAY, (float) Math.toDegrees(Math.atan2(-way.x, way.z)));
        if (creek) {
            progress.place(CREEK, near);
        }
        progress.setDirty();
        LOGGER.info("the ford lies between {} and {}{}, found in {} ms", near, far, creek ? ", a creek the quest cuts" : ", across a river",
                (System.nanoTime() - started) / NANOS_PER_MILLI);
    }

    @Nullable
    private static BlockPos bank(Unseen.Heights heights, ServerLevel level, Vec3 at, Vec3 step, int widest, boolean warm) {
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        for (int along = BANK_STEP; along <= widest; along += BANK_STEP) {
            Vec3 there = at.add(step.scale(along));
            if (!heights.biome((int) Math.round(there.x), sea, (int) Math.round(there.z)).is(BiomeTags.IS_RIVER)) {
                for (int inland = 0; inland <= BANK_INLAND; inland += BANK_STEP) {
                    Vec3 dry = there.add(step.scale(inland));
                    if (land(heights, level, dry, warm)) {
                        return column(heights, dry);
                    }
                }
                return null;
            }
        }
        return null;
    }

    private static boolean land(Unseen.Heights heights, ServerLevel level, Vec3 at, boolean warm) {
        int x = (int) Math.round(at.x);
        int z = (int) Math.round(at.z);
        int y = heights.surface(x, z);
        Holder<Biome> biome = heights.biome(x, y, z);
        return !heights.wet(x, z) && !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER)
                && !biome.is(BiomeTags.IS_BEACH) && !(warm && Unseen.snowy(biome, new BlockPos(x, y, z)));
    }

    private static boolean flat(Unseen.Heights heights, Vec3 at, Vec3 way, int half) {
        Vec3 side = new Vec3(-way.z, 0, way.x);
        int middle = column(heights, at).getY();
        for (Vec3 there : new Vec3[] {at.add(way.scale(-half)), at.add(way.scale(half)), at.add(side.scale(half * 2)), at.add(side.scale(-half * 2))}) {
            if (Math.abs(column(heights, there).getY() - middle) > 1) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Banks> river(Unseen.Heights heights, ServerLevel level, Vec3 from, Vec3 way, Balance.Ford balance, boolean warm) {
        Vec3 side = new Vec3(-way.z, 0, way.x);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        for (int offset = 0; offset <= balance.sideBlocks(); offset += SIDE_STEP) {
            for (int sign : offset == 0 ? new int[] {1} : new int[] {1, -1}) {
                for (int ahead = balance.aheadBlocks(); ahead <= balance.searchBlocks(); ahead += SAMPLE) {
                    Vec3 at = from.add(way.scale(ahead)).add(side.scale(offset * sign));
                    int x = (int) Math.round(at.x);
                    int z = (int) Math.round(at.z);
                    Holder<Biome> river = heights.biome(x, sea, z);
                    if (!river.is(BiomeTags.IS_RIVER) || warm && Unseen.snowy(river, new BlockPos(x, sea, z))) {
                        continue;
                    }
                    BlockPos back = bank(heights, level, at, way.scale(-1), balance.widestRiverBlocks(), warm);
                    BlockPos front = bank(heights, level, at, way, balance.widestRiverBlocks(), warm);
                    if (back != null && front != null && back.distSqr(front) <= (double) balance.widestRiverBlocks() * balance.widestRiverBlocks()) {
                        return Optional.of(new Banks(back, front, false));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Banks> dry(Unseen.Heights heights, ServerLevel level, Vec3 from, Vec3 way, Balance.Ford balance, boolean warm) {
        int half = balance.widthBlocks() / 2 + 1;
        for (int back = 0; back < balance.aheadBlocks(); back += STEP) {
            for (int sign : back == 0 ? new int[] {1} : new int[] {-1, 1}) {
                Vec3 at = from.add(way.scale(balance.aheadBlocks() + back * sign));
                if (land(heights, level, at.add(way.scale(-half)), warm) && land(heights, level, at, warm) && land(heights, level, at.add(way.scale(half)), warm)
                        && flat(heights, at, way, half)) {
                    return Optional.of(creek(heights, at, way, balance));
                }
            }
        }
        return Optional.empty();
    }

    private static Banks creek(Unseen.Heights heights, Vec3 at, Vec3 way, Balance.Ford balance) {
        int half = balance.widthBlocks() / 2 + 1;
        return new Banks(column(heights, at.add(way.scale(-half))), column(heights, at.add(way.scale(half))), true);
    }

    private static BlockPos column(Unseen.Heights heights, Vec3 at) {
        int x = (int) Math.round(at.x);
        int z = (int) Math.round(at.z);
        return new BlockPos(x, heights.surface(x, z), z);
    }

    static boolean built(MinecraftServer server) {
        return Progress.of(server).place(NAME).isPresent();
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> near = progress.place(NEAR_BANK);
        Optional<BlockPos> far = progress.place(FAR_BANK);
        if (near.isPresent() && far.isPresent() && progress.place(NAME).isEmpty() && loaded(server, near.get(), far.get())) {
            build(server, near.get(), far.get());
        }
        watch(server);
    }

    private static boolean loaded(MinecraftServer server, BlockPos near, BlockPos far) {
        ServerLevel level = Engine.overworld(server);
        Vec3 way = way(server);
        BlockPos outpost = BlockPos.containing(Vec3.atLowerCornerOf(far).add(way.scale(Balance.get().ford().outpostBlocks())));
        boolean ready = true;
        for (BlockPos at : new BlockPos[] {near, far, outpost}) {
            ChunkPos chunk = new ChunkPos(at);
            level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
            for (int x = chunk.x - LOAD_RADIUS; x <= chunk.x + LOAD_RADIUS; x++) {
                for (int z = chunk.z - LOAD_RADIUS; z <= chunk.z + LOAD_RADIUS; z++) {
                    ready &= level.getChunkSource().getChunkNow(x, z) != null;
                }
            }
        }
        return ready;
    }

    private static void build(MinecraftServer server, BlockPos near, BlockPos far) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Balance.Ford balance = Balance.get().ford();
        Vec3 way = way(server);
        Vec3 side = new Vec3(-way.z, 0, way.x);
        if (progress.place(CREEK).isPresent()) {
            creek(level, near, far, way, side, balance);
        } else {
            BlockPos middle = new BlockPos((near.getX() + far.getX()) / 2, near.getY(), (near.getZ() + far.getZ()) / 2);
            near = shore(level, middle, way.scale(-1)).orElse(near);
            far = shore(level, middle, way).orElse(far);
            ford(level, near, far, way, side, balance);
        }
        path(level, near, way.scale(-1), side, balance);
        path(level, far, way, side, balance);
        BlockPos corin = ashore(level, near, way.scale(-1)).above();
        BlockPos gazebound = ashore(level, far, way).above();
        BlockPos outpost = outpost(level, far, way, balance);
        progress.place(FAR, gazebound);
        progress.place(OUTPOST, outpost);
        progress.place("seat_" + NAME, corin);
        progress.yaws.put("seat_" + NAME, yaw(Vec3.atCenterOf(corin), Vec3.atCenterOf(gazebound)));
        progress.place(NAME, corin);
        progress.setDirty();
        for (BlockPos at : new BlockPos[] {near, far, BlockPos.containing(Vec3.atLowerCornerOf(far).add(way.scale(balance.outpostBlocks())))}) {
            ChunkPos chunk = new ChunkPos(at);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        }
        LOGGER.info("the ford stands at {}, the Gazebound's outpost at {}", corin, outpost);
    }

    private static void ford(ServerLevel level, BlockPos near, BlockPos far, Vec3 way, Vec3 side, Balance.Ford balance) {
        int length = (int) Math.ceil(Math.sqrt(near.distSqr(far)));
        for (int along = -1; along <= length + 1; along++) {
            for (int across = -balance.widthBlocks() / 2; across < balance.widthBlocks() / 2; across++) {
                Vec3 at = Vec3.atLowerCornerOf(near).add(way.scale(along)).add(side.scale(across));
                int x = (int) Math.floor(at.x);
                int z = (int) Math.floor(at.z);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos surface = new BlockPos(x, top, z);
                if (!level.getFluidState(surface).isSource() || !level.getBlockState(surface).is(Blocks.WATER)) {
                    continue;
                }
                BlockPos bed = surface.below();
                while (bed.getY() > level.getMinBuildHeight() && !level.getFluidState(bed).isEmpty()) {
                    bed = bed.below();
                }
                for (BlockPos fill = bed.above(); fill.getY() < surface.getY(); fill = fill.above()) {
                    level.setBlock(fill, gravel(fill), FLAGS);
                }
            }
        }
    }

    private static void creek(ServerLevel level, BlockPos near, BlockPos far, Vec3 way, Vec3 side, Balance.Ford balance) {
        BlockPos middle = new BlockPos((near.getX() + far.getX()) / 2, 0, (near.getZ() + far.getZ()) / 2);
        int water = surface(level, middle).getY();
        for (int sign : new int[] {1, -1}) {
            for (int reach = 0; reach <= balance.creekReachBlocks(); reach++) {
                double bend = Math.sin((reach * sign + middle.getX() % BEND_PHASE) / BEND_LENGTH) * BEND;
                int half = balance.widthBlocks() / 2 - ((reach * BEND_PHASE + middle.getZ()) % NARROW == 0 ? 1 : 0);
                boolean open = true;
                for (int across = -half - 1; across <= half; across++) {
                    BlockPos top = surface(level, creekColumn(middle, way, side, reach * sign, bend + across, water));
                    open &= top.getY() >= water && top.getY() <= water + CREEK_HIGHEST;
                }
                if (!open) {
                    break;
                }
                for (int across = -half - 1; across <= half; across++) {
                    BlockPos column = creekColumn(middle, way, side, reach * sign, bend + across, water);
                    boolean bank = across == -half - 1 || across == half;
                    for (int y = column.getY() + 1; y <= column.getY() + TRUNK; y++) {
                        BlockPos above = column.atY(y);
                        BlockState there = level.getBlockState(above);
                        boolean low = y <= column.getY() + CREEK_HIGHEST + 1;
                        if (!bank && there.is(BlockTags.LOGS) || low && !there.isAir() && !there.is(BlockTags.LOGS) && (!bank || there.canBeReplaced())) {
                            level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                    }
                    if (!bank) {
                        level.setBlock(column, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(column.below(), gravel(column.below()), FLAGS);
                    } else {
                        level.setBlock(column, (column.getX() * 31 + column.getZ() * 17 & 1) == 0 ? Blocks.GRAVEL.defaultBlockState()
                                : Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    private static BlockPos creekColumn(BlockPos middle, Vec3 way, Vec3 side, int reach, double across, int water) {
        Vec3 at = Vec3.atLowerCornerOf(middle).add(side.scale(reach)).add(way.scale(across));
        return BlockPos.containing(at.x, water, at.z);
    }

    private static void path(ServerLevel level, BlockPos bank, Vec3 inland, Vec3 side, Balance.Ford balance) {
        for (int along = 0; along < PATH; along++) {
            for (int across = -balance.widthBlocks() / 4; across < balance.widthBlocks() / 4; across++) {
                BlockPos top = surface(level, BlockPos.containing(Vec3.atLowerCornerOf(bank).add(inland.scale(along)).add(side.scale(across))));
                if (!level.getBlockState(top).is(BlockTags.DIRT) && !level.getBlockState(top).is(Blocks.SAND)) {
                    continue;
                }
                level.setBlock(top, (top.getX() * 31 + top.getZ() * 17 & 3) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
                BlockState above = level.getBlockState(top.above());
                if (above.canBeReplaced() && above.getFluidState().isEmpty()) {
                    level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    private static Optional<BlockPos> shore(ServerLevel level, BlockPos middle, Vec3 step) {
        for (int along = 0; along <= WIDEST_SCAN; along++) {
            BlockPos top = surface(level, BlockPos.containing(Vec3.atLowerCornerOf(middle).add(step.scale(along))));
            if (level.getFluidState(top).isEmpty()) {
                return Optional.of(top);
            }
        }
        return Optional.empty();
    }

    private static BlockPos ashore(ServerLevel level, BlockPos bank, Vec3 inland) {
        for (int along = INLAND; along <= INLAND * INLAND * 2; along++) {
            BlockPos top = surface(level, BlockPos.containing(Vec3.atLowerCornerOf(bank).add(inland.scale(along))));
            if (level.getFluidState(top).isEmpty()) {
                return top;
            }
        }
        return surface(level, BlockPos.containing(Vec3.atLowerCornerOf(bank).add(inland.scale(INLAND))));
    }

    private static BlockState gravel(BlockPos at) {
        return (at.getX() * 7 + at.getZ() * 13 & 3) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    private static BlockPos outpost(ServerLevel level, BlockPos far, Vec3 way, Balance.Ford balance) {
        BlockPos ahead = BlockPos.containing(Vec3.atLowerCornerOf(far).add(way.scale(balance.outpostBlocks())));
        BlockPos best = surface(level, ahead);
        for (int dx = -OUTPOST_SEARCH; dx <= OUTPOST_SEARCH; dx += OUTPOST_GRID) {
            for (int dz = -OUTPOST_SEARCH; dz <= OUTPOST_SEARCH; dz += OUTPOST_GRID) {
                BlockPos top = surface(level, ahead.offset(dx, 0, dz));
                if (top.getY() > best.getY() && level.getFluidState(top).isEmpty()) {
                    best = top;
                }
            }
        }
        Optional<BoundingBox> raised = Errands.raise(level, Errands.kinds(level, Errands.OUTPOSTS), best).map(StructureStart::getBoundingBox);
        if (raised.isEmpty()) {
            LOGGER.info("no Gazebound outpost would stand at {}", best);
        }
        return raised.map(box -> new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ())).orElse(best);
    }

    private static BlockPos surface(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1, at.getZ());
        while (top.getY() > level.getMinBuildHeight() && level.getFluidState(top).isEmpty()
                && (level.getBlockState(top).is(BlockTags.LOGS) || level.getBlockState(top).canBeReplaced())) {
            top.move(0, -1, 0);
        }
        return top.immutable();
    }

    private static Vec3 way(MinecraftServer server) {
        return Vec3.directionFromRotation(0, Progress.of(server).yaws.getOrDefault(WAY, 0f));
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static void cross(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        Optional<Entity> body = People.find(server, WOUNDED);
        BlockPos bank = Engine.at(server, NAME);
        if (body.isPresent() && body.get().blockPosition().distSqr(bank) <= ARRIVED * ARRIVED) {
            return;
        }
        BlockPos start = Engine.at(server, FAR);
        if (body.isEmpty()) {
            People.person(level, WOUNDED, Vec3.atBottomCenterOf(start), yaw(Vec3.atCenterOf(start), Vec3.atCenterOf(bank)), "standing");
        }
        People.walk(server, WOUNDED, Vec3.atBottomCenterOf(bank));
        crossing = true;
    }

    static boolean crossed(MinecraftServer server) {
        return !crossing && !People.walking(WOUNDED) && People.find(server, WOUNDED).isPresent();
    }

    static void lay(MinecraftServer server) {
        crossing = false;
        Places.seat(server, NAME).ifPresent(seat -> People.person(Engine.overworld(server), WOUNDED, seat.at(), seat.yaw(), SITTING));
    }

    private static void watch(MinecraftServer server) {
        Optional<Entity> body = People.find(server, WOUNDED);
        if (crossing && body.isPresent() && !People.walking(WOUNDED)) {
            lay(server);
        }
    }

    static void reset() {
        crossing = false;
    }
}
