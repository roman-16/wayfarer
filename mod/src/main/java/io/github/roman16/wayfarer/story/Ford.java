package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    static final String WOUNDED = "cadoc";
    private static final String NEAR_BANK = "ford_near_bank";
    private static final String FAR_BANK = "ford_far_bank";
    private static final String WAY = "ford_way";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_ford", Comparator.comparingLong(ChunkPos::toLong));
    private static final int STEP = 16;
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
    private static final int FIRE_BESIDE = 3;
    private static final int ARRIVED = 2;
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final String SITTING = "sitting";
    private static boolean crossing;

    private record Banks(BlockPos near, BlockPos far) {}

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
        Vec3 toward = new Vec3(hall.getX() - from.getX(), 0, hall.getZ() - from.getZ()).normalize();
        Unseen.Heights heights = new Unseen.Heights(level);
        int ahead = balance.aheadBlocks();
        Vec3 way = Unseen.warm(heights, from, toward, ahead) ? toward
                : Home.aim(from, toward, ahead, (origin, bent) -> Unseen.warm(heights, origin, bent, ahead)).way();
        Vec3 start = Vec3.atLowerCornerOf(from);
        Banks banks = dry(heights, start, way, balance, true)
                .or(() -> dry(heights, start, way, balance, false))
                .orElseGet(() -> creek(heights, start.add(way.scale(balance.aheadBlocks())), way, balance));
        progress.place(NEAR_BANK, banks.near());
        progress.place(FAR_BANK, banks.far());
        progress.yaws.put(WAY, (float) Math.toDegrees(Math.atan2(-way.x, way.z)));
        progress.setDirty();
        LOGGER.info("the ford lies between {} and {}, the way turned {} degrees from the hall's, found in {} ms", banks.near(), banks.far(),
                Home.degrees(toward, way), (System.nanoTime() - started) / NANOS_PER_MILLI);
    }

    private static boolean land(Unseen.Heights heights, Vec3 at, boolean warm) {
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

    private static Optional<Banks> dry(Unseen.Heights heights, Vec3 from, Vec3 way, Balance.Ford balance, boolean warm) {
        int half = balance.widthBlocks() / 2 + 1;
        for (int back = 0; back < balance.aheadBlocks(); back += STEP) {
            for (int sign : back == 0 ? new int[] {1} : new int[] {-1, 1}) {
                Vec3 at = from.add(way.scale(balance.aheadBlocks() + back * sign));
                if (land(heights, at.add(way.scale(-half)), warm) && land(heights, at, warm) && land(heights, at.add(way.scale(half)), warm)
                        && flat(heights, at, way, half)) {
                    return Optional.of(creek(heights, at, way, balance));
                }
            }
        }
        return Optional.empty();
    }

    private static Banks creek(Unseen.Heights heights, Vec3 at, Vec3 way, Balance.Ford balance) {
        int half = balance.widthBlocks() / 2 + 1;
        return new Banks(column(heights, at.add(way.scale(-half))), column(heights, at.add(way.scale(half))));
    }

    private static BlockPos column(Unseen.Heights heights, Vec3 at) {
        int x = (int) Math.round(at.x);
        int z = (int) Math.round(at.z);
        return new BlockPos(x, heights.surface(x, z), z);
    }

    static void unbuild(MinecraftServer server) {
        Progress.of(server).places.remove(NAME);
        Progress.of(server).setDirty();
    }

    static void forget(MinecraftServer server) {
        Progress progress = Progress.of(server);
        List.of(NEAR_BANK, FAR_BANK, NAME, FAR, OUTPOST, "seat_" + NAME).forEach(progress.places::remove);
        progress.yaws.remove(WAY);
        progress.yaws.remove("seat_" + NAME);
        progress.witnesses.remove(NAME);
        progress.witnesses.remove(OUTPOST);
        progress.setDirty();
        Marsh.forget(server);
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
        creek(level, near, far, way, side, balance);
        path(level, near, way.scale(-1), side, balance);
        path(level, far, way, side, balance);
        BlockPos cadoc = ashore(level, near, way.scale(-1)).above();
        BlockPos gazebound = ashore(level, far, way).above();
        BlockPos outpost = outpost(level, far, way, balance);
        if (Unseen.snowy(level.getBiome(near), near)) {
            fire(level, cadoc, side);
        }
        progress.place(FAR, gazebound);
        progress.place(OUTPOST, outpost);
        progress.place("seat_" + NAME, cadoc);
        progress.yaws.put("seat_" + NAME, yaw(Vec3.atCenterOf(cadoc), Vec3.atCenterOf(gazebound)));
        progress.place(NAME, cadoc);
        progress.setDirty();
        for (BlockPos at : new BlockPos[] {near, far, BlockPos.containing(Vec3.atLowerCornerOf(far).add(way.scale(balance.outpostBlocks())))}) {
            ChunkPos chunk = new ChunkPos(at);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        }
        Standing.keep(server, NAME);
        Standing.keep(server, OUTPOST);
        LOGGER.info("the ford stands at {}, the Gazebound's outpost at {}", cadoc, outpost);
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

    private static BlockPos ashore(ServerLevel level, BlockPos bank, Vec3 inland) {
        for (int along = INLAND; along <= INLAND * INLAND * 2; along++) {
            BlockPos top = surface(level, BlockPos.containing(Vec3.atLowerCornerOf(bank).add(inland.scale(along))));
            if (level.getFluidState(top).isEmpty()) {
                return top;
            }
        }
        return surface(level, BlockPos.containing(Vec3.atLowerCornerOf(bank).add(inland.scale(INLAND))));
    }

    private static void fire(ServerLevel level, BlockPos cadoc, Vec3 side) {
        for (int sign : new int[] {1, -1}) {
            BlockPos ground = surface(level, BlockPos.containing(Vec3.atLowerCornerOf(cadoc).add(side.scale(FIRE_BESIDE * sign))));
            if (level.getFluidState(ground).isEmpty() && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                level.setBlock(ground.above(), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
                LOGGER.info("the ford lies in the cold; a fire burns on its near bank at {}", ground.above());
                return;
            }
        }
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
