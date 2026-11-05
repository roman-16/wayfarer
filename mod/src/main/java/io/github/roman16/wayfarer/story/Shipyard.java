package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Shipyard {
    static final String NAME = "shipyard";
    static final String WRECK = "shipyard_wreck";
    static final String LAUNCH = "shipyard_launch";
    private static final String HULL_SEAT = "shipyard_hull";
    private static final String JETTY_SEAT = "shipyard_jetty";
    private static final String QUAY_SEAT = "shipyard_quay";
    private static final String SITE = "shipyard_site";
    private static final String WRECK_SITE = "shipyard_wreck_site";
    private static final String YARD_ORIGIN = "shipyard_origin";
    private static final String WRECK_ORIGIN = "shipyard_wreck_origin";
    private static final String LAUNCHING = "shipyard_launching";
    private static final String OUT = "shipyard_out";
    private static final ResourceLocation YARD = Wayfarer.id("shipyard/yard");
    private static final ResourceLocation HULL = Wayfarer.id("shipyard/hull");
    private static final ResourceLocation WRECKAGE = Wayfarer.id("shipyard/wreck");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/shipyard.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_shipyard", Comparator.comparingLong(ChunkPos::toLong));
    private static final int LOAD_RADIUS = 3;
    private static final int MARCH = 64;
    private static final int CELL = 8;
    private static final int BAND = 96;
    private static final int TURN_STEP = 4;
    private static final int WIDEST_TURN = 88;
    private static final int OFFSHORE = 48;
    private static final int OFFSHORE_SAMPLES = 4;
    private static final int OPEN = 512;
    private static final int LEAD = 1536;
    private static final int CHECKED = 16;
    private static final double BEACH_DETOUR = 2.25;
    private static final int[] ALONG = {0, 32, -32, 64, -64};
    private static final int INLAND = 18;
    private static final int HIGHEST = 8;
    private static final int ACROSS = 10;
    private static final int RAGGED = 8;
    private static final int REFINE = 24;
    private static final int DEEPEST = 24;
    private static final int SAMPLES = 32;
    private static final int SURROUND = 24;
    private static final int WEAR = 4;
    private static final int POSTS = 4;
    private static final int RAIL_RUN = 4;
    private static final int RAIL_DROP = 2;
    private static final float PACKAGE_WIDTH = 0.4f;
    private static final float PACKAGE_HEIGHT = 0.18f;
    private static final float PACKAGE_DEPTH = 0.3f;

    @Nullable
    private static CompletableFuture<Site> searching;

    private Shipyard() {}

    private record Site(BlockPos shore, Vec3 out, BlockPos wreck) {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (searching != null || progress.place(SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        BlockPos from = Caravan.last(server);
        Vec3 flat = new Vec3(from.getX(), 0, from.getZ());
        Vec3 out = flat.lengthSqr() < 1 ? Caravan.way(server) : flat.normalize();
        Unseen.Heights heights = new Unseen.Heights(level);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        Balance.World world = Balance.get().world();
        int farthest = world.homeBlocks() + world.coastBlocks() + world.seaBlocks();
        int wreck = Balance.get().shipyard().wreckBlocks();
        long started = System.nanoTime();
        searching = CompletableFuture.supplyAsync(() -> search(heights, from, out, sea, farthest, wreck), Util.backgroundExecutor())
                .whenComplete((site, failure) -> LOGGER.info("the shipyard's search took {} ms", (System.nanoTime() - started) / 1_000_000));
    }

    static void reset() {
        searching = null;
    }

    static void unbuild(MinecraftServer server, String name) {
        Progress.of(server).places.remove(name.equals(WRECK) ? WRECK_ORIGIN : YARD_ORIGIN);
        Progress.of(server).setDirty();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return switch (name) {
            case WRECK -> progress.place(WRECK_ORIGIN).isPresent();
            case LAUNCH -> progress.place(LAUNCH).isPresent();
            default -> progress.place(YARD_ORIGIN).isPresent();
        };
    }

    static void launch(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(LAUNCHING).isEmpty()) {
            progress.place(LAUNCHING, BlockPos.ZERO);
            progress.setDirty();
        }
        find(server);
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        if (searching != null && searching.isDone()) {
            Site site;
            try {
                site = searching.join();
            } catch (CompletionException failure) {
                LOGGER.error("the shipyard's search failed: {}", failure.getCause().toString());
                searching = null;
                return;
            }
            searching = null;
            progress.place(SITE, site.shore());
            progress.place(NAME, site.shore());
            progress.place(WRECK_SITE, site.wreck());
            progress.place(WRECK, site.wreck());
            progress.yaws.put(OUT, (float) Math.atan2(site.out().z, site.out().x));
            progress.setDirty();
            LOGGER.info("the shipyard will stand at {}, facing the sea {}, the wreck at {}", site.shore(), FirstCamp.direction(BlockPos.ZERO,
                    BlockPos.containing(site.out().scale(CELL))), site.wreck());
            Journal.syncAll(server);
        }
        progress.place(SITE).filter(site -> progress.place(YARD_ORIGIN).isEmpty() && loaded(level, site)).ifPresent(site -> yard(server, site));
        progress.place(WRECK_SITE).filter(site -> progress.place(WRECK_ORIGIN).isEmpty() && loaded(level, site)).ifPresent(site -> wreck(server, site));
        if (progress.place(LAUNCHING).isPresent() && progress.place(LAUNCH).isEmpty()) {
            progress.place(YARD_ORIGIN).filter(origin -> loaded(level, progress.place(SITE).orElse(origin))).ifPresent(origin -> finish(server, origin));
        }
    }

    private static Site search(Unseen.Heights heights, BlockPos from, Vec3 out, int sea, int farthest, int wreckBlocks) {
        int ahead = reach(heights, from, out, 0, farthest, sea);
        List<Shore> shores = new ArrayList<>();
        for (int turned = -WIDEST_TURN; turned <= WIDEST_TURN; turned += TURN_STEP) {
            Vec3 way = out.yRot((float) Math.toRadians(turned));
            int coast = reach(heights, from, way, Math.max(0, ahead - LEAD), farthest, sea);
            if (coast < 0) {
                continue;
            }
            Vec3 start = Vec3.atLowerCornerOf(from).multiply(1, 0, 1);
            BlockPos land = BlockPos.containing(start.add(way.scale(coast - CELL)));
            Vec3 axis = axis(way);
            if (!unfit(heights, land, axis, sea)) {
                shores.add(new Shore(land, axis, heights.biome(land.getX(), sea, land.getZ()).is(BiomeTags.IS_BEACH)));
            }
        }
        shores.sort(Comparator.comparingDouble(shore -> horizontal(shore.land(), from) * (shore.beach() ? 1 : BEACH_DETOUR)));
        int tried = 0;
        BlockPos first = null;
        Vec3 firstAxis = null;
        for (Shore shore : shores) {
            if (tried++ >= CHECKED) {
                break;
            }
            BlockPos edge = edge(heights, shore.land(), shore.axis(), BAND, sea);
            if (edge == null) {
                continue;
            }
            if (first == null) {
                first = edge;
                firstAxis = shore.axis();
            }
            if (fits(heights, edge, shore.axis(), sea)) {
                LOGGER.info("the shipyard's search took the {} shore of {} warm ones, after {} checked", shore.beach() ? "beach" : "plain", shores.size(), tried);
                return new Site(edge, shore.axis(), wreck(heights, edge, shore.axis(), wreckBlocks, sea));
            }
        }
        if (first != null) {
            LOGGER.warn("the shipyard's search found no low, straight shore among {} warm ones; it takes the nearest", shores.size());
            return new Site(first, firstAxis, wreck(heights, first, firstAxis, wreckBlocks, sea));
        }
        LOGGER.warn("the shipyard's search found no warm shore within {} degrees; it takes the coast straight ahead", WIDEST_TURN);
        int coast = Math.max(0, ahead);
        BlockPos far = BlockPos.containing(Vec3.atLowerCornerOf(from).multiply(1, 0, 1).add(out.scale(coast - CELL)));
        return new Site(far.atY(heights.surface(far.getX(), far.getZ())), axis(out), far);
    }

    private record Shore(BlockPos land, Vec3 axis, boolean beach) {}

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean unfit(Unseen.Heights heights, BlockPos land, Vec3 axis, int sea) {
        if (Unseen.snowy(heights.biome(land.getX(), sea, land.getZ()), land.atY(sea)) || heights.biome(land.getX(), sea, land.getZ()).is(BiomeTags.IS_MOUNTAIN)) {
            return true;
        }
        for (int out = OFFSHORE; out <= OFFSHORE * OFFSHORE_SAMPLES; out += OFFSHORE) {
            BlockPos water = BlockPos.containing(Vec3.atLowerCornerOf(land).add(axis.scale(out))).atY(sea);
            if (Unseen.snowy(heights.biome(water.getX(), sea, water.getZ()), water)) {
                return true;
            }
        }
        BlockPos open = BlockPos.containing(Vec3.atLowerCornerOf(land).add(axis.scale(OPEN))).atY(sea);
        return !heights.biome(open.getX(), sea, open.getZ()).is(BiomeTags.IS_OCEAN);
    }

    private static int reach(Unseen.Heights heights, BlockPos from, Vec3 way, int start, int farthest, int sea) {
        Vec3 flat = Vec3.atLowerCornerOf(from).multiply(1, 0, 1);
        for (int distance = start; flat.add(way.scale(distance)).length() <= farthest; distance += MARCH) {
            BlockPos at = BlockPos.containing(flat.add(way.scale(distance)));
            if (heights.biome(at.getX(), sea, at.getZ()).is(BiomeTags.IS_OCEAN)) {
                for (int back = CELL; back <= MARCH; back += CELL) {
                    BlockPos before = BlockPos.containing(flat.add(way.scale(distance - back)));
                    if (!heights.biome(before.getX(), sea, before.getZ()).is(BiomeTags.IS_OCEAN)) {
                        return distance - back + CELL;
                    }
                }
                return distance;
            }
        }
        return -1;
    }

    @Nullable
    private static BlockPos edge(Unseen.Heights heights, BlockPos near, Vec3 axis, int band, int sea) {
        BlockPos dry = null;
        for (int along = -band; along <= band; along += CELL) {
            BlockPos at = BlockPos.containing(Vec3.atLowerCornerOf(near).add(axis.scale(along)));
            boolean wet = heights.wet(at.getX(), at.getZ()) || heights.surface(at.getX(), at.getZ()) < sea;
            if (!wet) {
                dry = at;
            } else if (dry != null) {
                return dry.atY(heights.surface(dry.getX(), dry.getZ()));
            }
        }
        return null;
    }

    private static boolean fits(Unseen.Heights heights, BlockPos shore, Vec3 axis, int sea) {
        BlockPos inland = BlockPos.containing(Vec3.atLowerCornerOf(shore).subtract(axis.scale(INLAND)));
        if (heights.surface(inland.getX(), inland.getZ()) > sea + HIGHEST || heights.surface(shore.getX(), shore.getZ()) > sea + HIGHEST / 2) {
            return false;
        }
        Vec3 across = new Vec3(-axis.z, 0, axis.x);
        for (int side : new int[] {-ACROSS, ACROSS}) {
            BlockPos edge = edge(heights, BlockPos.containing(Vec3.atLowerCornerOf(shore).add(across.scale(side))), axis, BAND / 2, sea);
            if (edge == null || Math.abs(Vec3.atLowerCornerOf(edge.subtract(shore)).dot(axis)) > RAGGED) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos wreck(Unseen.Heights heights, BlockPos shore, Vec3 axis, int blocks, int sea) {
        Vec3 across = new Vec3(-axis.z, 0, axis.x);
        for (int shift : ALONG) {
            for (int side : new int[] {1, -1}) {
                BlockPos near = BlockPos.containing(Vec3.atLowerCornerOf(shore).add(across.scale((blocks + shift) * side)));
                BlockPos edge = edge(heights, near, axis, BAND, sea);
                if (edge != null && edge.getY() <= sea + HIGHEST / 2) {
                    return edge;
                }
            }
        }
        BlockPos near = BlockPos.containing(Vec3.atLowerCornerOf(shore).add(across.scale(blocks)));
        return near.atY(heights.surface(near.getX(), near.getZ()));
    }

    private static Vec3 axis(Vec3 way) {
        return Math.abs(way.x) >= Math.abs(way.z) ? new Vec3(Math.signum(way.x), 0, 0) : new Vec3(0, 0, Math.signum(way.z));
    }

    private static Vec3 out(Progress progress) {
        float angle = progress.yaws.getOrDefault(OUT, 0f);
        return axis(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
    }

    private static boolean loaded(ServerLevel level, BlockPos site) {
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

    private static void release(ServerLevel level, BlockPos site) {
        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(site), LOAD_RADIUS, new ChunkPos(site));
    }

    private static boolean water(ServerLevel level, BlockPos at) {
        return level.getFluidState(at).is(FluidTags.WATER);
    }

    private static BlockPos shore(ServerLevel level, BlockPos site, Vec3 axis) {
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        BlockPos dry = null;
        for (int along = -REFINE; along <= REFINE; along++) {
            BlockPos column = BlockPos.containing(Vec3.atLowerCornerOf(site).add(axis.scale(along)));
            int top = Ground.land(level, column.getX(), column.getZ());
            boolean wet = top < sea && water(level, column.atY(top));
            if (!wet) {
                dry = column;
            } else if (dry != null) {
                return dry.atY(sea);
            }
        }
        return site.atY(sea);
    }

    private static void yard(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Vec3 axis = out(progress);
        Rotation turn = Frost.facing(axis);
        JsonObject plan = PLAN.get().getAsJsonObject("yard");
        StructurePlaceSettings settings = Camps.settings(level, YARD, turn);
        JsonArray ground = plan.getAsJsonArray("ground");
        int width = ground.get(0).getAsInt();
        int length = ground.get(1).getAsInt();
        BlockPos shore = shore(level, site, axis);
        BlockPos origin = shore.subtract(StructureTemplate.calculateRelativePosition(settings, new BlockPos(width / 2, 0, length - 1)));
        Camps.build(level, YARD, origin, turn);
        wear(level, origin, settings, plan.getAsJsonArray("worn"), new BlockPos(width / 2, 0, length / 2));
        int quay = plan.get("quay").getAsInt();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                BlockPos top = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(x, 0, z)));
                if (!level.getBlockState(top).isAir() && !water(level, top)) {
                    underpin(level, top, z >= quay ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                }
            }
        }
        jetty(level, origin, settings, plan.getAsJsonArray("jetty"), axis);
        for (var rail : plan.getAsJsonArray("rails")) {
            for (int run = 0; run < RAIL_RUN; run++) {
                BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(rail.getAsInt(), 0, length + run)))
                        .below(run < RAIL_DROP ? 1 : 2);
                if (water(level, at)) {
                    level.setBlock(at, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis.x != 0 ? Direction.Axis.X : Direction.Axis.Z),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
        seat(progress, origin, settings, turn, HULL_SEAT, plan.getAsJsonArray("edda"));
        seat(progress, origin, settings, turn, JETTY_SEAT, plan.getAsJsonArray("launch"));
        seat(progress, origin, settings, turn, QUAY_SEAT, plan.getAsJsonArray("maud"));
        progress.place(NAME, origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("hull")))));
        progress.place(YARD_ORIGIN, origin);
        progress.ward(NAME, level.getStructureManager().getOrCreate(YARD).getBoundingBox(settings, origin));
        progress.setDirty();
        release(level, site);
        Standing.keep(server, NAME);
        LOGGER.info("the shipyard stands at {}, its shore at {}, turned {}", origin, shore, turn);
        Journal.syncAll(server);
    }

    private static void wear(ServerLevel level, BlockPos origin, StructurePlaceSettings settings, JsonArray cells, BlockPos middle) {
        BlockPos centre = origin.offset(StructureTemplate.calculateRelativePosition(settings, middle));
        Map<BlockState, Integer> seen = new HashMap<>();
        for (int step = 0; step < SAMPLES; step++) {
            double angle = Math.PI * 2 * step / SAMPLES;
            int x = centre.getX() + (int) Math.round(Math.cos(angle) * SURROUND);
            int z = centre.getZ() + (int) Math.round(Math.sin(angle) * SURROUND);
            BlockPos top = new BlockPos(x, Ground.land(level, x, z), z);
            BlockState state = level.getBlockState(top);
            if (state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, top) && !state.is(BlockTags.LOGS)) {
                seen.merge(state.getBlock().defaultBlockState(), 1, Integer::sum);
            }
        }
        BlockState surface = seen.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Blocks.COARSE_DIRT.defaultBlockState());
        BlockState trodden = surface.is(BlockTags.SAND) ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        for (var cell : cells) {
            JsonArray xz = cell.getAsJsonArray();
            BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(xz.get(0).getAsInt(), 0, xz.get(1).getAsInt())));
            level.setBlock(at, Math.floorMod(at.hashCode(), WEAR) == 0 ? trodden : surface, Block.UPDATE_CLIENTS);
        }
    }

    private static void underpin(ServerLevel level, BlockPos top, BlockState fill) {
        for (BlockPos at = top.below(); top.getY() - at.getY() <= DEEPEST; at = at.below()) {
            BlockState state = level.getBlockState(at);
            if (!state.isAir() && !water(level, at) && !state.canBeReplaced()) {
                return;
            }
            level.setBlock(at, fill, Block.UPDATE_CLIENTS);
        }
    }

    private static void jetty(ServerLevel level, BlockPos origin, StructurePlaceSettings settings, JsonArray jetty, Vec3 axis) {
        int first = jetty.get(0).getAsInt();
        int start = jetty.get(1).getAsInt();
        int width = jetty.get(2).getAsInt();
        int length = jetty.get(3).getAsInt();
        int built = 0;
        for (int run = 0; run < length; run++) {
            BlockPos middle = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(first + width / 2, 0, start + run)));
            if (!water(level, middle.below())) {
                if (run > 1) {
                    break;
                }
                continue;
            }
            built = run;
            for (int across = 0; across < width; across++) {
                BlockPos deck = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(first + across, 0, start + run)));
                boolean post = (across == 0 || across == width - 1) && run % POSTS == POSTS - 1;
                level.setBlock(deck, post ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (post) {
                    underpin(level, deck, Blocks.SPRUCE_LOG.defaultBlockState());
                }
            }
        }
        for (int across : new int[] {0, width - 1}) {
            BlockPos end = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(first + across, 0, start + built)));
            if (level.getBlockState(end).is(Blocks.SPRUCE_PLANKS) || level.getBlockState(end).is(Blocks.SPRUCE_LOG)) {
                underpin(level, end, Blocks.SPRUCE_LOG.defaultBlockState());
                level.setBlock(end, Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(end.above(), Blocks.SPRUCE_FENCE.defaultBlockState().setValue(FenceBlock.WATERLOGGED, false), Block.UPDATE_CLIENTS);
                level.setBlock(end.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), Block.UPDATE_CLIENTS);
            }
        }
        LOGGER.info("the shipyard's jetty runs {} blocks out {}", built + 1, axis);
    }

    private static void seat(Progress progress, BlockPos origin, StructurePlaceSettings settings, Rotation turn, String name, JsonArray spot) {
        BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(spot)));
        BlockPos facing = StructureTemplate.transform(new BlockPos(spot.get(3).getAsInt(), 0, spot.get(4).getAsInt()), Mirror.NONE, turn, BlockPos.ZERO);
        progress.place("seat_" + name, at);
        progress.yaws.put("seat_" + name, Frost.yaw(Vec3.atLowerCornerOf(facing)));
    }

    private static void wreck(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Rotation turn = Frost.facing(out(progress));
        BlockPos origin = Camps.place(level, WRECKAGE, site, turn);
        StructurePlaceSettings settings = Camps.settings(level, WRECKAGE, turn);
        JsonObject plan = PLAN.get().getAsJsonObject("wreck");
        BlockPos keel = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("keel"))));
        BlockPos tools = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("tools"))));
        BlockPos log = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("log"))));
        Clues.marker(level, keel, "keel");
        Clues.marker(level, tools, "tools");
        Clues.item(level, Blocks.BROWN_WOOL.defaultBlockState(), Vec3.atLowerCornerOf(log), PACKAGE_WIDTH, PACKAGE_HEIGHT, PACKAGE_DEPTH, "log");
        Clues.box(level, Vec3.atBottomCenterOf(log), "log");
        progress.place(WRECK, keel);
        progress.place(WRECK_ORIGIN, origin);
        progress.ward(WRECK, level.getStructureManager().getOrCreate(WRECKAGE).getBoundingBox(settings, origin));
        progress.setDirty();
        release(level, site);
        Standing.keep(server, WRECK);
        LOGGER.info("the wreck lies at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    private static void finish(MinecraftServer server, BlockPos origin) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Rotation turn = Frost.facing(out(progress));
        level.getStructureManager().getOrCreate(HULL).placeInWorld(level, origin, origin, Camps.settings(level, HULL, turn), level.random, Block.UPDATE_CLIENTS);
        progress.place(LAUNCH, progress.place(NAME).orElse(origin));
        progress.setDirty();
        Standing.keep(server, NAME);
        progress.place(SITE).ifPresent(site -> release(level, site));
        LOGGER.info("the hull on the shipyard's slip is finished");
    }
}
