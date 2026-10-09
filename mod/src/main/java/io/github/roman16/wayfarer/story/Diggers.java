package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Diggers {
    static final String NAME = "diggers";
    static final String HALL = "diggers_hall";
    private static final String SITE = "diggers_site";
    private static final String TURN = "diggers_turn";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/diggers.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_diggers", Comparator.comparingLong(ChunkPos::toLong));
    private static final int ANGLE_STEP = 16;
    private static final int NUDGE = 512;
    private static final int NUDGE_STEP = 128;
    private static final int WIDEST = 128;
    private static final int CHUNK = 16;
    private static final int CLEAR = 16;
    private static final int DECLINE = 48;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int FOREST = 64;
    private static final int SAMPLE = 8;
    private static final float TRACK_SCALE = 1.0625f;
    @Nullable
    private static Build build;

    private Diggers() {}

    private record Site(BlockPos centre, Rotation turn, double score) {}

    private static final class Build {
        @Nullable
        private CompletableFuture<List<Site>> searching;
        @Nullable
        private Site site;
        private ChunkPos ticket = ChunkPos.ZERO;
        private int radius;
    }

    static void begin(MinecraftServer server) {
        if (build == null) {
            build = new Build();
        }
    }

    static void reset() {
        build = null;
    }

    static void tick(MinecraftServer server) {
        if (build == null) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        if (build.site == null) {
            search(server, level);
        } else if (loaded(level)) {
            place(server, level);
        }
    }

    private static void search(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> chosen = progress.place(SITE);
        if (chosen.isPresent()) {
            hold(level, new Site(chosen.get(), Rotation.values()[progress.yaws.getOrDefault(TURN, 0f).intValue()], 0));
            return;
        }
        if (build.searching == null) {
            int ahead = Balance.get().diggers().aheadBlocks();
            Home.Aim aim = Home.aim(progress.place(Caravan.NAME).or(() -> progress.place(Steps.OWN_CAMP)).orElseGet(level::getSharedSpawnPos),
                    Caravan.way(server), ahead);
            Unseen.Heights heights = new Unseen.Heights(level);
            build.searching = CompletableFuture.supplyAsync(() -> sites(heights, aim.from(), aim.way(), ahead), Util.backgroundExecutor());
            return;
        }
        if (!build.searching.isDone()) {
            return;
        }
        Site best = build.searching.join().getFirst();
        progress.place(SITE, best.centre());
        progress.yaws.put(TURN, (float) best.turn().ordinal());
        progress.setDirty();
        hold(level, best);
    }

    private static void hold(ServerLevel level, Site site) {
        build.site = site;
        build.ticket = new ChunkPos(site.centre());
        Vec3i size = template(level, "tunnels").getSize();
        int extent = Math.max(size.getX(), size.getZ());
        build.radius = Math.ceilDiv(extent + CHUNK, CHUNK);
        level.getChunkSource().addRegionTicket(TICKET, build.ticket, build.radius, build.ticket);
    }

    private static List<Site> sites(Unseen.Heights heights, BlockPos from, Vec3 way, int ahead) {
        List<Site> dry = new ArrayList<>();
        List<Site> wet = new ArrayList<>();
        double heading = Math.atan2(way.z, way.x);
        for (int angle = -WIDEST; angle <= WIDEST; angle += ANGLE_STEP) {
            double towards = heading + Math.toRadians(angle);
            for (int nudge = -NUDGE; nudge <= NUDGE; nudge += NUDGE_STEP) {
                int x = from.getX() + (int) Math.round(Math.cos(towards) * (ahead + nudge));
                int z = from.getZ() + (int) Math.round(Math.sin(towards) * (ahead + nudge));
                int surface = heights.surface(x, z);
                double score = -Math.abs(angle) / 8.0 - Math.abs(nudge) / 32.0;
                if (heights.biome(x, surface, z).is(BiomeTags.IS_FOREST)) {
                    score += FOREST;
                }
                boolean soaked = heights.wet(x, z);
                for (int along = SAMPLE; along <= DECLINE; along += SAMPLE) {
                    int px = x + (int) Math.round(Math.cos(towards) * along);
                    int pz = z + (int) Math.round(Math.sin(towards) * along);
                    score -= Math.max(0, surface - heights.surface(px, pz)) * 2;
                    soaked |= heights.wet(px, pz);
                }
                Site site = new Site(new BlockPos(x, surface, z), turn(Math.cos(towards), Math.sin(towards)), score);
                (soaked ? wet : dry).add(site);
            }
        }
        List<Site> sites = dry.isEmpty() ? wet : dry;
        sites.sort(Comparator.comparingDouble(Site::score).reversed());
        return sites;
    }

    private static Rotation turn(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        }
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    private static boolean loaded(ServerLevel level) {
        for (int x = build.ticket.x - build.radius + 1; x < build.ticket.x + build.radius; x++) {
            for (int z = build.ticket.z - build.radius + 1; z < build.ticket.z + build.radius; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void place(MinecraftServer server, ServerLevel level) {
        Site site = build.site;
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(site.turn());
        JsonObject entrance = PLAN.get().getAsJsonObject("entrance");
        JsonObject tunnels = PLAN.get().getAsJsonObject("tunnels");
        int surface = ground(level, site.centre().getX(), site.centre().getZ());
        BlockPos centre = vector(entrance.getAsJsonArray("centre"));
        BlockPos entranceAt = new BlockPos(site.centre().getX(), surface - centre.getY(), site.centre().getZ())
                .subtract(turned(settings, centre.atY(0)));
        BlockPos tunnelsAt = entranceAt.offset(turned(settings, vector(entrance.getAsJsonArray("join")).subtract(vector(tunnels.getAsJsonArray("join")))));
        StructureTemplate mouth = template(level, "entrance");
        clear(level, mouth.getBoundingBox(settings, entranceAt));
        template(level, "tunnels").placeInWorld(level, tunnelsAt, tunnelsAt, settings, level.random, FLAGS);
        mouth.placeInWorld(level, entranceAt, entranceAt, settings, level.random, FLAGS);
        Progress progress = Progress.of(server);
        BlockPos door = entranceAt.offset(turned(settings, vector(entrance.getAsJsonArray("mouth"))));
        progress.place(NAME, door);
        progress.place(HALL, tunnelsAt.offset(turned(settings, vector(tunnels.getAsJsonArray("pedestal")))));
        JsonObject hall = tunnels.getAsJsonObject("hall");
        progress.ward(NAME, BoundingBox.fromCorners(tunnelsAt.offset(turned(settings, vector(hall.getAsJsonArray("from")))),
                tunnelsAt.offset(turned(settings, vector(hall.getAsJsonArray("to"))))));
        track(level, door.below());
        for (JsonElement mark : tunnels.getAsJsonArray("marks")) {
            track(level, tunnelsAt.offset(turned(settings, vector(mark.getAsJsonArray()))));
        }
        progress.places.remove(SITE);
        progress.yaws.remove(TURN);
        progress.setDirty();
        level.getChunkSource().removeRegionTicket(TICKET, build.ticket, build.radius, build.ticket);
        LOGGER.info("the Diggers' tunnels lie under {}, their hall at {}", door, progress.place(HALL).orElseThrow());
        build = null;
        Journal.syncAll(server);
    }

    private static void clear(ServerLevel level, BoundingBox box) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY() + CLEAR; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced() && !state.isAir() && state.getFluidState().isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    private static void track(ServerLevel level, BlockPos at) {
        Clues.display(level, at, TRACK_SCALE, NAME);
    }

    private static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        while (pos.getY() > level.getMinBuildHeight() && (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(BlockTags.LOGS)
                || level.getBlockState(pos).canBeReplaced())) {
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    private static BlockPos turned(StructurePlaceSettings settings, BlockPos local) {
        return StructureTemplate.calculateRelativePosition(settings, local);
    }

    private static StructureTemplate template(ServerLevel level, String piece) {
        return level.getStructureManager().getOrCreate(Wayfarer.id("diggers/" + piece));
    }

    private static BlockPos vector(JsonArray xyz) {
        return Prologue.vector(xyz);
    }
}
