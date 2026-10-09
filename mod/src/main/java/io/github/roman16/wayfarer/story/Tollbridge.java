package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Burning;
import io.github.roman16.wayfarer.TollbridgeBell;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class Tollbridge {
    static final String NAME = "tollbridge";
    static final String QUEST = "ash";
    static final String CELLAR = "cellar";
    private static final String BURNS_UNTIL = "morning";
    static final String BELL = "bell";
    private static final List<String> BELL_STANDERS = List.of("agna", "hob", "pip");
    private static final int BELL_DIRECTIONS = 16;
    private static final int BELL_FAR = 4;
    private static final int BELL_NEAR = 2;
    static final String DEAD = Wayfarer.MODID + "_dead";
    private static final String SITE = "tollbridge_site";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/tollbridge.json");
    private static final String TOWER_PIECE = "bell_tower";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_tollbridge", Comparator.comparingLong(ChunkPos::toLong));
    private static final Direction[] AROUND = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int[] ANGLES = {0, 16, -16, 32, -32, 64, -64, 96, -96};
    private static final int[] NUDGES = {0, 32, 64, 128};
    private static final int BRIDGE_SPAN = 11;
    private static final int CHUNK = 16;
    private static final int DEAD_APART = 8;
    private static final int AT_DOORS = 4;
    private static final int EIGHTHS = 8;
    private static final int INDOORS = 5;
    private static final int DRY = 4;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int FOUNDATION = 8;
    private static final int GAP = 1;
    private static final int INNER = 24;
    private static final int LANE = 16;
    private static final int LANE_EVERY = 32;
    private static final int OUTER = 32;
    private static final int PARALLEL = 16;
    private static final int ROWS_PER_TICK = 8;
    private static final int SAMPLE = 8;
    private static final int SCORCH = 3;
    private static final int SEGMENT = 8;
    private static final int SQUARE = 12;
    private static final int STALLS = 4;
    private static final int STEEP = 4;
    private static final int STREET = 1;
    private static final int SUNK = 1024;
    private static final int TALLEST = 8;
    private static final int TOWER_TOP = 24;
    private static final int TRACKS = 8;
    private static final int TREE_REACH = 32;
    private static final int WELL = 6;
    @Nullable
    private static Build build;

    private Tollbridge() {}

    public static Optional<BlockPos> burning(MinecraftServer server) {
        Progress progress = Progress.of(server);
        int morning = Story.get().quest(QUEST).map(quest -> quest.steps().stream().map(Story.Step::id).toList().indexOf(BURNS_UNTIL)).orElse(0);
        return progress.place(NAME).filter(at -> !progress.done(QUEST) && progress.steps.getOrDefault(QUEST, 0) < morning);
    }

    private enum Stage {
        SEARCHING,
        WAITING,
        LOADING,
        GROUND,
        PIECES,
        PEOPLE
    }

    private record Site(int x, int z, boolean crowded, double score) {}

    private record Lot(String piece, int minX, int minZ, Rotation rotation, @Nullable Integer base, List<Long> paths, @Nullable BlockPos door) {}

    private record Placed(Lot lot, BlockPos origin, StructurePlaceSettings settings) {}

    private record Cell(BlockPos at, Direction way) {}

    private static final class Town {
        private final BlockPos centre;
        private final Direction front;
        private final int radius;
        private final RandomSource random;
        private final Map<Long, Integer> grounds = new HashMap<>();
        private final Set<Long> streets = new HashSet<>();
        private final Set<Long> square = new HashSet<>();
        private final List<Long> main = new ArrayList<>();
        private final List<Cell> approach = new ArrayList<>();
        private final List<Lot> lots = new ArrayList<>();
        private final List<BoundingBox> taken = new ArrayList<>();
        @Nullable
        private Cell bridge;
        @Nullable
        private Lot well;
        private boolean inn;
        private boolean smithy;

        private Town(BlockPos centre, Direction front, int radius, RandomSource random) {
            this.centre = centre;
            this.front = front;
            this.radius = radius;
            this.random = random;
        }
    }

    private static final class Build {
        private Stage stage = Stage.SEARCHING;
        @Nullable
        private CompletableFuture<List<Site>> searching;
        private List<Site> sites = List.of();
        @Nullable
        private Site site;
        private Unseen.Heights heights;
        private int waited;
        private ChunkPos ticket = ChunkPos.ZERO;
        private Town town;
        private int row;
        private int piece;
        private final List<Placed> placed = new ArrayList<>();
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
        switch (build.stage) {
            case SEARCHING -> search(server, level);
            case WAITING -> await(server, level);
            case LOADING -> {
                if (loaded(level)) {
                    planned(server, level);
                }
            }
            case GROUND -> ground(level);
            case PIECES -> pieces(level);
            case PEOPLE -> people(server, level);
        }
    }

    private static int radius() {
        return Balance.get().tollbridge().radiusBlocks();
    }

    private static void search(MinecraftServer server, ServerLevel level) {
        Optional<BlockPos> chosen = Progress.of(server).place(SITE);
        if (chosen.isPresent()) {
            build.heights = new Unseen.Heights(level);
            build.sites = List.of(new Site(chosen.get().getX(), chosen.get().getZ(), false, 0));
            build.stage = Stage.WAITING;
            return;
        }
        if (build.searching == null) {
            BlockPos walked = Progress.of(server).place(Steps.OWN_CAMP)
                    .orElseGet(() -> Engine.players(server).stream().findFirst().map(ServerPlayer::blockPosition).orElse(level.getSharedSpawnPos()));
            BlockPos last = Engine.at(server, Prologue.LAST_CAMP);
            int ahead = Balance.get().tollbridge().aheadBlocks();
            Home.Aim aim = Home.aim(walked, new Vec3(walked.getX() - last.getX(), 0, walked.getZ() - last.getZ()).normalize(), ahead);
            BlockPos camp = aim.from();
            double heading = Math.atan2(aim.way().z, aim.way().x);
            build.heights = new Unseen.Heights(level);
            Unseen.Heights heights = build.heights;
            Neighbours neighbours = new Neighbours(level);
            build.searching = CompletableFuture.supplyAsync(() -> sites(heights, neighbours, camp, heading, ahead), Util.backgroundExecutor());
            return;
        }
        if (!build.searching.isDone()) {
            return;
        }
        build.sites = build.searching.join();
        if (build.sites.stream().allMatch(Site::crowded)) {
            LOGGER.warn("every site for Tollbridge has another place within {} blocks; it stands at the least crowded", Balance.get().tollbridge().clearBlocks());
        }
        build.stage = Stage.WAITING;
    }

    private static List<Site> sites(Unseen.Heights heights, Neighbours neighbours, BlockPos camp, double heading, int ahead) {
        int radius = radius();
        List<Site> sites = new ArrayList<>();
        for (int angle : ANGLES) {
            double way = heading + Math.toRadians(angle);
            for (int nudge : NUDGES) {
                int x = camp.getX() + (int) Math.round(Math.cos(way) * (ahead + nudge));
                int z = camp.getZ() + (int) Math.round(Math.sin(way) * (ahead + nudge));
                List<Integer> samples = new ArrayList<>();
                int wet = 0;
                for (int dx = -radius; dx <= radius; dx += SAMPLE) {
                    for (int dz = -radius; dz <= radius; dz += SAMPLE) {
                        if (dx * dx + dz * dz > radius * radius) {
                            continue;
                        }
                        samples.add(heights.surface(x + dx, z + dz));
                        if (heights.wet(x + dx, z + dz)) {
                            wet++;
                        }
                    }
                }
                double mean = samples.stream().mapToInt(Integer::intValue).average().orElse(0);
                double spread = Math.sqrt(samples.stream().mapToDouble(height -> (height - mean) * (height - mean)).average().orElse(0));
                boolean river = river(heights, x, z, radius);
                boolean crowded = neighbours.near(heights, x, z);
                double sunk = (wet * DRY > samples.size() || heights.wet(x, z) ? SUNK : 0) + (crowded ? SUNK * 2 : 0);
                sites.add(new Site(x, z, crowded,
                        -sunk - spread * 4 - wet + (river ? radius / (double) SAMPLE : 0) - Math.abs(angle) / 8.0 - Math.abs(nudge) / 16.0));
            }
        }
        sites.sort(Comparator.comparingDouble(Site::score).reversed());
        return sites;
    }

    private static boolean river(Unseen.Heights heights, int x, int z, int radius) {
        for (Direction way : AROUND) {
            int run = 0;
            boolean banked = false;
            for (int distance = SQUARE; distance <= radius; distance += SAMPLE / 2) {
                boolean wet = heights.wet(x + way.getStepX() * distance, z + way.getStepZ() * distance);
                if (wet) {
                    run += SAMPLE / 2;
                } else {
                    if (banked && run > 0 && run <= BRIDGE_SPAN) {
                        return true;
                    }
                    banked = true;
                    run = 0;
                }
            }
        }
        return false;
    }

    private static final class Neighbours {
        private final ChunkGeneratorStructureState state;
        private final BiomeSource biomes;
        private final Climate.Sampler sampler;
        private final List<Holder<StructureSet>> sets;
        private final int reach;

        private Neighbours(ServerLevel level) {
            this.state = level.getChunkSource().getGeneratorState();
            this.biomes = level.getChunkSource().getGenerator().getBiomeSource();
            this.sampler = level.getChunkSource().randomState().sampler();
            this.sets = state.possibleStructureSets().stream()
                    .filter(set -> set.value().structures().stream().anyMatch(entry -> standing(entry.structure()))).toList();
            this.reach = Math.ceilDiv(radius() + Balance.get().tollbridge().clearBlocks(), CHUNK);
        }

        private boolean near(Unseen.Heights heights, int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            for (Holder<StructureSet> set : sets) {
                for (int i = cx - reach; i <= cx + reach; i++) {
                    for (int j = cz - reach; j <= cz + reach; j++) {
                        if (!set.value().placement().isStructureChunk(state, i, j)) {
                            continue;
                        }
                        int bx = i * CHUNK + CHUNK / 2;
                        int bz = j * CHUNK + CHUNK / 2;
                        var biome = biomes.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(heights.surface(bx, bz)), QuartPos.fromBlock(bz), sampler);
                        for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                            if (standing(entry.structure()) && entry.structure().value().biomes().contains(biome)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private static boolean standing(Holder<Structure> structure) {
            GenerationStep.Decoration step = structure.value().step();
            boolean ours = structure.unwrapKey().map(key -> key.location().getNamespace().equals(Wayfarer.MODID)).orElse(false);
            return ours || step != GenerationStep.Decoration.UNDERGROUND_STRUCTURES && step != GenerationStep.Decoration.UNDERGROUND_DECORATION
                    && step != GenerationStep.Decoration.STRONGHOLDS;
        }
    }

    private static void await(MinecraftServer server, ServerLevel level) {
        build.waited++;
        boolean late = build.waited >= Balance.get().tollbridge().waitSeconds();
        int view = server.getPlayerList().getViewDistance();
        for (Site site : build.sites) {
            if (late || Unseen.hidden(build.heights, Unseen.viewers(level), targets(site), view, false)) {
                build.site = site;
                Progress.of(server).place(SITE, new BlockPos(site.x(), 0, site.z()));
                build.ticket = new ChunkPos(new BlockPos(site.x(), 0, site.z()));
                level.getChunkSource().addRegionTicket(TICKET, build.ticket, ticketRadius(), build.ticket);
                build.stage = Stage.LOADING;
                return;
            }
        }
    }

    private static int ticketRadius() {
        return Math.ceilDiv(radius() + CHUNK, CHUNK);
    }

    private static List<Vec3> targets(Site site) {
        int radius = radius();
        List<Vec3> targets = new ArrayList<>();
        targets.add(new Vec3(site.x() + 0.5, build.heights.surface(site.x(), site.z()) + TOWER_TOP, site.z() + 0.5));
        for (Direction way : AROUND) {
            int x = site.x() + way.getStepX() * radius;
            int z = site.z() + way.getStepZ() * radius;
            targets.add(new Vec3(x + 0.5, build.heights.surface(x, z) + TALLEST, z + 0.5));
        }
        return targets;
    }

    private static boolean loaded(ServerLevel level) {
        int reach = radius() + SAMPLE;
        for (int x = (build.site.x() - reach) >> 4; x <= (build.site.x() + reach) >> 4; x++) {
            for (int z = (build.site.z() - reach) >> 4; z <= (build.site.z() + reach) >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void planned(MinecraftServer server, ServerLevel level) {
        Town town = plan(server, level, build.site);
        long homes = town.lots.stream().filter(lot -> !lot.paths().isEmpty()).count();
        List<Site> others = build.sites.stream().filter(site -> site != build.site).toList();
        if (homes >= Balance.get().tollbridge().leastBuildings() || others.isEmpty()) {
            build.town = town;
            build.stage = Stage.GROUND;
            return;
        }
        LOGGER.info("only {} buildings fit Tollbridge at {} {}; it tries the next site", homes, build.site.x(), build.site.z());
        level.getChunkSource().removeRegionTicket(TICKET, build.ticket, ticketRadius(), build.ticket);
        Progress.of(server).places.remove(SITE);
        Progress.of(server).setDirty();
        build.sites = others;
        build.site = null;
        build.waited = 0;
        build.stage = Stage.WAITING;
    }

    private static Town plan(MinecraftServer server, ServerLevel level, Site site) {
        BlockPos camp = Progress.of(server).place(Steps.OWN_CAMP).orElse(Engine.at(server, Prologue.LAST_CAMP));
        Direction front = Direction.getNearest((double) camp.getX() - site.x(), 0, (double) camp.getZ() - site.z());
        Town town = new Town(new BlockPos(site.x(), 0, site.z()), front, radius(),
                RandomSource.create(level.getSeed() ^ BlockPos.asLong(site.x(), 0, site.z())));
        Direction side = front.getClockWise();
        BlockPos centre = town.centre;
        Lot tower = lot(level, TOWER_PIECE, centre.relative(front, size(TOWER_PIECE).get(2).getAsInt() / 2), front, null);
        town.lots.add(tower);
        town.taken.add(box(tower));
        for (int dx = -SQUARE; dx <= SQUARE; dx++) {
            for (int dz = -SQUARE; dz <= SQUARE; dz++) {
                long key = BlockPos.asLong(centre.getX() + dx, 0, centre.getZ() + dz);
                if (!wet(level, town, centre.getX() + dx, centre.getZ() + dz) && !inside(town.taken, centre.getX() + dx, centre.getZ() + dz, 0)) {
                    town.square.add(key);
                }
            }
        }
        town.well = settle(level, town, "well", centre.relative(front, WELL + 4).relative(side, WELL), front, true);
        for (int stall = 0; stall < STALLS; stall++) {
            settle(level, town, "stall", centre.relative(side.getOpposite(), SQUARE - 4).relative(front, stall * 4 - (STALLS - 1) * 2), side, true);
        }
        List<List<Cell>> streets = new ArrayList<>();
        for (Direction way : AROUND) {
            List<Cell> street = walk(level, town, centre.relative(way, SQUARE + 1), way, true, town.radius - SQUARE - 1);
            if (way == front) {
                town.approach.addAll(street);
            }
            street.forEach(cell -> town.main.add(cell.at().asLong()));
            streets.add(street);
        }
        List<List<Cell>> lanes = branch(level, town, streets, 3);
        streets.addAll(lanes);
        streets.addAll(branch(level, town, lanes, 2));
        tollHouse(level, town);
        for (Direction way : AROUND) {
            List<Cell> edge = new ArrayList<>();
            for (int along = -SQUARE; along <= SQUARE; along++) {
                edge.add(new Cell(centre.relative(way, SQUARE - STREET - 1).relative(way.getClockWise(), along), way.getClockWise()));
            }
            build(level, town, edge);
        }
        List<List<Cell>> kept = new ArrayList<>();
        for (List<Cell> street : streets) {
            if (build(level, town, street) > 0 || town.main.contains(street.isEmpty() ? 0L : street.getFirst().at().asLong())) {
                kept.add(street);
            }
        }
        town.streets.clear();
        kept.forEach(street -> street.forEach(cell -> pave(level, town, cell)));
        return town;
    }

    private static List<List<Cell>> branch(ServerLevel level, Town town, List<List<Cell>> from, int chance) {
        List<List<Cell>> lanes = new ArrayList<>();
        for (List<Cell> street : from) {
            for (int index = LANE / 2; index < street.size(); index += LANE_EVERY) {
                Cell cell = street.get(index);
                for (Direction turn : new Direction[] {cell.way().getClockWise(), cell.way().getCounterClockWise()}) {
                    if (town.random.nextInt(4) < chance) {
                        lanes.add(walk(level, town, cell.at().relative(turn, STREET + 1), turn, false, LANE + town.random.nextInt(LANE)));
                    }
                }
            }
        }
        return lanes;
    }

    private static List<Cell> walk(ServerLevel level, Town town, BlockPos start, Direction way, boolean main, int budget) {
        List<Cell> cells = new ArrayList<>();
        Set<Long> own = new HashSet<>();
        BlockPos at = start;
        Direction going = way;
        int segment = SEGMENT + town.random.nextInt(SEGMENT);
        for (int step = 0; step < budget; step++) {
            if (outside(town, at, 0) || town.square.contains(at.asLong()) || inside(town.taken, at.getX(), at.getZ(), 0)
                    || step > 0 && town.streets.contains(at.asLong()) && !own.contains(at.asLong())) {
                break;
            }
            if (wet(level, town, at.getX(), at.getZ())) {
                if (!main || town.bridge != null || cells.isEmpty()) {
                    break;
                }
                BlockPos beyond = bridge(level, town, cells.getLast().at(), going);
                if (beyond == null) {
                    break;
                }
                at = beyond;
                continue;
            }
            if (step >= SEGMENT / 2 && crowded(town, own, at, going)) {
                break;
            }
            Cell cell = new Cell(at, going);
            cells.add(cell);
            own.addAll(pave(level, town, cell));
            if (--segment <= 0) {
                segment = SEGMENT + town.random.nextInt(SEGMENT);
                Direction turn = town.random.nextBoolean() ? going.getClockWise() : going.getCounterClockWise();
                if (town.random.nextInt(3) == 0 && open(level, town, at, turn) && away(town, at, turn)) {
                    going = turn;
                }
            }
            BlockPos next = at.relative(going);
            int climb = Math.abs(height(level, town, next) - height(level, town, at));
            if (!wet(level, town, next.getX(), next.getZ()) && climb > 1) {
                Direction left = going.getCounterClockWise();
                Direction right = going.getClockWise();
                Direction turn = open(level, town, at, left) && away(town, at, left) ? left : open(level, town, at, right) && away(town, at, right) ? right : null;
                if (turn != null) {
                    going = turn;
                    next = at.relative(going);
                } else if (climb > 2) {
                    break;
                }
            }
            at = next;
        }
        return cells;
    }

    private static List<Long> pave(ServerLevel level, Town town, Cell cell) {
        List<Long> paved = new ArrayList<>();
        for (int across = -STREET; across <= STREET; across++) {
            BlockPos at = cell.at().relative(cell.way().getClockWise(), across);
            if (!wet(level, town, at.getX(), at.getZ())) {
                town.streets.add(at.asLong());
                paved.add(at.asLong());
            }
        }
        return paved;
    }

    private static boolean crowded(Town town, Set<Long> own, BlockPos at, Direction way) {
        for (int apart = STREET * 2 + 2; apart <= PARALLEL; apart++) {
            for (Direction side : new Direction[] {way.getClockWise(), way.getCounterClockWise()}) {
                long key = at.relative(side, apart).asLong();
                if (town.streets.contains(key) && !own.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean open(ServerLevel level, Town town, BlockPos at, Direction way) {
        BlockPos next = at.relative(way);
        return !wet(level, town, next.getX(), next.getZ()) && Math.abs(height(level, town, next) - height(level, town, at)) <= 1;
    }

    private static boolean away(Town town, BlockPos at, Direction way) {
        BlockPos next = at.relative(way, SEGMENT);
        return next.distManhattan(town.centre.atY(next.getY())) > at.distManhattan(town.centre.atY(at.getY())) - SEGMENT / 2;
    }

    @Nullable
    private static BlockPos bridge(ServerLevel level, Town town, BlockPos bank, Direction way) {
        int span = 0;
        BlockPos at = bank.relative(way);
        while (wet(level, town, at.getX(), at.getZ()) && span <= BRIDGE_SPAN) {
            span++;
            at = at.relative(way);
        }
        int length = spec("bridge").getAsJsonArray("size").get(2).getAsInt();
        int width = spec("bridge").getAsJsonArray("size").get(0).getAsInt();
        if (span == 0 || span > BRIDGE_SPAN || outside(town, at.relative(way, 2), 0)) {
            return null;
        }
        BlockPos start = bank.relative(way, 1 - (length - span) / 2);
        BlockPos end = start.relative(way, length - 1);
        BlockPos left = start.relative(way.getCounterClockWise(), width / 2);
        BlockPos right = end.relative(way.getClockWise(), width / 2);
        Rotation rotation = facing(way);
        Lot lot = new Lot("bridge", Math.min(left.getX(), right.getX()), Math.min(left.getZ(), right.getZ()), rotation, height(level, town, bank) - 1, List.of(), null);
        town.lots.add(lot);
        town.taken.add(box(lot));
        town.bridge = new Cell(bank, way);
        return end.relative(way);
    }

    private static int build(ServerLevel level, Town town, List<Cell> street) {
        int built = 0;
        for (boolean clockwise : new boolean[] {true, false}) {
            int index = 0;
            while (index < street.size()) {
                Cell cell = street.get(index);
                Direction side = clockwise ? cell.way().getClockWise() : cell.way().getCounterClockWise();
                BlockPos edge = cell.at().relative(side, STREET + 1 + GAP);
                String piece = choose(town, cell.at());
                while (piece != null && settle(level, town, piece, edge, side.getOpposite(), false) == null) {
                    piece = smaller(town, cell.at(), piece);
                }
                if (piece == null) {
                    index++;
                    continue;
                }
                if (piece.equals("inn")) {
                    town.inn = true;
                } else if (piece.equals("smithy")) {
                    town.smithy = true;
                }
                index += width(piece) - 2 * margin(piece) + GAP + 1 + town.random.nextInt(3);
                built++;
            }
        }
        return built;
    }

    private static String choose(Town town, BlockPos at) {
        int distance = at.distManhattan(town.centre.atY(at.getY()));
        if (!town.inn && distance < INNER * 2) {
            return "inn";
        }
        if (!town.smithy && distance < INNER * 2) {
            return "smithy";
        }
        List<String> lots = kinds(at, town).toList();
        List<String> variants = pieces(lots.get(town.random.nextInt(lots.size())));
        return variants.get(town.random.nextInt(variants.size()));
    }

    @Nullable
    private static String smaller(Town town, BlockPos at, String than) {
        List<String> smaller = kinds(at, town).distinct().flatMap(kind -> pieces(kind).stream()).filter(piece -> area(piece) < area(than)).toList();
        return smaller.isEmpty() ? null : smaller.get(town.random.nextInt(smaller.size()));
    }

    private static Stream<String> kinds(BlockPos at, Town town) {
        int distance = at.distManhattan(town.centre.atY(at.getY()));
        return PLAN.get().getAsJsonArray("lots").asList().stream().map(JsonElement::getAsString).filter(kind -> {
            String ring = PLAN.get().getAsJsonObject("kinds").getAsJsonObject(kind).get("ring").getAsString();
            return ring.equals("any") || ring.equals("inner") && distance < INNER || ring.equals("outer") && distance > OUTER;
        });
    }

    private static List<String> pieces(String kind) {
        return PLAN.get().getAsJsonObject("kinds").getAsJsonObject(kind).getAsJsonArray("pieces").asList().stream().map(JsonElement::getAsString).toList();
    }

    private static int area(String piece) {
        return width(piece) * size(piece).get(2).getAsInt();
    }

    private static void tollHouse(ServerLevel level, Town town) {
        List<Cell> near = town.bridge != null ? List.of(town.bridge) : town.approach.isEmpty() ? List.of() : List.of(town.approach.getLast());
        for (Cell cell : near) {
            for (int back = 2; back <= SEGMENT; back++) {
                for (Direction side : new Direction[] {cell.way().getClockWise(), cell.way().getCounterClockWise()}) {
                    BlockPos centre = cell.at().relative(cell.way(), -back).relative(side, STREET + 1 + GAP);
                    if (settle(level, town, "toll_house", centre, side.getOpposite(), false) != null) {
                        return;
                    }
                }
            }
        }
    }

    @Nullable
    private static Lot settle(ServerLevel level, Town town, String piece, BlockPos edge, Direction front, boolean square) {
        Lot lot = lot(level, piece, edge, front, town);
        if (lot == null || !fits(level, town, piece, box(lot), square)) {
            return null;
        }
        town.lots.add(lot);
        town.taken.add(box(lot));
        return lot;
    }

    private static Lot lot(ServerLevel level, String piece, BlockPos edge, Direction front, @Nullable Town town) {
        JsonArray size = size(piece);
        int width = size.get(0).getAsInt();
        int depth = size.get(2).getAsInt();
        Rotation rotation = facing(front);
        Direction across = front.getClockWise();
        BlockPos frontEdge = edge;
        BlockPos a = frontEdge.relative(across, -(width / 2));
        BlockPos b = frontEdge.relative(across, width - 1 - width / 2).relative(front.getOpposite(), depth - 1);
        int minX = Math.min(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        BlockPos door = door(level, piece, minX, minZ, rotation);
        List<Long> paths = new ArrayList<>();
        if (town != null) {
            BlockPos step = door.relative(front);
            for (int reach = 0; reach < margin(piece) + GAP + 2 && !town.streets.contains(step.asLong()) && !town.square.contains(step.asLong()); reach++) {
                paths.add(step.asLong());
                step = step.relative(front);
            }
        }
        return new Lot(piece, minX, minZ, rotation, null, paths, door);
    }

    private static boolean fits(ServerLevel level, Town town, String piece, BoundingBox box, boolean square) {
        if (outside(town, new BlockPos(box.minX(), 0, box.minZ()), 0) || outside(town, new BlockPos(box.maxX(), 0, box.maxZ()), 0)
                || outside(town, new BlockPos(box.minX(), 0, box.maxZ()), 0) || outside(town, new BlockPos(box.maxX(), 0, box.minZ()), 0)) {
            return false;
        }
        int margin = margin(piece);
        BoundingBox inner = box.inflatedBy(-margin, 0, -margin);
        for (Lot other : town.lots) {
            BoundingBox full = box(other);
            BoundingBox house = full.inflatedBy(-margin(other.piece()), 0, -margin(other.piece()));
            if (house.inflatedBy(GAP).intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ()) || full.intersects(inner.minX(), inner.minZ(), inner.maxX(), inner.maxZ())) {
                return false;
            }
        }
        List<Integer> heights = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                long key = BlockPos.asLong(x, 0, z);
                boolean house = margin == 0 || x >= box.minX() + margin - 1 && x <= box.maxX() - margin + 1 && z >= box.minZ() + margin - 1
                        && z <= box.maxZ() - margin + 1;
                if (house && (town.streets.contains(key) || town.square.contains(key) != square) || wet(level, town, x, z)) {
                    return false;
                }
                if (x >= box.minX() + margin && x <= box.maxX() - margin && z >= box.minZ() + margin && z <= box.maxZ() - margin) {
                    heights.add(height(level, town, new BlockPos(x, 0, z)));
                }
            }
        }
        heights.sort(Integer::compare);
        int median = heights.get(heights.size() / 2);
        return heights.getLast() - median <= STEEP && median - heights.getFirst() <= STEEP;
    }

    private static BlockPos door(ServerLevel level, String piece, int minX, int minZ, Rotation rotation) {
        JsonArray at = piece.equals(TOWER_PIECE) ? tower().getAsJsonArray("door") : spec(piece).getAsJsonArray("door");
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        BlockPos origin = origin(level, piece, settings, minX, 0, minZ);
        return StructureTemplate.calculateRelativePosition(settings, new BlockPos(at.get(0).getAsInt(), 0, at.get(1).getAsInt())).offset(origin);
    }

    private static BlockPos origin(ServerLevel level, String piece, StructurePlaceSettings settings, int minX, int base, int minZ) {
        BoundingBox local = template(level, piece).getBoundingBox(settings, BlockPos.ZERO);
        return new BlockPos(minX - local.minX(), base - local.minY(), minZ - local.minZ());
    }

    private static BoundingBox box(Lot lot) {
        JsonArray size = size(lot.piece());
        boolean turned = lot.rotation() == Rotation.CLOCKWISE_90 || lot.rotation() == Rotation.COUNTERCLOCKWISE_90;
        int width = size.get(turned ? 2 : 0).getAsInt();
        int depth = size.get(turned ? 0 : 2).getAsInt();
        return new BoundingBox(lot.minX(), 0, lot.minZ(), lot.minX() + width - 1, 0, lot.minZ() + depth - 1);
    }

    private static Rotation facing(Direction front) {
        return Arrays.stream(Rotation.values()).filter(rotation -> rotation.rotate(Direction.SOUTH) == front).findFirst().orElseThrow();
    }

    private static JsonArray size(String piece) {
        return piece.equals(TOWER_PIECE) ? tower().getAsJsonArray("size") : spec(piece).getAsJsonArray("size");
    }

    private static int margin(String piece) {
        return piece.equals(TOWER_PIECE) ? tower().get("margin").getAsInt() : spec(piece).get("margin").getAsInt();
    }

    private static int width(String piece) {
        return size(piece).get(0).getAsInt();
    }

    private static StructureTemplate template(ServerLevel level, String piece) {
        return level.getStructureManager().getOrCreate(Wayfarer.id("tollbridge/" + piece));
    }

    private static boolean outside(Town town, BlockPos at, int within) {
        int dx = at.getX() - town.centre.getX();
        int dz = at.getZ() - town.centre.getZ();
        return dx * dx + dz * dz > (town.radius - within) * (town.radius - within);
    }

    private static boolean inside(List<BoundingBox> boxes, int x, int z, int by) {
        return boxes.stream().anyMatch(box -> box.inflatedBy(by).intersects(x, z, x, z));
    }

    private static boolean wet(ServerLevel level, Town town, int x, int z) {
        int top = ground(level, x, z);
        return !level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty();
    }

    private static int height(ServerLevel level, Town town, BlockPos at) {
        return town.grounds.computeIfAbsent(BlockPos.asLong(at.getX(), 0, at.getZ()), key -> ground(level, at.getX(), at.getZ()));
    }

    private static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        while (growth(level.getBlockState(new BlockPos(x, top, z)))) {
            top--;
        }
        return top;
    }

    private static boolean growth(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static void ground(ServerLevel level) {
        Town town = build.town;
        JsonObject materials = PLAN.get().getAsJsonObject("materials");
        int reach = town.radius + SAMPLE;
        int from = town.centre.getZ() - reach;
        for (int done = 0; done < ROWS_PER_TICK && build.row <= reach * 2; done++, build.row++) {
            int z = from + build.row;
            for (int x = town.centre.getX() - reach; x <= town.centre.getX() + reach; x++) {
                long key = BlockPos.asLong(x, 0, z);
                boolean street = town.streets.contains(key);
                boolean square = town.square.contains(key);
                if (street || square || inside(town.taken, x, z, 2)) {
                    clear(level, x, z);
                }
                int top = ground(level, x, z);
                BlockPos surface = new BlockPos(x, top, z);
                if (!level.getFluidState(surface).isEmpty()) {
                    continue;
                }
                boolean burnt = street || square || burnt(town, x, z);
                if (burnt) {
                    thaw(level, surface);
                }
                BlockState state = street ? pick(materials.getAsJsonArray("street"), surface)
                        : square ? pick(materials.getAsJsonArray("square"), surface)
                        : burnt && level.getBlockState(surface).is(Blocks.GRASS_BLOCK) && Math.floorMod(surface.hashCode(), SCORCH) == 0
                        ? pick(materials.getAsJsonArray("ash"), surface) : null;
                if (state != null) {
                    level.setBlock(surface, state, FLAGS);
                }
            }
        }
        if (build.row > reach * 2) {
            build.stage = Stage.PIECES;
        }
    }

    private static boolean burnt(Town town, int x, int z) {
        if (inside(town.taken, x, z, SCORCH)) {
            return true;
        }
        for (int dx = -STREET - 1; dx <= STREET + 1; dx++) {
            for (int dz = -STREET - 1; dz <= STREET + 1; dz++) {
                long key = BlockPos.asLong(x + dx, 0, z + dz);
                if (town.streets.contains(key) || town.square.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void thaw(ServerLevel level, BlockPos surface) {
        if (level.getBlockState(surface.above()).is(Blocks.SNOW)) {
            level.setBlock(surface.above(), Blocks.AIR.defaultBlockState(), FLAGS);
        }
        BlockState ground = level.getBlockState(surface);
        if (ground.hasProperty(SnowyDirtBlock.SNOWY) && ground.getValue(SnowyDirtBlock.SNOWY)) {
            level.setBlock(surface, ground.setValue(SnowyDirtBlock.SNOWY, false), FLAGS);
        }
    }

    private static void clear(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int floor = ground(level, x, z);
        for (int y = top; y > floor && y > top - TREE_REACH; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    private static BlockState pick(JsonArray palette, BlockPos at) {
        int total = palette.asList().stream().mapToInt(entry -> entry.getAsJsonArray().get(1).getAsInt()).sum();
        int roll = Math.floorMod(Long.hashCode(at.asLong() * 0x9E3779B97F4A7C15L) >> 8, total);
        for (JsonElement entry : palette) {
            roll -= entry.getAsJsonArray().get(1).getAsInt();
            if (roll < 0) {
                return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getAsJsonArray().get(0).getAsString())).defaultBlockState();
            }
        }
        return Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static void pieces(ServerLevel level) {
        Town town = build.town;
        if (build.piece < town.lots.size()) {
            Lot lot = town.lots.get(build.piece++);
            build.placed.add(place(level, lot));
            JsonArray street = PLAN.get().getAsJsonObject("materials").getAsJsonArray("street");
            for (long path : lot.paths()) {
                BlockPos column = BlockPos.of(path);
                clear(level, column.getX(), column.getZ());
                BlockPos surface = new BlockPos(column.getX(), ground(level, column.getX(), column.getZ()), column.getZ());
                if (level.getFluidState(surface).isEmpty()) {
                    level.setBlock(surface, pick(street, surface), FLAGS);
                }
            }
            return;
        }
        smoke(level, town);
        build.stage = Stage.PEOPLE;
    }

    private static Placed place(ServerLevel level, Lot lot) {
        StructureTemplate template = template(level, lot.piece());
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation());
        BoundingBox box = box(lot);
        int margin = margin(lot.piece());
        List<Integer> heights = new ArrayList<>();
        for (int x = box.minX() + margin; x <= box.maxX() - margin; x++) {
            for (int z = box.minZ() + margin; z <= box.maxZ() - margin; z++) {
                heights.add(ground(level, x, z));
            }
        }
        heights.sort(Integer::compare);
        int ground = lot.piece().equals(TOWER_PIECE) ? tower().get("ground").getAsInt() : 0;
        int floor = lot.base() != null ? lot.base() + 1 : heights.get(heights.size() / 2);
        int base = lot.base() != null ? lot.base() : floor - ground;
        int top = base + template.getSize().getY();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                boolean inner = x >= box.minX() + margin && x <= box.maxX() - margin && z >= box.minZ() + margin && z <= box.maxZ() - margin;
                int column = ground(level, x, z);
                BlockState surface = level.getBlockState(new BlockPos(x, column, z));
                for (int y = floor + 1; y <= Math.max(top, column); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir() && (inner || y <= column || growth(level.getBlockState(pos)))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                if (!inner && column > floor && lot.base() == null) {
                    level.setBlock(new BlockPos(x, floor, z), surface, FLAGS);
                }
            }
        }
        BlockPos origin = origin(level, lot.piece(), settings, box.minX(), base, box.minZ());
        template.placeInWorld(level, origin, origin, settings, level.random, FLAGS);
        for (int x = box.minX(); x <= box.maxX() && lot.base() == null; x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                boolean inner = x >= box.minX() + margin && x <= box.maxX() - margin && z >= box.minZ() + margin && z <= box.maxZ() - margin;
                foundation(level, x, z, base, floor, inner ? Blocks.COBBLESTONE : Blocks.DIRT);
            }
        }
        return new Placed(lot, origin, settings);
    }

    private static void foundation(ServerLevel level, int x, int z, int base, int floor, Block fill) {
        int lowest = Integer.MIN_VALUE;
        for (int y = base; y <= floor; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                lowest = y;
                break;
            }
        }
        if (lowest == Integer.MIN_VALUE) {
            return;
        }
        for (int depth = 1; depth <= FOUNDATION; depth++) {
            BlockPos below = new BlockPos(x, lowest - depth, z);
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.canBeReplaced()) {
                return;
            }
            level.setBlock(below, fill.defaultBlockState(), FLAGS);
        }
    }

    private static void smoke(ServerLevel level, Town town) {
        List<Placed> hearths = new ArrayList<>(build.placed.stream().filter(placed -> PLAN.get().getAsJsonObject("pieces").has(placed.lot().piece())
                && spec(placed.lot().piece()).has("hearth")).toList());
        for (int fire = 0; fire < PLAN.get().get("smoke").getAsInt() && !hearths.isEmpty(); fire++) {
            Placed placed = hearths.remove(town.random.nextInt(hearths.size()));
            JsonArray hearth = spec(placed.lot().piece()).getAsJsonArray("hearth");
            BlockPos at = world(placed, hearth.get(0).getAsInt(), hearth.get(1).getAsInt(), hearth.get(2).getAsInt());
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof CampfireBlock) {
                level.setBlock(at, state.setValue(CampfireBlock.SIGNAL_FIRE, true), FLAGS);
                level.setBlock(at.below(), Blocks.HAY_BLOCK.defaultBlockState(), FLAGS);
            }
        }
    }

    private static BlockPos world(Placed placed, int x, int y, int z) {
        return StructureTemplate.calculateRelativePosition(placed.settings(), new BlockPos(x, y, z)).offset(placed.origin());
    }

    private static BlockPos world(Placed placed, JsonArray at) {
        return world(placed, at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt());
    }

    private static void people(MinecraftServer server, ServerLevel level) {
        Town town = build.town;
        Progress progress = Progress.of(server);
        Placed tower = build.placed.getFirst();
        Rotation turn = tower.lot().rotation();
        JsonArray bell = tower().getAsJsonArray("bell");
        BlockPos bellAt = surface(level, world(tower, bell.get(0).getAsInt(), 0, bell.get(1).getAsInt())).above();
        Direction facing = turn.rotate(Direction.byName(tower().get("bell_facing").getAsString()));
        level.setBlock(bellAt, Wayfarer.TOLLBRIDGE_BELL.get().defaultBlockState().setValue(TollbridgeBell.FACING, facing).setValue(TollbridgeBell.FALLEN, true), FLAGS);
        progress.place(BELL, bellAt);
        JsonArray seat = tower().getAsJsonArray("bell_seat");
        BlockPos seatAt = surface(level, world(tower, seat.get(0).getAsInt(), 0, seat.get(1).getAsInt())).above();
        progress.place("seat_" + BELL, seatAt);
        progress.yaws.put("seat_" + BELL, (float) yaw(seatAt, bellAt));
        List<BlockPos> taken = new ArrayList<>(List.of(seatAt));
        for (String person : BELL_STANDERS) {
            standing(level, bellAt, taken).ifPresent(at -> {
                taken.add(at);
                progress.place("seat_" + BELL + "_" + person, at);
                progress.yaws.put("seat_" + BELL + "_" + person, (float) yaw(at, bellAt));
            });
        }
        BlockPos fire = world(tower, tower().getAsJsonArray("fire"));
        progress.place(CELLAR, fire);
        BoundingBox ward = template(level, TOWER_PIECE).getBoundingBox(tower.settings(), tower.origin());
        progress.ward(NAME, new BoundingBox(Math.min(ward.minX(), bellAt.getX()), Math.min(ward.minY(), bellAt.getY()), Math.min(ward.minZ(), bellAt.getZ()),
                Math.max(ward.maxX(), bellAt.getX()), Math.max(ward.maxY(), bellAt.getY()), Math.max(ward.maxZ(), bellAt.getZ())));
        tower().getAsJsonObject("seats").entrySet().forEach(entry -> {
            BlockPos at = world(tower, entry.getValue().getAsJsonArray());
            progress.place("seat_" + entry.getKey(), at);
            progress.yaws.put("seat_" + entry.getKey(), (float) yaw(at, fire));
        });
        survivors(server);
        dead(level, town);
        build.placed.stream().filter(placed -> placed.lot().piece().equals("inn")).findFirst()
                .ifPresent(inn -> Clues.marker(level, world(inn, PLAN.get().getAsJsonArray("strongbox")), "strongbox"));
        Clues.door(level, world(tower, tower().getAsJsonArray("clue")), "cellar");
        tracks(level, town);
        progress.place(NAME, surface(level, town.centre));
        progress.places.remove(SITE);
        int reach = town.radius + SAMPLE;
        Burning.hold(level, new BoundingBox(town.centre.getX() - reach, level.getMinBuildHeight(), town.centre.getZ() - reach,
                town.centre.getX() + reach, level.getMaxBuildHeight(), town.centre.getZ() + reach));
        level.getChunkSource().removeRegionTicket(TICKET, build.ticket, ticketRadius(), build.ticket);
        progress.setDirty();
        LOGGER.info("Tollbridge stands at {} with {} buildings along {} blocks of street{}", town.centre, build.placed.size(), town.streets.size(),
                town.bridge != null ? " and its bridge" : "");
        build = null;
    }

    static void survivors(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        tower().getAsJsonObject("seats").entrySet().forEach(entry -> Places.seat(server, entry.getKey())
                .ifPresent(seat -> People.person(level, entry.getKey(), seat.at(), seat.yaw(), entry.getValue().getAsJsonArray().get(3).getAsString())));
    }

    private static void dead(ServerLevel level, Town town) {
        JsonObject dead = PLAN.get().getAsJsonObject("dead");
        List<String> skins = new ArrayList<>(dead.getAsJsonArray("others").asList().stream().map(JsonElement::getAsString).toList());
        List<BlockPos> laid = new ArrayList<>();
        if (town.well != null) {
            BoundingBox box = box(town.well);
            lay(level, town, surface(level, new BlockPos(box.minX() - 1, 0, box.minZ() + (box.maxZ() - box.minZ()) / 2)).above(), dead.get("well").getAsString(),
                    "well", laid);
        }
        List<Placed> homes = new ArrayList<>(build.placed.stream().filter(placed -> !placed.lot().paths().isEmpty()).toList());
        Collections.shuffle(homes, new Random(town.random.nextLong()));
        if (!homes.isEmpty()) {
            lay(level, town, surface(level, BlockPos.of(homes.getFirst().lot().paths().getFirst())).above(), dead.get("doorway").getAsString(), "doorway", laid);
        }
        int indoors = skins.size() * INDOORS / EIGHTHS;
        for (int round = 0; round < 2 && indoors > 0; round++) {
            for (Placed home : homes) {
                List<BlockPos> floor = floor(level, home, laid);
                if (indoors > 0 && !floor.isEmpty() && (round == 0 || town.random.nextBoolean())) {
                    lay(level, town, floor.get(town.random.nextInt(floor.size())), skins.removeFirst(), null, laid);
                    indoors--;
                }
            }
        }
        int doors = skins.size() * AT_DOORS / EIGHTHS;
        for (Placed home : homes.stream().skip(1).toList()) {
            if (doors-- <= 0) {
                break;
            }
            lay(level, town, surface(level, BlockPos.of(home.lot().paths().getLast())).above(), skins.removeFirst(), null, laid);
        }
        List<Long> streets = new ArrayList<>(town.main);
        Collections.shuffle(streets, new Random(town.random.nextLong()));
        for (long street : streets) {
            BlockPos cell = surface(level, BlockPos.of(street)).above();
            if (skins.isEmpty()) {
                break;
            }
            if (laid.stream().allMatch(body -> body.distManhattan(cell) >= DEAD_APART)) {
                lay(level, town, cell, skins.removeFirst(), null, laid);
            }
        }
    }

    private static List<BlockPos> floor(ServerLevel level, Placed home, List<BlockPos> laid) {
        BoundingBox inner = box(home.lot()).inflatedBy(-margin(home.lot().piece()) - 1, 0, -margin(home.lot().piece()) - 1);
        int y = home.origin().getY() + 1;
        List<BlockPos> cells = new ArrayList<>();
        for (int x = inner.minX(); x <= inner.maxX(); x++) {
            for (int z = inner.minZ(); z <= inner.maxZ(); z++) {
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                        && laid.stream().allMatch(body -> body.distManhattan(at) > 2)) {
                    cells.add(at);
                }
            }
        }
        return cells;
    }

    private static void lay(ServerLevel level, Town town, BlockPos at, String skin, @Nullable String clue, List<BlockPos> laid) {
        JsonArray poses = PLAN.get().getAsJsonArray("dead_poses");
        String pose = clue != null ? PLAN.get().get("found_pose").getAsString() : poses.get(town.random.nextInt(poses.size())).getAsString();
        Entity body = People.spawn(level, skin, Vec3.atBottomCenterOf(at), town.random.nextFloat() * 360, pose, DEAD);
        if (clue != null) {
            body.addTag(Steps.CLUE + clue);
        }
        laid.add(at);
    }

    private static void tracks(ServerLevel level, Town town) {
        List<Cell> street = town.approach;
        for (int print = 0; print < TRACKS && 2 + print * 2 < street.size(); print++) {
            Cell cell = street.get(2 + print * 2);
            Clues.print(level, surface(level, cell.at().relative(cell.way().getClockWise(), print % 2)).above(), "tracks");
        }
    }

    private static Optional<BlockPos> standing(ServerLevel level, BlockPos bell, List<BlockPos> taken) {
        for (int radius = BELL_NEAR; radius <= BELL_FAR; radius++) {
            for (int step = 0; step < BELL_DIRECTIONS; step++) {
                double angle = Math.PI * 2 * step / BELL_DIRECTIONS;
                BlockPos at = surface(level, bell.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius))).above();
                if (Math.abs(at.getY() - bell.getY()) <= 1 && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && taken.stream().allMatch(other -> other.distManhattan(at) >= 2)) {
                    return Optional.of(at);
                }
            }
        }
        return Optional.empty();
    }

    private static BlockPos surface(ServerLevel level, BlockPos column) {
        return column.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1);
    }

    private static double yaw(BlockPos from, BlockPos to) {
        return Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX())) - 90;
    }

    private static JsonObject tower() {
        return PLAN.get().getAsJsonObject("tower");
    }

    private static JsonObject spec(String name) {
        return PLAN.get().getAsJsonObject("pieces").getAsJsonObject(name);
    }
}
