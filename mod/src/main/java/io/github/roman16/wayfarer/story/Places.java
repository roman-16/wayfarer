package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.HallStructure;
import io.github.roman16.wayfarer.ShaftPiece;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Places {
    private static final int CHECK_TICKS = 20;
    static final int VISIT_BLOCKS = 64;
    private static final int SPIRAL = 6;
    private static final int SURFACE = 8;
    private static final String FIRE_SEAT = "fire_";
    static final String ENTRANCE = "_entrance";
    private static final String AGAIN = "places_again";
    private static final int CLEAR_BLOCKS = 16;
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String AT = "@";
    static final String PERSON = "person:";

    private Places() {}

    enum Again {
        AHEAD,
        NEVER,
        THERE
    }

    private record Site(
            BiConsumer<MinecraftServer, String> build,
            @Nullable BiPredicate<MinecraftServer, String> ready,
            @Nullable BiConsumer<MinecraftServer, String> unbuild,
            @Nullable BiConsumer<MinecraftServer, String> forget) {
        Again again() {
            return forget != null ? Again.AHEAD : unbuild != null ? Again.THERE : Again.NEVER;
        }
    }

    private static final Map<String, Site> SITES = sites();

    private static Map<String, Site> sites() {
        Map<String, Site> sites = new HashMap<>();
        site(sites, (server, name) -> Tollbridge.begin(server), null, (server, name) -> Tollbridge.unbuild(server), null, Tollbridge.NAME);
        site(sites, (server, name) -> Diggers.begin(server), null, (server, name) -> Diggers.unbuild(server), null, Diggers.NAME, Diggers.HALL);
        site(sites, (server, name) -> FirstCamp.find(server), (server, name) -> FirstCamp.built(server), (server, name) -> FirstCamp.unbuild(server), null,
                FirstCamp.NAME);
        site(sites, (server, name) -> FirstVillage.find(server), null, (server, name) -> FirstVillage.unbuild(server), null, FirstVillage.NAME, FirstVillage.STONE);
        site(sites, (server, name) -> Ford.find(server), null, (server, name) -> Ford.unbuild(server), (server, name) -> Ford.forget(server), Ford.NAME, Ford.FAR,
                Ford.OUTPOST);
        site(sites, (server, name) -> Frost.find(server), null, Frost::unbuild, null, Frost.NAME, Frost.TRACKS, Frost.HALL, Frost.STONE);
        site(sites, (server, name) -> Frost.lake(server), null, Frost::unbuild, null, Frost.LAKE, Frost.LAKE_STONE);
        site(sites, (server, name) -> Marsh.find(server), null, (server, name) -> Marsh.unbuild(server), (server, name) -> Marsh.forget(server), Marsh.NAME,
                Marsh.HALL, Marsh.CELLAR);
        site(sites, (server, name) -> Mine.find(server), null, Mine::unbuild, null, Mine.NAME, Mine.DEEP, Mine.CART);
        site(sites, (server, name) -> ReadingHouse.find(server), null, (server, name) -> ReadingHouse.unbuild(server), (server, name) -> ReadingHouse.forget(server),
                ReadingHouse.NAME);
        site(sites, (server, name) -> Shipyard.find(server), Shipyard::ready, Shipyard::unbuild, null, Shipyard.NAME, Shipyard.WRECK);
        site(sites, (server, name) -> Shipyard.launch(server), Shipyard::ready, null, null, Shipyard.LAUNCH);
        site(sites, (server, name) -> Voyage.find(server), null, null, null, Voyage.FAR_SHORE);
        site(sites, (server, name) -> TideFolk.find(server), TideFolk::ready, (server, name) -> TideFolk.unbuild(server), null, TideFolk.NAME, TideFolk.HALL);
        site(sites, (server, name) -> DryCity.find(server), DryCity::ready, (server, name) -> DryCity.unbuild(server), null, DryCity.NAME, DryCity.STONE);
        site(sites, (server, name) -> WendCamp.find(server), (server, name) -> WendCamp.ready(server), (server, name) -> WendCamp.unbuild(server),
                (server, name) -> WendCamp.forget(server), WendCamp.NAME, WendCamp.CHAIR);
        site(sites, (server, name) -> SandSingers.find(server), SandSingers::ready, (server, name) -> SandSingers.unbuild(server), null, SandSingers.NAME,
                SandSingers.LAIR);
        site(sites, (server, name) -> Locks.find(server), Locks::ready, (server, name) -> Locks.unbuild(server), null, Locks.NAME, Locks.STONE);
        site(sites, Errands::find, Errands::ready, Errands::unbuild, null, Errands.MISSING, Errands.SETTLED, Errands.BURNING, Errands.OUTPOST, Errands.RITE,
                Errands.BRAM, Errands.CAIRNS, Errands.SEAL, Errands.DEN, Errands.BUNDLE, Errands.LAST_BUNDLE, Errands.NEVER_SLEEPS, Errands.NEST, Errands.FIRST_GATE);
        site(sites, Act3::find, Act3::ready, Act3::unbuild, null, Act3.HOLLOW, Act3.AIRFIELD, Act3.KEEPERS);
        site(sites, Act3Halls::find, Act3Halls::ready, Act3Halls::unbuild, null, Act3Halls.WARD, Act3Halls.WARD_STONE, Act3Halls.WARD_CHAMPION,
                Act3Halls.WARD_LINES, Act3Halls.MANSION_HALL, Act3Halls.MANSION, Act3Halls.MANSION_STAND, Act3Halls.SCRIBES, Act3Halls.SCRIBES_STONE,
                Act3Halls.GRAVEYARD, Act3Halls.GRAVEYARD_STONE);
        site(sites, (server, name) -> Caravan.headIn(server), (server, name) -> Caravan.standing(server).isPresent() || Caravan.heading(server), null, null,
                Caravan.NAME);
        return Map.copyOf(sites);
    }

    private static void site(Map<String, Site> sites, BiConsumer<MinecraftServer, String> build, @Nullable BiPredicate<MinecraftServer, String> ready,
            @Nullable BiConsumer<MinecraftServer, String> unbuild, @Nullable BiConsumer<MinecraftServer, String> forget, String... names) {
        for (String name : names) {
            sites.put(name, new Site(build, ready, unbuild, forget));
        }
    }

    record Seat(Vec3 at, float yaw) {}

    record Hall(BoundingBox box, BoundingBox shaft, BlockPos threshold, BlockPos stone, List<BoundingBox> rooms) {}

    static Hall raise(ServerLevel level, ResourceLocation id, BlockPos site, boolean landMade) {
        Structure registered = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        if (registered == null) {
            throw new IllegalStateException("the game has no structure " + id);
        }
        Structure hall = landMade && registered instanceof HallStructure halls ? halls.anywhere() : registered;
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
        ShaftPiece shaft = standing.getPieces().stream().filter(piece -> piece instanceof ShaftPiece).map(piece -> (ShaftPiece) piece).findFirst()
                .orElseThrow(() -> new IllegalStateException(hall + " at " + box + " has no stair"));
        BlockPos stone = BlockPos.betweenClosedStream(box).filter(pos -> level.getBlockState(pos).is(Wayfarer.CALLING_STONE)).findFirst()
                .map(BlockPos::immutable).orElseThrow(() -> new IllegalStateException(hall + " at " + box + " has no calling stone"));
        return new Hall(box, shaft.getBoundingBox(), shaft.threshold(), stone, standing.getPieces().stream().map(StructurePiece::getBoundingBox).toList());
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

    static Again again(MinecraftServer server, String name) {
        Site site = SITES.get(name);
        Again again = site == null ? Again.NEVER : site.again();
        if (again == Again.AHEAD) {
            clear(server, name);
            site.forget().accept(server, name);
            build(server, name);
        } else if (again == Again.THERE) {
            Progress progress = Progress.of(server);
            progress.scratch(AGAIN).putBoolean(name, true);
            progress.setDirty();
        }
        return again;
    }

    static void rebuild(MinecraftServer server, String name) {
        Site site = SITES.get(name);
        if (site == null || site.unbuild() == null) {
            throw new IllegalArgumentException("the story has no way to build " + name + " again");
        }
        clear(server, name);
        site.unbuild().accept(server, name);
        build(server, name);
    }

    private static void clear(MinecraftServer server, String name) {
        BoundingBox ward = Progress.of(server).wards.get(name);
        if (ward == null) {
            return;
        }
        Engine.overworld(server).getEntities((Entity) null, AABB.of(ward).inflate(CLEAR_BLOCKS),
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith(Steps.CLUE))).forEach(Entity::discard);
    }

    private static void rebuildUnseen(MinecraftServer server) {
        Progress progress = Progress.of(server);
        CompoundTag waiting = progress.scratch(AGAIN);
        if (waiting.isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        List<Unseen.Viewer> viewers = Unseen.viewers(level);
        int view = server.getPlayerList().getViewDistance();
        for (String name : List.copyOf(waiting.getAllKeys())) {
            if (progress.place(name).map(spot -> Unseen.beyond(viewers, Vec3.atCenterOf(spot), view)).orElse(true)) {
                waiting.remove(name);
                progress.setDirty();
                rebuild(server, name);
                LOGGER.info("the story place {} goes up again where it stood", name);
            }
        }
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
        if (name.startsWith(PERSON)) {
            return People.label(server, name.substring(PERSON.length()).split(AT, 2)[0]);
        }
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

    static Optional<BlockPos> aim(Progress progress, ServerPlayer player, String name) {
        if (name.startsWith(PERSON)) {
            String[] person = name.substring(PERSON.length()).split(AT, 2);
            return People.find(player.server, person[0]).filter(body -> body.level() == player.level()).map(Entity::blockPosition)
                    .or(() -> person.length > 1 ? seat(player.server, person[1]).map(seat -> BlockPos.containing(seat.at())) : Optional.empty());
        }
        return aim(progress, name).map(pos -> standable(player.serverLevel(), wayIn(progress, player, pos)));
    }

    static Optional<ResourceLocation> crowded(ServerLevel level, BlockPos site) {
        int clear = Balance.get().structures().clearBlocks();
        Optional<String> raised = Progress.of(level.getServer()).rooms.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(room -> room.inflatedBy(clear).isInside(site.atY(room.getCenter().getY()))))
                .map(Map.Entry::getKey).findFirst();
        if (raised.isPresent()) {
            return Optional.of(Wayfarer.id(raised.get()));
        }
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        BoundingBox around = new BoundingBox(site.getX() - clear, site.getY() - SURFACE, site.getZ() - clear, site.getX() + clear, level.getMaxBuildHeight(),
                site.getZ() + clear);
        for (int dx = -clear; dx <= clear; dx += SectionPos.SECTION_SIZE) {
            for (int dz = -clear; dz <= clear; dz += SectionPos.SECTION_SIZE) {
                Optional<ResourceLocation> standing = level.structureManager()
                        .startsForStructure(new ChunkPos(site.offset(dx, 0, dz)), structure -> !Optional.ofNullable(structures.getKey(structure))
                                .map(key -> key.getNamespace().equals(Wayfarer.MODID)).orElse(true))
                        .stream().filter(start -> start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().intersects(around)))
                        .map(start -> structures.getKey(start.getStructure())).findFirst();
                if (standing.isPresent()) {
                    return standing;
                }
            }
        }
        return Optional.empty();
    }

    private static BlockPos standable(ServerLevel level, BlockPos mark) {
        if (!level.isLoaded(mark) || !level.canSeeSky(mark.above())) {
            return mark;
        }
        return BlockPos.containing(Ground.stand(level, Vec3.atBottomCenterOf(mark)));
    }

    private static BlockPos wayIn(Progress progress, ServerPlayer player, BlockPos goal) {
        for (Map.Entry<String, List<BoundingBox>> hall : progress.rooms.entrySet()) {
            Optional<BlockPos> entrance = progress.place(hall.getKey() + ENTRANCE);
            if (entrance.isEmpty() || hall.getValue().stream().noneMatch(room -> room.isInside(goal))) {
                continue;
            }
            boolean inside = player.getBlockY() < entrance.get().getY() - 1 && hall.getValue().stream().anyMatch(room -> room.isInside(player.blockPosition()));
            if (!inside) {
                return entrance.get();
            }
        }
        return goal;
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
            rebuildUnseen(event.getServer());
            visit(event.getServer());
            Steps.keep(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Standing.reset();
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
