package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.ShaftPiece;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Places {
    private static final int CHECK_TICKS = 20;
    static final int VISIT_BLOCKS = 64;
    private static final int SPIRAL = 6;
    private static final String FIRE_SEAT = "fire_";

    private Places() {}

    private record Site(BiConsumer<MinecraftServer, String> build, @Nullable BiPredicate<MinecraftServer, String> ready) {}

    private static final Map<String, Site> SITES = sites();

    private static Map<String, Site> sites() {
        Map<String, Site> sites = new HashMap<>();
        site(sites, (server, name) -> Tollbridge.begin(server), null, Tollbridge.NAME);
        site(sites, (server, name) -> Diggers.begin(server), null, Diggers.NAME, Diggers.HALL);
        site(sites, (server, name) -> FirstCamp.find(server), (server, name) -> FirstCamp.built(server), FirstCamp.NAME);
        site(sites, (server, name) -> FirstVillage.find(server), null, FirstVillage.NAME, FirstVillage.STONE);
        site(sites, (server, name) -> Ford.find(server), null, Ford.NAME);
        site(sites, (server, name) -> Frost.find(server), null, Frost.NAME, Frost.TRACKS, Frost.HALL, Frost.STONE);
        site(sites, (server, name) -> Frost.lake(server), null, Frost.LAKE, Frost.LAKE_STONE);
        site(sites, (server, name) -> Marsh.find(server), null, Marsh.NAME, Marsh.HALL, Marsh.CELLAR);
        site(sites, (server, name) -> Mine.find(server), null, Mine.NAME, Mine.DEEP, Mine.CART);
        site(sites, (server, name) -> ReadingHouse.find(server), null, ReadingHouse.NAME);
        site(sites, (server, name) -> Shipyard.find(server), Shipyard::ready, Shipyard.NAME, Shipyard.WRECK);
        site(sites, (server, name) -> Shipyard.launch(server), Shipyard::ready, Shipyard.LAUNCH);
        site(sites, (server, name) -> Voyage.find(server), null, Voyage.FAR_SHORE);
        site(sites, (server, name) -> TideFolk.find(server), TideFolk::ready, TideFolk.NAME, TideFolk.HALL);
        site(sites, (server, name) -> DryCity.find(server), DryCity::ready, DryCity.NAME, DryCity.STONE);
        site(sites, (server, name) -> WendCamp.find(server), (server, name) -> WendCamp.ready(server), WendCamp.NAME, WendCamp.CHAIR);
        site(sites, (server, name) -> SandSingers.find(server), SandSingers::ready, SandSingers.NAME, SandSingers.LAIR);
        site(sites, (server, name) -> Locks.find(server), Locks::ready, Locks.NAME, Locks.STONE);
        site(sites, Errands::find, Errands::ready, Errands.MISSING, Errands.SETTLED, Errands.BURNING, Errands.OUTPOST, Errands.RITE, Errands.BRAM,
                Errands.CAIRNS, Errands.SEAL, Errands.DEN, Errands.BUNDLE, Errands.LAST_BUNDLE, Errands.NEVER_SLEEPS, Errands.NEST, Errands.FIRST_GATE);
        site(sites, Act3::find, Act3::ready, Act3.HOLLOW, Act3.AIRFIELD, Act3.KEEPERS);
        site(sites, Act3Halls::find, Act3Halls::ready, Act3Halls.WARD, Act3Halls.WARD_STONE, Act3Halls.WARD_CHAMPION, Act3Halls.WARD_LINES, Act3Halls.MANSION_HALL,
                Act3Halls.MANSION, Act3Halls.MANSION_STAND, Act3Halls.SCRIBES, Act3Halls.SCRIBES_STONE, Act3Halls.GRAVEYARD, Act3Halls.GRAVEYARD_STONE);
        site(sites, (server, name) -> Caravan.headIn(server), (server, name) -> Caravan.standing(server).isPresent() || Caravan.heading(server), Caravan.NAME);
        return Map.copyOf(sites);
    }

    private static void site(Map<String, Site> sites, BiConsumer<MinecraftServer, String> build, @Nullable BiPredicate<MinecraftServer, String> ready,
            String... names) {
        for (String name : names) {
            sites.put(name, new Site(build, ready));
        }
    }

    record Seat(Vec3 at, float yaw) {}

    record Hall(BoundingBox box, BoundingBox shaft, BlockPos stair, BlockPos stone) {}

    static Hall raise(ServerLevel level, ResourceLocation id, BlockPos site) {
        Structure hall = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        if (hall == null) {
            throw new IllegalStateException("the game has no structure " + id);
        }
        StructureStart start = StructureStart.INVALID_START;
        ChunkPos middle = new ChunkPos(site);
        for (int ring = 0; ring <= SPIRAL && !start.isValid(); ring++) {
            for (int dx = -ring; dx <= ring && !start.isValid(); dx++) {
                for (int dz = -ring; dz <= ring && !start.isValid(); dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        start = hall.generate(level.registryAccess(), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
                                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(middle.x + dx, middle.z + dz), 0,
                                level, any -> true);
                    }
                }
            }
        }
        if (!start.isValid()) {
            throw new IllegalStateException(hall + " would stand nowhere near " + site);
        }
        StructureStart standing = start;
        BoundingBox box = standing.getBoundingBox();
        ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ())),
                new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()))).forEach(chunk -> standing.placeInChunk(level,
                        level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                        new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(),
                                chunk.getMaxBlockZ()), chunk));
        BoundingBox shaft = standing.getPieces().stream().filter(piece -> piece instanceof ShaftPiece).map(StructurePiece::getBoundingBox).findFirst()
                .orElseThrow(() -> new IllegalStateException(hall + " at " + box + " has no stair"));
        BlockPos stone = BlockPos.betweenClosedStream(box).filter(pos -> level.getBlockState(pos).is(Wayfarer.CALLING_STONE)).findFirst()
                .map(BlockPos::immutable).orElseThrow(() -> new IllegalStateException(hall + " at " + box + " has no calling stone"));
        return new Hall(box, shaft, new BlockPos(shaft.getCenter().getX(), shaft.maxY() + 1, shaft.getCenter().getZ()), stone);
    }

    static void empty(ServerLevel level, BlockPos stone) {
        if (level.getBlockEntity(stone) instanceof CallingStoneBlockEntity calling) {
            calling.hold(Optional.empty());
        }
    }

    static void build(MinecraftServer server, String name) {
        if (ready(server, name)) {
            return;
        }
        Site site = SITES.get(name);
        if (site == null) {
            throw new IllegalArgumentException("the story has no way to build " + name);
        }
        site.build().accept(server, name);
    }

    static boolean buildable(String name) {
        return SITES.containsKey(name);
    }

    static void unbuild(MinecraftServer server, String name) {
        switch (name) {
            case Diggers.NAME, Diggers.HALL -> Diggers.unbuild(server);
            case FirstCamp.NAME -> FirstCamp.unbuild(server);
            case FirstVillage.NAME, FirstVillage.STONE -> FirstVillage.unbuild(server);
            case Ford.NAME, Ford.OUTPOST -> Ford.unbuild(server);
            case Frost.NAME, Frost.HALL, Frost.LAKE -> Frost.unbuild(server, name);
            case Marsh.NAME, Marsh.HALL -> Marsh.unbuild(server);
            case Shipyard.NAME, Shipyard.WRECK -> Shipyard.unbuild(server, name);
            default -> throw new IllegalArgumentException("the story has no way to build " + name + " again");
        }
    }

    static boolean forget(MinecraftServer server, String name) {
        switch (name) {
            case Ford.NAME, Ford.FAR, Ford.OUTPOST -> Ford.forget(server);
            case Marsh.NAME, Marsh.HALL, Marsh.CELLAR -> Marsh.forget(server);
            default -> {
                return false;
            }
        }
        build(server, name);
        return true;
    }

    static boolean rebuilds(String name) {
        return switch (name) {
            case Diggers.NAME, Diggers.HALL, FirstCamp.NAME, FirstVillage.NAME, FirstVillage.STONE, Ford.NAME, Ford.OUTPOST, Frost.NAME, Frost.HALL, Frost.LAKE,
                    Marsh.NAME, Marsh.HALL, Shipyard.NAME, Shipyard.WRECK -> true;
            default -> false;
        };
    }

    private static void visit(MinecraftServer server) {
        Progress progress = Progress.of(server);
        boolean found = false;
        for (String name : Story.get().places()) {
            Optional<BlockPos> pos = progress.place(name);
            if (!progress.visited.contains(name) && pos.isPresent() && Engine.players(server).stream().anyMatch(player -> player.level().dimension() == Level.OVERWORLD
                    && Steps.horizontal(player.blockPosition(), pos.get()) <= (double) VISIT_BLOCKS * VISIT_BLOCKS)) {
                progress.visited.add(name);
                found = true;
            }
        }
        if (found) {
            progress.setDirty();
            Journal.syncAll(server);
        }
    }

    static String label(MinecraftServer server, String name) {
        return "wayfarer.place." + (name.equals(Tollbridge.NAME) && !Progress.of(server).named(Tollbridge.NAME) ? Tollbridge.BURNING : name);
    }

    static boolean ready(MinecraftServer server, String name) {
        Site site = SITES.get(name);
        return site != null && site.ready() != null ? site.ready().test(server, name) : Progress.of(server).place(name).isPresent();
    }

    static int arrival(String name) {
        return switch (name) {
            case Ford.NAME -> Balance.get().ford().noticeBlocks();
            case Frost.NAME -> Balance.get().frost().houseBlocks();
            case Frost.HALL -> Balance.get().frost().stairBlocks();
            case Frost.LAKE -> Balance.get().frost().shoreBlocks();
            case Frost.STONE -> Balance.get().frost().stoneBlocks();
            case Marsh.NAME -> Balance.get().marsh().noticeBlocks();
            case Marsh.HALL -> Balance.get().marsh().hallBlocks();
            case Marsh.CELLAR -> Balance.get().marsh().cellarBlocks();
            case Mine.NAME -> Balance.get().frost().stairBlocks();
            case Mine.DEEP -> Balance.get().frost().mineWallBlocks();
            case TideFolk.HALL -> Balance.get().tide().stoneBlocks();
            case Act3Halls.WARD_LINES -> Balance.get().story().nearBlocks();
            case Voyage.FAR_SHORE -> Balance.get().voyage().landfallBlocks();
            default -> Balance.get().story().arriveBlocks();
        };
    }

    static int leaving(String name) {
        return switch (name) {
            case Frost.STONE -> Balance.get().frost().leaveBlocks();
            case SandSingers.LAIR -> Balance.get().worm().fleeBlocks();
            default -> Balance.get().prologue().awayBlocks();
        };
    }

    static Optional<BlockPos> place(MinecraftServer server, String name) {
        return Progress.of(server).place(name);
    }

    static Optional<BlockPos> aim(Progress progress, String name) {
        return switch (name) {
            case Marsh.NAME -> progress.place(name).or(() -> Marsh.site(progress));
            default -> progress.place(name);
        };
    }

    static Optional<Seat> seat(MinecraftServer server, String name) {
        if (name.startsWith(FIRE_SEAT)) {
            return Caravan.fireSeat(server, Integer.parseInt(name.substring(FIRE_SEAT.length())));
        }
        Progress progress = Progress.of(server);
        return progress.place("seat_" + name).map(pos -> new Seat(Vec3.atBottomCenterOf(pos), progress.yaws.getOrDefault("seat_" + name, 0f)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS == 0 && Engine.running(event.getServer())) {
            Tollbridge.tick(event.getServer());
            Caravan.tick(event.getServer());
            Bell.tick(event.getServer());
            Diggers.tick(event.getServer());
            FirstCamp.tick(event.getServer());
            FirstVillage.tick(event.getServer());
            Ford.tick(event.getServer());
            Frost.tick(event.getServer());
            Marsh.tick(event.getServer());
            Mine.tick(event.getServer());
            ReadingHouse.tick(event.getServer());
            Shipyard.tick(event.getServer());
            TideFolk.tick(event.getServer());
            DryCity.tick(event.getServer());
            WendCamp.tick(event.getServer());
            SandSingers.tick(event.getServer());
            Locks.tick(event.getServer());
            Errands.tick(event.getServer());
            Act3.tick(event.getServer());
            Act3Halls.tick(event.getServer());
            Voyage.tick(event.getServer());
            Standing.tick(event.getServer());
            visit(event.getServer());
            Steps.keep(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Tollbridge.reset();
        Caravan.reset();
        Diggers.reset();
        Ford.reset();
        Shipyard.reset();
        Voyage.reset();
        TideFolk.reset();
        DryCity.reset();
        SandSingers.reset();
        Locks.reset();
        Errands.reset();
        Act3Halls.reset();
    }
}
