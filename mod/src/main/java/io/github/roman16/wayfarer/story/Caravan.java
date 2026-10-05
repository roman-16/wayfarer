package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

public final class Caravan {
    static final String NAME = "caravan";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final JsonArray HOUSEHOLDS = Prologue.json("/wayfarer/story.json").getAsJsonArray("caravan");
    private static final int[] ANGLES = {0, 15, -15, 30, -30, 45, -45, 60, -60};
    private static final int AROUND = 32;
    private static final int BUSH = 4;
    private static final int CHUNKS = 2;
    private static final int CROWDED = 2;
    private static final Set<Block> DANGERS = Set.of(Blocks.BEE_NEST, Blocks.BEEHIVE, Blocks.CACTUS, Blocks.MAGMA_BLOCK, Blocks.POWDER_SNOW,
            Blocks.SWEET_BERRY_BUSH, Blocks.FIRE, Blocks.SOUL_FIRE);
    private static final int EMBERS = 2;
    private static final int EXPOSED = 8;
    private static final int FARTHER = 16;
    private static final int FIRE_SHIFT = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int FOUNDATION = 4;
    private static final int GAP = 2;
    private static final int HOLLOW = 8;
    private static final int LOW = 4;
    private static final int NEAR_WATER = 8;
    private static final int[] NUDGES = {0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180};
    private static final int REACH = 8;
    private static final int REACH_OUT = 16;
    private static final int RING = 12;
    private static final int SAFE = 16;
    private static final int SAFE_ABOVE = 8;
    private static final int SAFE_BELOW = 4;
    private static final int SEARCH_SPAN = 128;
    private static final int SEARCH_STEP = 32;
    private static final int STEEP = 4;
    private static final int SURVEY = 16;
    private static final int TALLEST = 8;
    private static final int TRIES = 4;
    private static final int AWAY = 32;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_caravan", Comparator.comparingLong(ChunkPos::toLong));
    @Nullable
    private static Camp camp;

    private Caravan() {}

    private enum Stage {
        LOADING,
        SEARCHING,
        STANDING
    }

    private record Placed(BoundingBox box, BlockPos centre, BlockPos front) {}

    private record Spot(BlockPos centre, int distance, int score) {}

    private record Survey(@Nullable String hard, List<String> wishes) {}

    private record Fallback(BlockPos centre, List<String> wishes) {}

    private static final class Camp {
        private BlockPos centre;
        private final float facing;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<BlockPos> rejected = new ArrayList<>();
        private final List<Fallback> fallbacks = new ArrayList<>();
        private boolean settling;
        private int tries;
        private List<BoundingBox> boxes = new ArrayList<>();
        @Nullable
        private CompletableFuture<BlockPos> pending;
        private Stage stage = Stage.SEARCHING;
        private ChunkPos ticket = ChunkPos.ZERO;
        private Unseen.Heights heights;
        private final BlockPos from;

        private Camp(ServerLevel level, BlockPos from, float facing) {
            this.level = level;
            this.centre = from;
            this.from = from;
            this.facing = facing;
        }
    }

    static void begin(MinecraftServer server) {
        if (camp != null) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        BlockPos last = Engine.at(server, Prologue.LAST_CAMP);
        BlockPos town = Engine.at(server, Tollbridge.NAME);
        float facing = (float) Math.toDegrees(Math.atan2(town.getZ() - last.getZ(), town.getX() - last.getX())) - 90;
        camp = new Camp(level, town, facing);
        camp.heights = new Unseen.Heights(level);
    }

    static void reset() {
        camp = null;
    }

    static void tick(MinecraftServer server) {
        if (camp == null) {
            return;
        }
        ServerLevel level = camp.level;
        switch (camp.stage) {
            case SEARCHING -> search(server);
            case LOADING -> {
                if (loaded(level)) {
                    load(server, level);
                }
            }
            case STANDING -> guard(level, camp);
        }
    }

    private static void search(MinecraftServer server) {
        if (camp.pending == null) {
            int view = server.getPlayerList().getViewDistance();
            List<Unseen.Viewer> viewers = Unseen.viewers(camp.level);
            Unseen.Heights heights = camp.heights;
            List<BlockPos> rejected = List.copyOf(camp.rejected);
            BlockPos from = camp.from;
            float facing = camp.facing;
            camp.pending = CompletableFuture.supplyAsync(() -> {
                int nearest = (view + CHUNKS + 2) * 16;
                List<Spot> spots = new ArrayList<>();
                for (int distance = nearest; distance <= nearest + SEARCH_SPAN; distance += SEARCH_STEP) {
                    for (int angle : ANGLES) {
                        Vec3 way = Vec3.directionFromRotation(0, facing + angle);
                        BlockPos centre = BlockPos.containing(from.getX() + way.x * distance, 0, from.getZ() + way.z * distance);
                        if (rejected.stream().anyMatch(spot -> spot.distSqr(centre.atY(spot.getY())) < AWAY * AWAY * 4) || misfit(heights, centre) != null) {
                            continue;
                        }
                        spots.add(new Spot(centre.atY(heights.surface(centre.getX(), centre.getZ())), distance, score(heights, centre) - (distance - nearest) / FARTHER));
                    }
                }
                spots.sort(Comparator.comparingInt(Spot::score).reversed());
                for (Spot spot : spots) {
                    Vec3 top = new Vec3(spot.centre().getX() + 0.5, spot.centre().getY() + TALLEST, spot.centre().getZ() + 0.5);
                    if (Unseen.hidden(heights, viewers, List.of(top), view, true)) {
                        return spot.centre();
                    }
                }
                return null;
            }, Util.backgroundExecutor());
            return;
        }
        if (!camp.pending.isDone()) {
            return;
        }
        BlockPos found = camp.pending.join();
        camp.pending = null;
        if (found != null) {
            camp.centre = found;
            camp.ticket = new ChunkPos(found);
            camp.level.getChunkSource().addRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
            camp.stage = Stage.LOADING;
        } else if (!camp.fallbacks.isEmpty()) {
            settle();
        }
    }

    private static void settle() {
        Fallback best = camp.fallbacks.stream().min(Comparator.comparingInt(fallback -> fallback.wishes().size())).orElseThrow();
        camp.centre = best.centre();
        camp.settling = true;
        camp.ticket = new ChunkPos(best.centre());
        camp.level.getChunkSource().addRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
        camp.stage = Stage.LOADING;
    }

    private static boolean loaded(ServerLevel level) {
        for (int x = camp.ticket.x - CHUNKS; x <= camp.ticket.x + CHUNKS; x++) {
            for (int z = camp.ticket.z - CHUNKS; z <= camp.ticket.z + CHUNKS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void load(MinecraftServer server, ServerLevel level) {
        Survey survey = survey(level, camp.centre);
        String hard = survey.hard() == null ? build(level, camp) : survey.hard();
        level.getChunkSource().removeRegionTicket(TICKET, camp.ticket, CHUNKS, camp.ticket);
        if (hard == null && (camp.settling || survey.wishes().isEmpty())) {
            camp.stage = Stage.STANDING;
            Progress.of(server).place(NAME, camp.centre);
            Journal.syncAll(server);
            LOGGER.info("the caravan made camp at {}", camp.centre);
            return;
        }
        takeDown(level);
        if (hard == null) {
            camp.fallbacks.add(new Fallback(camp.centre, survey.wishes()));
        }
        camp.rejected.add(camp.centre);
        camp.tries++;
        camp.stage = Stage.SEARCHING;
        if (camp.tries >= TRIES && !camp.fallbacks.isEmpty()) {
            settle();
        }
    }

    private static void takeDown(ServerLevel level) {
        List<Map.Entry<BlockPos, BlockState>> originals = new ArrayList<>(camp.originals.entrySet());
        for (int index = originals.size() - 1; index >= 0; index--) {
            level.setBlock(originals.get(index).getKey(), originals.get(index).getValue(), FLAGS);
        }
        camp.originals.clear();
        camp.boxes.clear();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (camp != null && !camp.boxes.isEmpty() && event.getEntity() instanceof LightningBolt bolt && event.getLevel() == camp.level
                && bolt.blockPosition().distSqr(camp.centre.atY(bolt.getBlockY())) < (REACH_OUT + SAFE) * (REACH_OUT + SAFE)) {
            event.setCanceled(true);
        }
    }

    @Nullable
    private static String misfit(Unseen.Heights heights, BlockPos centre) {
        return heights.wet(centre.getX(), centre.getZ()) ? "in water" : null;
    }

    private static int score(Unseen.Heights heights, BlockPos centre) {
        int ground = heights.surface(centre.getX(), centre.getZ());
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int total = 0;
        boolean water = false;
        List<BlockPos> around = ring(centre, AROUND);
        around.removeFirst();
        for (BlockPos point : around) {
            int y = heights.surface(point.getX(), point.getZ());
            low = Math.min(low, y);
            high = Math.max(high, y);
            total += y;
            water |= heights.wet(point.getX(), point.getZ());
        }
        int score = water ? NEAR_WATER : 0;
        if (ground * around.size() < total - LOW * around.size()) {
            score -= HOLLOW;
        }
        if (ground > high + LOW) {
            score -= EXPOSED;
        }
        List<BlockPos> camp = ring(centre, RING);
        int flatLow = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).min().orElse(ground);
        int flatHigh = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).max().orElse(ground);
        long soaked = camp.stream().filter(point -> heights.wet(point.getX(), point.getZ())).count();
        return score - (flatHigh - flatLow) - (int) soaked * NEAR_WATER;
    }

    private static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> points = new ArrayList<>(List.of(centre));
        for (int step = 0; step < 8; step++) {
            double angle = Math.toRadians(step * 45);
            points.add(centre.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius)));
        }
        return points;
    }

    private static Survey survey(ServerLevel level, BlockPos centre) {
        String danger = danger(level, centre);
        if (danger != null) {
            return new Survey(danger, List.of());
        }
        String inside = neighbour(level, centre, REACH_OUT);
        if (inside != null) {
            return new Survey("%s in the way".formatted(inside), List.of());
        }
        List<String> wishes = new ArrayList<>();
        String neighbour = neighbour(level, centre, REACH_OUT + AWAY);
        if (neighbour != null) {
            wishes.add("%s within %d blocks".formatted(neighbour, AWAY));
        }
        int columns = 0;
        int trees = 0;
        int water = 0;
        for (int x = -SURVEY; x <= SURVEY; x++) {
            for (int z = -SURVEY; z <= SURVEY; z++) {
                if (x * x + z * z > SURVEY * SURVEY) {
                    continue;
                }
                columns++;
                int top = ground(level, centre.getX() + x, centre.getZ() + z);
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1 - top > BUSH) {
                    trees++;
                } else if (!level.getBlockState(new BlockPos(centre.getX() + x, top, centre.getZ() + z)).getFluidState().isEmpty()) {
                    water++;
                }
            }
        }
        if (trees * CROWDED > columns) {
            wishes.add("%d%% under trees".formatted(100 * trees / columns));
        }
        if (water * CROWDED > columns) {
            wishes.add("%d%% under water".formatted(100 * water / columns));
        }
        return new Survey(null, wishes);
    }

    @Nullable
    private static String danger(ServerLevel level, BlockPos centre) {
        int reach = REACH_OUT + SAFE;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > reach * reach) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1;
                for (int y = top - SAFE_BELOW; y <= top + SAFE_ABOVE; y++) {
                    BlockPos pos = new BlockPos(centre.getX() + x, y, centre.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(FluidTags.LAVA) || DANGERS.contains(state.getBlock())) {
                        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String neighbour(ServerLevel level, BlockPos centre, int reach) {
        BoundingBox around = new BoundingBox(centre.getX() - reach, level.getMinBuildHeight(), centre.getZ() - reach, centre.getX() + reach,
                level.getMaxBuildHeight(), centre.getZ() + reach);
        for (int x = (centre.getX() - reach) >> 4; x <= (centre.getX() + reach) >> 4; x++) {
            for (int z = (centre.getZ() - reach) >> 4; z <= (centre.getZ() + reach) >> 4; z++) {
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(x, z), structure -> true)) {
                    BoundingBox box = start.getBoundingBox();
                    if (box.intersects(around) && box.maxY() >= centre.getY() - SAFE_BELOW) {
                        return String.valueOf(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure()));
                    }
                }
            }
        }
        return null;
    }

    private static void guard(ServerLevel level, Camp camp) {
        if (!level.isLoaded(camp.centre)) {
            return;
        }
        for (BoundingBox box : camp.boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - EMBERS, box.minY() - EMBERS, box.minZ() - EMBERS, box.maxX() + EMBERS,
                    box.maxY() + EMBERS, box.maxZ() + EMBERS)) {
                if (level.getBlockState(pos).is(BlockTags.FIRE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    @Nullable
    private static String build(ServerLevel level, Camp camp) {
        List<BoundingBox> taken = new ArrayList<>();
        List<BlockPos> shifts = new ArrayList<>();
        for (int dx = -FIRE_SHIFT; dx <= FIRE_SHIFT; dx += FIRE_SHIFT / 2) {
            for (int dz = -FIRE_SHIFT; dz <= FIRE_SHIFT; dz += FIRE_SHIFT / 2) {
                shifts.add(camp.centre.offset(dx, 0, dz));
            }
        }
        shifts.sort(Comparator.comparingDouble(spot -> spot.distSqr(camp.centre)));
        Placed fire = null;
        for (BlockPos spot : shifts) {
            fire = place(level, camp, "fire", spot, Rotation.NONE, taken);
            if (fire != null) {
                break;
            }
        }
        if (fire == null) {
            return "no room for the fire";
        }
        camp.centre = fire.centre;
        camp.boxes = taken;
        List<BlockPos> seats = new ArrayList<>();
        BlockPos.betweenClosedStream(fire.box).filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG))
                .forEach(pos -> seats.add(pos.immutable()));
        List<JsonElement> households = HOUSEHOLDS.asList();
        long together = households.size();
        int slot = 0;
        for (JsonElement entry : households) {
            JsonObject household = entry.getAsJsonObject();
            String piece = household.get("piece").getAsString();
            StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
            double angle = camp.facing + 180 + 360.0 * slot++ / together;
            int radius = 4 + Math.max(template.getSize().getX(), template.getSize().getZ()) / 2 + GAP;
            Placed placed = null;
            for (int nudge : NUDGES) {
                for (int extra = 0; extra <= REACH && placed == null; extra += 2) {
                    Vec3 way = Vec3.directionFromRotation(0, (float) (angle + nudge));
                    BlockPos at = camp.centre.offset((int) Math.round(way.x * (radius + extra)), 0, (int) Math.round(way.z * (radius + extra)));
                    placed = place(level, camp, piece, at, facing(camp.centre, at), taken);
                }
                if (placed != null) {
                    break;
                }
            }
            if (placed == null) {
                continue;
            }
            path(level, camp, placed, taken);
            if (household.has("people")) {
                for (JsonElement person : household.getAsJsonArray("people")) {
                    JsonObject who = person.getAsJsonObject();
                    boolean sits = who.get("at").getAsString().equals("fire") && !seats.isEmpty();
                    BlockPos spot = sits ? seats.removeFirst().above() : placed.front.above();
                    double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
                    String pose = who.has("pose") ? who.get("pose").getAsString() : sits ? "sitting" : "standing";
                    People.person(level, who.get("person").getAsString(), Vec3.atBottomCenterOf(spot), (float) yaw, pose);
                }
            }
        }
        return null;
    }

    private static Rotation facing(BlockPos centre, BlockPos at) {
        int dx = centre.getX() - at.getX();
        int dz = centre.getZ() - at.getZ();
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        }
        return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    @Nullable
    private static Placed place(ServerLevel level, Camp camp, String piece, BlockPos at, Rotation rotation, List<BoundingBox> taken) {
        StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
        BlockPos pivot = new BlockPos(template.getSize().getX() / 2, 0, template.getSize().getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).setIgnoreEntities(true)
                .setKnownShape(true);
        BlockPos origin = at.subtract(pivot);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        if (taken.stream().anyMatch(box -> box.inflatedBy(1).intersects(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
            return null;
        }
        List<Integer> tops = new ArrayList<>();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int top = ground(level, x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, top, z));
                if (!ground.getFluidState().isEmpty() || level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 - top > BUSH) {
                    return null;
                }
                tops.add(top);
            }
        }
        tops.sort(Integer::compare);
        int y = tops.get(tops.size() / 2);
        if (tops.getLast() - y > STEEP || y - tops.getFirst() > STEEP) {
            return null;
        }
        BlockPos placedOrigin = origin.atY(y);
        BoundingBox box = template.getBoundingBox(settings, placedOrigin);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            remember(level, camp, pos);
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), Math.max(box.maxY(), y + BUSH), box.maxZ())) {
            if (growth(level.getBlockState(pos))) {
                remember(level, camp, pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                terrace(level, camp, x, z, y);
            }
        }
        template.placeInWorld(level, placedOrigin, placedOrigin, settings, level.getRandom(), FLAGS);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                foundation(level, camp, x, z, y, box.maxY());
            }
        }
        taken.add(box);
        Direction front = rotation.rotate(Direction.NORTH);
        BlockPos centre = at.atY(y);
        BlockPos door = centre.relative(front, template.getSize().getZ() / 2 + 1);
        int doorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door.getX(), door.getZ()) - 1;
        return new Placed(box, centre, door.atY(doorY));
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

    private static void terrace(ServerLevel level, Camp camp, int x, int z, int y) {
        int top = ground(level, x, z);
        if (top <= y) {
            return;
        }
        BlockState surface = level.getBlockState(new BlockPos(x, top, z));
        for (int height = y; height <= top; height++) {
            remember(level, camp, new BlockPos(x, height, z));
            level.setBlock(new BlockPos(x, height, z), height == y ? surface : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void foundation(ServerLevel level, Camp camp, int x, int z, int y, int top) {
        int lowest = Integer.MIN_VALUE;
        for (int height = y; height <= top; height++) {
            BlockPos pos = new BlockPos(x, height, z);
            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).equals(camp.originals.get(pos))) {
                lowest = height;
                break;
            }
        }
        if (lowest == Integer.MIN_VALUE) {
            return;
        }
        for (int depth = 1; depth <= FOUNDATION; depth++) {
            BlockPos below = new BlockPos(x, lowest - depth, z);
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return;
            }
            remember(level, camp, below);
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), FLAGS);
        }
    }

    private static void path(ServerLevel level, Camp camp, Placed placed, List<BoundingBox> taken) {
        Vec3 from = Vec3.atCenterOf(placed.front);
        Vec3 to = Vec3.atCenterOf(camp.centre);
        double length = Math.sqrt(from.distanceToSqr(to.x, from.y, to.z));
        for (double travelled = 0; travelled < length - 3; travelled += 0.5) {
            int x = Mth.floor(from.x + (to.x - from.x) * travelled / length);
            int z = Mth.floor(from.z + (to.z - from.z) * travelled / length);
            if (taken.stream().anyMatch(box -> box.isInside(x, box.minY(), z))) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState ground = level.getBlockState(top);
            if (!ground.is(BlockTags.DIRT) || ground.is(Blocks.DIRT_PATH) || camp.originals.containsKey(top)) {
                continue;
            }
            remember(level, camp, top);
            remember(level, camp, top.above());
            if (level.getBlockState(top.above()).canBeReplaced()) {
                level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
        }
    }

    private static void remember(ServerLevel level, Camp camp, BlockPos pos) {
        camp.originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

}
