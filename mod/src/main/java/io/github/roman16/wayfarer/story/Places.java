package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.ShaftPiece;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
    private static final int SPIRAL = 6;
    private static final String FIRE_SEAT = "fire_";

    private Places() {}

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
        switch (name) {
            case Tollbridge.NAME -> Tollbridge.begin(server);
            case Diggers.NAME, Diggers.HALL -> Diggers.begin(server);
            case FirstCamp.NAME -> FirstCamp.find(server);
            case FirstVillage.NAME, FirstVillage.STONE -> FirstVillage.find(server);
            case Ford.NAME -> Ford.find(server);
            case Frost.NAME, Frost.TRACKS, Frost.HALL, Frost.STONE -> Frost.find(server);
            case Frost.LAKE, Frost.LAKE_STONE -> Frost.lake(server);
            case Marsh.NAME, Marsh.HALL, Marsh.CELLAR -> Marsh.find(server);
            case Mine.NAME, Mine.DEEP, Mine.CART -> Mine.find(server);
            case ReadingHouse.NAME -> ReadingHouse.find(server);
            case Shipyard.NAME, Shipyard.WRECK -> Shipyard.find(server);
            case Shipyard.LAUNCH -> Shipyard.launch(server);
            case Voyage.FAR_SHORE -> Voyage.find(server);
            case TideFolk.NAME, TideFolk.HALL -> TideFolk.find(server);
            case DryCity.NAME, DryCity.STONE -> DryCity.find(server);
            case WendCamp.NAME, WendCamp.CHAIR -> WendCamp.find(server);
            case SandSingers.NAME, SandSingers.LAIR -> SandSingers.find(server);
            case Locks.NAME, Locks.STONE -> Locks.find(server);
            case Errands.MISSING, Errands.SETTLED, Errands.BURNING, Errands.OUTPOST, Errands.RITE, Errands.BRAM, Errands.CAIRNS, Errands.SEAL, Errands.DEN,
                    Errands.BUNDLE, Errands.LAST_BUNDLE, Errands.NEVER_SLEEPS, Errands.NEST, Errands.FIRST_GATE -> Errands.find(server, name);
            case Act3.HOLLOW, Act3.AIRFIELD, Act3.KEEPERS -> Act3.find(server, name);
            case Act3Halls.WARD, Act3Halls.WARD_STONE, Act3Halls.WARD_CHAMPION, Act3Halls.WARD_LINES, Act3Halls.MANSION_HALL, Act3Halls.MANSION, Act3Halls.MANSION_STAND, Act3Halls.SCRIBES,
                    Act3Halls.SCRIBES_STONE, Act3Halls.GRAVEYARD, Act3Halls.GRAVEYARD_STONE -> Act3Halls.find(server, name);
            default -> throw new IllegalArgumentException("the story has no way to build " + name);
        }
    }

    static String label(MinecraftServer server, String name) {
        return "wayfarer.place." + (name.equals(Tollbridge.NAME) && !Progress.of(server).named(Tollbridge.NAME) ? Tollbridge.BURNING : name);
    }

    static boolean ready(MinecraftServer server, String name) {
        return switch (name) {
            case FirstCamp.NAME -> FirstCamp.built(server);
            case Shipyard.NAME, Shipyard.WRECK, Shipyard.LAUNCH -> Shipyard.ready(server, name);
            case TideFolk.NAME, TideFolk.HALL -> TideFolk.ready(server, name);
            case DryCity.NAME, DryCity.STONE -> DryCity.ready(server, name);
            case WendCamp.NAME, WendCamp.CHAIR -> WendCamp.ready(server);
            case SandSingers.NAME, SandSingers.LAIR -> SandSingers.ready(server, name);
            case Locks.NAME, Locks.STONE -> Locks.ready(server, name);
            case Errands.MISSING, Errands.SETTLED, Errands.BURNING, Errands.OUTPOST, Errands.RITE, Errands.BRAM, Errands.CAIRNS, Errands.SEAL, Errands.DEN,
                    Errands.BUNDLE, Errands.LAST_BUNDLE, Errands.NEVER_SLEEPS, Errands.NEST, Errands.FIRST_GATE -> Errands.ready(server, name);
            case Act3.HOLLOW, Act3.AIRFIELD, Act3.KEEPERS -> Act3.ready(server, name);
            case Act3Halls.WARD, Act3Halls.WARD_STONE, Act3Halls.WARD_CHAMPION, Act3Halls.WARD_LINES, Act3Halls.MANSION_HALL, Act3Halls.MANSION, Act3Halls.MANSION_STAND, Act3Halls.SCRIBES,
                    Act3Halls.SCRIBES_STONE, Act3Halls.GRAVEYARD, Act3Halls.GRAVEYARD_STONE -> Act3Halls.ready(server, name);
            default -> Progress.of(server).place(name).isPresent();
        };
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
