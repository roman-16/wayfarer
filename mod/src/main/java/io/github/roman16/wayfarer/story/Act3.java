package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

final class Act3 {
    static final String HOLLOW = "hollow";
    static final String KEEPERS = "keepers";
    private static final String KEEPERS_SITE = "keepers_site";
    static final String KEEPERS_FRAME = "keepers_frame";
    private static final String FRAME_MARKED = "keepers_frame_marked";
    private static final String FRAME_CLUE = "frame";
    private static final List<ResourceLocation> STRONGHOLDS = List.of(ResourceLocation.fromNamespaceAndPath("betterstrongholds", "stronghold"));
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("endrem", "ancient_portal_frame");
    private static final int FRAME_SEARCH = 2;
    private static final int FRAME_REACH = 10;
    private static final int RING = 4;
    private static final int FLOOR_REACH = 1;
    private static final double EYE = 1.6;
    private static final String HOLLOW_MARKED = "hollow_marked";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] WOUNDED = {"wounded_1", "wounded_2", "wounded_3"};
    private static final String[] GRAVES = {"grave_1", "grave_2", "grave_3"};
    private static final int WOUNDED_RING = 5;
    private static final int GRAVE_RING = 9;
    private static final int OPEN_TURNS = 4;
    private static final double OPEN_STEP = 16;

    private Act3() {}

    static void find(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        BlockPos from = Caravan.last(server);
        switch (name) {
            case HOLLOW -> {
                if (progress.place(HOLLOW).isEmpty()) {
                    progress.place(HOLLOW, from);
                    progress.setDirty();
                }
            }
            case KEEPERS -> {
                if (progress.place(KEEPERS_SITE).isEmpty()) {
                    Optional<BlockPos> found = ring(level, from);
                    if (found.isEmpty()) {
                        LOGGER.error("the Keepers' door failed: the world has no ring of strongholds");
                        return;
                    }
                    progress.place(KEEPERS_SITE, found.get());
                    progress.place(KEEPERS, found.get());
                    progress.setDirty();
                    LOGGER.info("the Keepers wait in the stronghold at {}", found.get());
                }
            }
            default -> throw new IllegalStateException("act 3 has no place " + name);
        }
    }

    static void unbuild(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        switch (name) {
            case HOLLOW -> progress.places.remove(HOLLOW_MARKED);
            case KEEPERS -> List.of(KEEPERS_FRAME, FRAME_MARKED).forEach(progress.places::remove);
            default -> throw new IllegalStateException("act 3 has no place " + name);
        }
        progress.setDirty();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return switch (name) {
            case HOLLOW -> progress.place(HOLLOW_MARKED).isPresent();
            case KEEPERS -> progress.place(KEEPERS_FRAME).isPresent();
            default -> false;
        };
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        progress.place(HOLLOW).filter(centre -> progress.place(HOLLOW_MARKED).isEmpty() && HallSearch.loaded(level, centre)).ifPresent(centre -> {
            mark(server, level, centre);
            HallSearch.release(level, centre);
        });
        progress.place(KEEPERS_FRAME).filter(frame -> progress.place(FRAME_MARKED).isEmpty() && level.isLoaded(frame)).ifPresent(frame -> {
            Clues.marker(level, frame.above(), FRAME_CLUE);
            progress.place(FRAME_MARKED, frame);
            progress.setDirty();
        });
        progress.place(KEEPERS_SITE).filter(site -> progress.place(KEEPERS_FRAME).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            frame(server, level, site);
            HallSearch.release(level, site);
        });
    }

    private static void mark(MinecraftServer server, ServerLevel level, BlockPos centre) {
        Progress progress = Progress.of(server);
        for (int index = 0; index < WOUNDED.length; index++) {
            Clues.marker(level, open(server, level, centre, WOUNDED_RING, index * 120.0 + 30), WOUNDED[index]);
        }
        for (int index = 0; index < GRAVES.length; index++) {
            BlockPos grave = open(server, level, centre, GRAVE_RING, index * 120.0 + 90);
            level.setBlock(grave, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(grave.above(), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            Clues.marker(level, grave.above(), GRAVES[index]);
        }
        progress.place(HOLLOW_MARKED, centre);
        progress.setDirty();
        Standing.keep(server, HOLLOW);
        LOGGER.info("the hollow's wounded and dead lie round {}", centre);
    }

    private static Optional<BlockPos> ring(ServerLevel level, BlockPos from) {
        List<Holder<Structure>> kinds = strongholds(level).stream().toList();
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).stream()
                .filter(set -> set.structures().stream().anyMatch(entry -> kinds.contains(entry.structure())))
                .map(StructureSet::placement).filter(placement -> placement instanceof ConcentricRingsStructurePlacement)
                .flatMap(placement -> Optional.ofNullable(level.getChunkSource().getGeneratorState().getRingPositionsFor((ConcentricRingsStructurePlacement) placement))
                        .orElse(List.of()).stream())
                .map(chunk -> chunk.getMiddleBlockPosition(from.getY()))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(from)));
    }

    private static HolderSet<Structure> strongholds(ServerLevel level) {
        return HolderSet.direct(STRONGHOLDS.stream().flatMap(id -> level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(id).stream())
                .map(holder -> (Holder<Structure>) holder).toList());
    }

    private static void frame(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> frame = Optional.empty();
        Optional<BoundingBox> room = Optional.empty();
        for (Holder<Structure> kind : strongholds(level)) {
            StructureStart start = level.getChunk(site.getX() >> 4, site.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(kind.value());
            if (start != null && start.isValid()) {
                BoundingBox box = start.getBoundingBox();
                ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).forEach(chunk -> level.getChunk(chunk.x, chunk.z));
                frame = BlockPos.betweenClosedStream(box).filter(pos -> framing(level.getBlockState(pos))).findFirst().map(BlockPos::immutable);
                room = frame.flatMap(found -> start.getPieces().stream().map(StructurePiece::getBoundingBox).filter(piece -> piece.isInside(found))
                        .min(Comparator.comparingLong(piece -> (long) piece.getXSpan() * piece.getYSpan() * piece.getZSpan())));
                break;
            }
        }
        BlockPos at = frame.orElse(site);
        BoundingBox near = new BoundingBox(at.getX() - FRAME_REACH, at.getY() - FRAME_SEARCH, at.getZ() - FRAME_REACH, at.getX() + FRAME_REACH,
                at.getY() + FRAME_SEARCH * 2, at.getZ() + FRAME_REACH);
        clear(level, room.flatMap(piece -> BoundingBox.encapsulatingBoxes(List.of(piece, near))).orElse(near));
        keep(level, at);
        BlockPos seat = seat(level, at);
        progress.place(KEEPERS_FRAME, at);
        progress.place(KEEPERS, seat);
        progress.place("seat_keepers_oldest", seat);
        progress.yaws.put("seat_keepers_oldest", Frost.yaw(Vec3.atLowerCornerOf(at.subtract(seat)).multiply(1, 0, 1)));
        progress.setDirty();
        LOGGER.info("the Keepers' frame is at {}{}", at, frame.isPresent() ? "" : " (no frame block found; the stronghold's middle)");
        Journal.syncAll(server);
    }

    public static boolean quiet(ServerLevel level, BlockPos pos) {
        int reach = Balance.get().gate().quietBlocks();
        return level.dimension() == Level.OVERWORLD && Progress.of(level.getServer()).place(KEEPERS_FRAME).filter(frame -> frame.closerThan(pos, reach)).isPresent();
    }

    private static void keep(ServerLevel level, BlockPos frame) {
        int reach = Balance.get().gate().quietBlocks();
        int spawners = 0;
        for (BlockPos pos : BlockPos.betweenClosed(frame.offset(-reach, -reach, -reach), frame.offset(reach, reach, reach))) {
            if (level.getBlockState(pos).is(Blocks.SPAWNER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                spawners++;
            }
        }
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, new AABB(frame).inflate(reach), Monster::isAlive);
        monsters.forEach(Entity::discard);
        LOGGER.info("the Keepers keep their hall: {} spawners and {} monsters gone within {} blocks of the frame", spawners, monsters.size(), reach);
    }

    private static boolean framing(BlockState state) {
        return state.is(Blocks.END_PORTAL_FRAME) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(FRAME);
    }

    static Vec3 centre(ServerLevel level, BlockPos frame) {
        double x = 0;
        double z = 0;
        int count = 0;
        for (BlockPos at : BlockPos.betweenClosed(frame.offset(-RING, 0, -RING), frame.offset(RING, 0, RING))) {
            if (framing(level.getBlockState(at))) {
                x += at.getX() + 0.5;
                z += at.getZ() + 0.5;
                count++;
            }
        }
        return count == 0 ? Vec3.atBottomCenterOf(frame) : new Vec3(x / count, frame.getY(), z / count);
    }

    private static BlockPos seat(ServerLevel level, BlockPos frame) {
        List<Predicate<BlockPos>> wishes = List.of(
                pos -> floor(level, pos) && apart(pos, frame) && sees(level, pos, frame),
                pos -> Ground.standable(level, Vec3.atBottomCenterOf(pos)) && apart(pos, frame) && sees(level, pos, frame),
                pos -> Ground.standable(level, Vec3.atBottomCenterOf(pos)) && apart(pos, frame),
                pos -> Ground.standable(level, Vec3.atBottomCenterOf(pos)));
        for (Predicate<BlockPos> wish : wishes) {
            Optional<BlockPos> found = BlockPos.betweenClosedStream(frame.offset(-FRAME_REACH, -FRAME_SEARCH, -FRAME_REACH), frame.offset(FRAME_REACH, FRAME_SEARCH * 2, FRAME_REACH))
                    .filter(wish).map(BlockPos::immutable).min(Comparator.comparingDouble(pos -> pos.distSqr(frame)));
            if (found.isPresent()) {
                return found.get();
            }
        }
        LOGGER.warn("no free floor within {} blocks of the Keepers' frame at {}", FRAME_REACH, frame);
        return frame.above();
    }

    private static boolean apart(BlockPos pos, BlockPos frame) {
        return Math.abs(pos.getX() - frame.getX()) + Math.abs(pos.getZ() - frame.getZ()) >= FRAME_SEARCH * 2;
    }

    private static void clear(ServerLevel level, BoundingBox room) {
        BoundingBox wall = room.inflatedBy(1);
        int drained = 0;
        int spawners = 0;
        for (BlockPos pos : BlockPos.betweenClosed(wall.minX(), wall.minY(), wall.minZ(), wall.maxX(), wall.maxY(), wall.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (room.isInside(pos) && state.is(Blocks.SPAWNER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                spawners++;
                continue;
            }
            if (level.getFluidState(pos).isEmpty()) {
                continue;
            }
            if (!room.isInside(pos)) {
                level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            drained++;
        }
        if (drained > 0 || spawners > 0) {
            LOGGER.info("the Keepers' frame room was cleared of {} blocks of water and {} spawners", drained, spawners);
        }
    }

    private static boolean sees(ServerLevel level, BlockPos from, BlockPos frame) {
        BlockHitResult hit = level.clip(new ClipContext(Vec3.atBottomCenterOf(from).add(0, EYE, 0), Vec3.atCenterOf(frame.above()), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().closerThan(frame, FRAME_SEARCH);
    }

    private static boolean floor(ServerLevel level, BlockPos pos) {
        return BlockPos.betweenClosedStream(pos.offset(-FLOOR_REACH, 0, -FLOOR_REACH), pos.offset(FLOOR_REACH, 0, FLOOR_REACH)).allMatch(near -> Ground.standable(level, Vec3.atBottomCenterOf(near)));
    }

    private static BlockPos around(BlockPos centre, int ring, double degrees) {
        double angle = Math.toRadians(degrees);
        return centre.offset((int) Math.round(Math.cos(angle) * ring), 0, (int) Math.round(Math.sin(angle) * ring));
    }

    private static BlockPos open(MinecraftServer server, ServerLevel level, BlockPos centre, int ring, double degrees) {
        List<BoundingBox> camp = Caravan.boxes(server);
        for (int turn = 0; turn <= OPEN_TURNS; turn++) {
            for (int side : turn == 0 ? new int[] {1} : new int[] {1, -1}) {
                for (int out = 0; out <= ring; out += WOUNDED_RING) {
                    BlockPos spot = Ground.land(level, around(centre, ring + out, degrees + side * turn * OPEN_STEP));
                    if (camp.stream().noneMatch(box -> box.inflatedBy(1).isInside(spot)) && !level.getBlockState(spot).is(BlockTags.WOOL)
                            && level.getFluidState(spot).isEmpty()) {
                        return spot;
                    }
                }
            }
        }
        return Ground.land(level, around(centre, ring, degrees));
    }
}
