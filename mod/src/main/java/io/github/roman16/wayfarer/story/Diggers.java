package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Climate;
import io.github.roman16.wayfarer.HallRoom;
import io.github.roman16.wayfarer.HallStructure;
import io.github.roman16.wayfarer.Underground;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class Diggers {
    static final String NAME = "diggers";
    static final String HALL = "diggers_hall";
    static final ResourceKey<Structure> LOST = ResourceKey.create(Registries.STRUCTURE, Wayfarer.id("hall/lost"));
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
    private static final int SLICE = 16;
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
        private final Deque<BoundingBox> slices = new ArrayDeque<>();
        private final Deque<Site> others = new ArrayDeque<>();
        @Nullable
        private StructurePlaceSettings settings;
        private BlockPos tunnelsAt = BlockPos.ZERO;
        private BlockPos entranceAt = BlockPos.ZERO;
    }

    static void begin(MinecraftServer server) {
        if (build == null) {
            build = new Build();
        }
    }

    static void reset() {
        build = null;
    }

    static void unbuild(MinecraftServer server) {
        Progress progress = Progress.of(server);
        progress.places.remove(NAME);
        progress.places.remove(HALL);
        progress.setDirty();
        begin(server);
    }

    static void tick(MinecraftServer server) {
        if (build == null) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        if (build.site == null) {
            search(server, level);
        } else if (build.settings != null) {
            slice(server, level);
        } else if (loaded(level)) {
            place(level);
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
            Climate climate = ((HallStructure) level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(LOST).value()).climate();
            build.searching = CompletableFuture.supplyAsync(() -> sites(heights, aim.from(), aim.way(), ahead, climate), Util.backgroundExecutor());
            return;
        }
        if (!build.searching.isDone()) {
            return;
        }
        build.others.addAll(build.searching.join());
        choose(level, progress, build.others.poll());
    }

    private static void choose(ServerLevel level, Progress progress, Site best) {
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

    private static List<Site> sites(Unseen.Heights heights, BlockPos from, Vec3 way, int ahead, Climate climate) {
        List<Candidate> candidates = new ArrayList<>();
        double heading = Math.atan2(way.z, way.x);
        for (int angle = -WIDEST; angle <= WIDEST; angle += ANGLE_STEP) {
            double towards = heading + Math.toRadians(angle);
            for (int nudge = -NUDGE; nudge <= NUDGE; nudge += NUDGE_STEP) {
                int x = from.getX() + (int) Math.round(Math.cos(towards) * (ahead + nudge));
                int z = from.getZ() + (int) Math.round(Math.sin(towards) * (ahead + nudge));
                int surface = heights.surface(x, z);
                double score = -Math.abs(angle) / 8.0 - Math.abs(nudge) / 32.0;
                Holder<Biome> biome = heights.biome(x, surface, z);
                if (biome.is(BiomeTags.IS_FOREST)) {
                    score += FOREST;
                }
                boolean soaked = heights.wet(x, z);
                for (int along = SAMPLE; along <= DECLINE; along += SAMPLE) {
                    int px = x + (int) Math.round(Math.cos(towards) * along);
                    int pz = z + (int) Math.round(Math.sin(towards) * along);
                    score -= Math.max(0, surface - heights.surface(px, pz)) * 2;
                    soaked |= heights.wet(px, pz);
                }
                candidates.add(new Candidate(new Site(new BlockPos(x, surface, z), turn(Math.cos(towards), Math.sin(towards)), score), soaked, biome));
            }
        }
        for (Predicate<Holder<Biome>> pass : List.<Predicate<Holder<Biome>>>of(climate::fits, climate::bears, biome -> true)) {
            for (boolean soaked : new boolean[] {false, true}) {
                List<Site> sites = candidates.stream().filter(candidate -> candidate.soaked() == soaked && pass.test(candidate.biome())).map(Candidate::site)
                        .sorted(Comparator.comparingDouble(Site::score).reversed()).collect(Collectors.toCollection(ArrayList::new));
                if (!sites.isEmpty()) {
                    return sites;
                }
            }
        }
        return List.of();
    }

    private record Candidate(Site site, boolean soaked, Holder<Biome> biome) {}

    private static Rotation turn(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        }
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    private static boolean loaded(ServerLevel level) {
        for (int x = build.ticket.x - build.radius + 1; x < build.ticket.x + build.radius; x++) {
            for (int z = build.ticket.z - build.radius + 1; z < build.ticket.z + build.radius; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void place(ServerLevel level) {
        Site site = build.site;
        StructurePlaceSettings settings = Underground.settings(site.turn());
        JsonObject entrance = PLAN.get().getAsJsonObject("entrance");
        JsonObject tunnels = PLAN.get().getAsJsonObject("tunnels");
        int surface = Ground.land(level, site.centre().getX(), site.centre().getZ());
        BlockPos centre = vector(entrance.getAsJsonArray("centre"));
        BlockPos entranceAt = new BlockPos(site.centre().getX(), surface - centre.getY(), site.centre().getZ()).subtract(turned(settings, centre.atY(0)));
        BoundingBox mouth = template(level, "entrance").getBoundingBox(settings, entranceAt);
        boolean wet = BlockPos.betweenClosedStream(mouth.minX(), 0, mouth.minZ(), mouth.maxX(), 0, mouth.maxZ())
                .anyMatch(column -> Ground.wet(level, column.getX(), column.getZ()));
        if (wet && !build.others.isEmpty()) {
            level.getChunkSource().removeRegionTicket(TICKET, build.ticket, build.radius, build.ticket);
            choose(level, Progress.of(level.getServer()), build.others.poll());
            return;
        }
        build.entranceAt = entranceAt;
        build.tunnelsAt = build.entranceAt.offset(turned(settings, vector(entrance.getAsJsonArray("join")).subtract(vector(tunnels.getAsJsonArray("join")))));
        build.settings = settings;
        clear(level, template(level, "entrance").getBoundingBox(settings, build.entranceAt));
        BoundingBox whole = template(level, "tunnels").getBoundingBox(settings, build.tunnelsAt);
        for (int x = whole.minX(); x <= whole.maxX(); x += SLICE) {
            for (int z = whole.minZ(); z <= whole.maxZ(); z += SLICE) {
                build.slices.add(new BoundingBox(x, whole.minY(), z, Math.min(x + SLICE - 1, whole.maxX()), whole.maxY(), Math.min(z + SLICE - 1, whole.maxZ())));
            }
        }
    }

    private static void slice(MinecraftServer server, ServerLevel level) {
        StructurePlaceSettings settings = build.settings;
        BlockPos tunnelsAt = build.tunnelsAt;
        BlockPos entranceAt = build.entranceAt;
        BoundingBox slice = build.slices.poll();
        if (slice != null) {
            template(level, "tunnels").placeInWorld(level, tunnelsAt, tunnelsAt, settings.copy().setBoundingBox(slice), level.random, FLAGS);
            return;
        }
        JsonObject entrance = PLAN.get().getAsJsonObject("entrance");
        JsonObject tunnels = PLAN.get().getAsJsonObject("tunnels");
        template(level, "entrance").placeInWorld(level, entranceAt, entranceAt, settings, level.random, FLAGS);
        Progress progress = Progress.of(server);
        BlockPos door = entranceAt.offset(turned(settings, vector(entrance.getAsJsonArray("mouth"))));
        progress.place(NAME, door);
        progress.place(HALL, tunnelsAt.offset(turned(settings, vector(tunnels.getAsJsonArray("stone")))));
        progress.place(HALL + Places.ENTRANCE, door);
        progress.rooms.put(HALL, List.of(template(level, "tunnels").getBoundingBox(settings, tunnelsAt), template(level, "entrance").getBoundingBox(settings, entranceAt)));
        JsonObject hall = tunnels.getAsJsonObject("hall");
        BoundingBox room = BoundingBox.fromCorners(tunnelsAt.offset(turned(settings, vector(hall.getAsJsonArray("from")))),
                tunnelsAt.offset(turned(settings, vector(hall.getAsJsonArray("to")))));
        progress.ward(NAME, room);
        Places.register(level, level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(LOST), new ChunkPos(room.getCenter()), 0,
                List.of(new HallRoom(room)));
        track(level, door.below());
        for (JsonElement mark : tunnels.getAsJsonArray("marks")) {
            track(level, tunnelsAt.offset(turned(settings, vector(mark.getAsJsonArray()))));
        }
        progress.setDirty();
        level.getChunkSource().removeRegionTicket(TICKET, build.ticket, build.radius, build.ticket);
        Standing.keep(server, NAME);
        Standing.keep(server, HALL);
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
