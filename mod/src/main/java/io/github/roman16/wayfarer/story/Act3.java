package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Rules;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

final class Act3 {
    static final String HOLLOW = "hollow";
    static final String AIRFIELD = "airfield";
    static final String KEEPERS = "keepers";
    private static final String KEEPERS_SITE = "keepers_site";
    private static final String KEEPERS_FRAME = "keepers_frame";
    private static final String FRAME_MARKED = "keepers_frame_marked";
    private static final String FRAME_CLUE = "frame";
    private static final List<ResourceLocation> STRONGHOLDS = List.of(ResourceLocation.fromNamespaceAndPath("betterstrongholds", "stronghold"));
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("endrem", "ancient_portal_frame");
    private static final int FRAME_SEARCH = 2;
    private static final int FRAME_REACH = 10;
    private static final int FLOOR_REACH = 1;
    private static final double EYE = 1.6;
    private static final String HOLLOW_MARKED = "hollow_marked";
    private static final String AIRFIELD_SITE = "airfield_site";
    private static final String AIRFIELD_ORIGIN = "airfield_origin";
    private static final String AIRFIELD_TURN = "airfield_turn";
    private static final int MOORING = 32;
    private static final int BANNER_SLOT = 4;
    private static final int DYE_SLOT = 5;
    private static final int BOILER_SLOT = 6;
    private static final String WINGS = "wings";
    private static final ResourceLocation FIELD = Wayfarer.id("act3/airfield");
    private static final ResourceLocation SHIP = ResourceLocation.fromNamespaceAndPath("immersive_aircraft", "airship");
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/act3.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] WOUNDED = {"wounded_1", "wounded_2", "wounded_3"};
    private static final String[] GRAVES = {"grave_1", "grave_2", "grave_3"};
    private static final int WOUNDED_RING = 5;
    private static final int GRAVE_RING = 9;
    private static final int TURN_STEP = 20;
    private static final int WIDEST = 120;
    private static final int SAMPLE = 64;
    private static final int HIGH = 24;

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
            case AIRFIELD -> {
                if (progress.place(AIRFIELD_SITE).isEmpty()) {
                    BlockPos site = high(server, from);
                    progress.place(AIRFIELD_SITE, site);
                    progress.place(AIRFIELD, site);
                    progress.setDirty();
                    LOGGER.info("the Gazebound airfield will stand at {}", site);
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

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return switch (name) {
            case HOLLOW -> progress.place(HOLLOW_MARKED).isPresent();
            case AIRFIELD -> progress.place(AIRFIELD_ORIGIN).isPresent();
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
        progress.place(AIRFIELD_SITE).filter(site -> progress.place(AIRFIELD_ORIGIN).isEmpty() && HallSearch.loaded(level, site)).ifPresent(site -> {
            field(server, level, site);
            HallSearch.release(level, site);
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
        progress.place(AIRFIELD_ORIGIN).filter(origin -> !progress.done(WINGS) && level.areEntitiesLoaded(ChunkPos.asLong(origin))).ifPresent(origin -> moor(server, level));
    }

    private static void mark(MinecraftServer server, ServerLevel level, BlockPos centre) {
        Progress progress = Progress.of(server);
        for (int index = 0; index < WOUNDED.length; index++) {
            Clues.marker(level, ground(level, around(centre, WOUNDED_RING, index * 120.0 + 30)), WOUNDED[index]);
        }
        for (int index = 0; index < GRAVES.length; index++) {
            BlockPos grave = ground(level, around(centre, GRAVE_RING, index * 120.0 + 90));
            level.setBlock(grave, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(grave.above(), Blocks.ROOTED_DIRT.defaultBlockState(), Block.UPDATE_ALL);
            Clues.marker(level, grave.above(), GRAVES[index]);
        }
        progress.place(HOLLOW_MARKED, centre);
        progress.setDirty();
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
        for (Holder<Structure> kind : strongholds(level)) {
            StructureStart start = level.getChunk(site.getX() >> 4, site.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(kind.value());
            if (start != null && start.isValid()) {
                BoundingBox box = start.getBoundingBox();
                ChunkPos.rangeClosed(new ChunkPos(box.minX() >> 4, box.minZ() >> 4), new ChunkPos(box.maxX() >> 4, box.maxZ() >> 4)).forEach(chunk -> level.getChunk(chunk.x, chunk.z));
                frame = BlockPos.betweenClosedStream(box).filter(pos -> {
                    var state = level.getBlockState(pos);
                    return state.is(Blocks.END_PORTAL_FRAME) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(FRAME);
                }).findFirst().map(BlockPos::immutable);
                break;
            }
        }
        BlockPos at = frame.orElse(site);
        clear(level, new BoundingBox(at.getX() - FRAME_REACH, at.getY() - FRAME_SEARCH, at.getZ() - FRAME_REACH, at.getX() + FRAME_REACH,
                at.getY() + FRAME_SEARCH * 2, at.getZ() + FRAME_REACH));
        BlockPos seat = BlockPos.betweenClosedStream(at.offset(-FRAME_REACH, -FRAME_SEARCH, -FRAME_REACH), at.offset(FRAME_REACH, FRAME_SEARCH * 2, FRAME_REACH))
                .filter(pos -> floor(level, pos) && Math.abs(pos.getX() - at.getX()) + Math.abs(pos.getZ() - at.getZ()) >= FRAME_SEARCH * 2 && sees(level, pos, at))
                .map(BlockPos::immutable).min(Comparator.comparingDouble(pos -> pos.distSqr(at))).orElse(at.above());
        progress.place(KEEPERS_FRAME, at);
        progress.place(KEEPERS, seat);
        progress.place("seat_keepers_oldest", seat);
        progress.yaws.put("seat_keepers_oldest", Frost.yaw(Vec3.atLowerCornerOf(at.subtract(seat)).multiply(1, 0, 1)));
        progress.setDirty();
        LOGGER.info("the Keepers' frame is at {}{}", at, frame.isPresent() ? "" : " (no frame block found; the stronghold's middle)");
        Journal.syncAll(server);
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
        return BlockPos.betweenClosedStream(pos.offset(-FLOOR_REACH, 0, -FLOOR_REACH), pos.offset(FLOOR_REACH, 0, FLOOR_REACH)).allMatch(near -> standable(level, near));
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && level.getFluidState(pos.below()).isEmpty();
    }

    private static BlockPos around(BlockPos centre, int ring, double degrees) {
        double angle = Math.toRadians(degrees);
        return centre.offset((int) Math.round(Math.cos(angle) * ring), 0, (int) Math.round(Math.sin(angle) * ring));
    }

    private static BlockPos ground(ServerLevel level, BlockPos at) {
        level.getChunk(at);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at).below();
    }

    static BlockPos high(MinecraftServer server, BlockPos from) {
        ServerLevel level = Engine.overworld(server);
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        int ahead = Balance.get().errands().aheadBlocks();
        Vec3 way = Caravan.way(server);
        BlockPos best = null;
        for (int turned = 0; turned <= WIDEST; turned += TURN_STEP) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                for (int out = ahead; out >= ahead / 2; out -= SAMPLE) {
                    int x = from.getX() + (int) Math.round(bent.x * out);
                    int z = from.getZ() + (int) Math.round(bent.z * out);
                    if (heights.wet(x, z)) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x, heights.surface(x, z), z);
                    if (at.getY() >= sea + HIGH && Math.abs(heights.surface(x + 8, z) - at.getY()) <= 3 && Math.abs(heights.surface(x, z + 8) - at.getY()) <= 3) {
                        return at;
                    }
                    if (best == null || at.getY() > best.getY()) {
                        best = at;
                    }
                }
            }
        }
        return best == null ? from : best;
    }

    private static void field(MinecraftServer server, ServerLevel level, BlockPos site) {
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElse(site);
        Vec3 toward = Vec3.atLowerCornerOf(from.subtract(site)).multiply(1, 0, 1);
        Rotation turn = Frost.facing(toward.lengthSqr() < 1 ? new Vec3(0, 0, -1) : toward.normalize().scale(-1));
        BlockPos origin = Camps.place(level, FIELD, site, turn);
        progress.place(AIRFIELD_ORIGIN, origin);
        progress.yaws.put(AIRFIELD_TURN, (float) turn.ordinal());
        JsonObject plan = PLAN.get().getAsJsonObject(AIRFIELD);
        progress.place(AIRFIELD, world(level, origin, turn, plan, "rack"));
        progress.ward(AIRFIELD, level.getStructureManager().getOrCreate(FIELD).getBoundingBox(Camps.settings(level, FIELD, turn), origin));
        progress.setDirty();
        Clues.marker(level, world(level, origin, turn, plan, "rack"), "robes");
        LOGGER.info("the Gazebound airfield stands at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    private static void moor(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos origin = progress.place(AIRFIELD_ORIGIN).orElseThrow();
        if (!level.getEntities((Entity) null, new AABB(origin).inflate(MOORING), entity -> entity.getTags().contains(Rules.MOORED)).isEmpty()) {
            return;
        }
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(AIRFIELD_TURN, 0f).intValue()];
        BlockPos at = world(level, origin, turn, PLAN.get().getAsJsonObject(AIRFIELD), "airship");
        Optional<Entity> ship = BuiltInRegistries.ENTITY_TYPE.getOptional(SHIP).map(type -> type.create(level));
        if (ship.isEmpty()) {
            LOGGER.error("wings failed: Immersive Aircraft's airship is not in this game");
            return;
        }
        ship.get().moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
        ship.get().addTag(Rules.MOORED);
        if (ship.get() instanceof Container hold) {
            hold.setItem(BANNER_SLOT, new ItemStack(Items.GRAY_BANNER));
            hold.setItem(DYE_SLOT, new ItemStack(Items.GRAY_DYE));
            hold.setItem(BOILER_SLOT, new ItemStack(Items.COAL, Balance.get().errands().airshipCoal()));
        }
        level.addFreshEntity(ship.get());
        LOGGER.info("the Gazebound airship is moored at {}", at);
    }

    private static BlockPos world(ServerLevel level, BlockPos origin, Rotation turn, JsonObject plan, String key) {
        return origin.offset(StructureTemplate.calculateRelativePosition(Camps.settings(level, FIELD, turn), Prologue.vector(plan.getAsJsonArray(key))));
    }
}
