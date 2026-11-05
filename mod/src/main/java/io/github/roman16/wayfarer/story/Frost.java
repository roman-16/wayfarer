package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Halls;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

public final class Frost {
    static final String NAME = "frost";
    static final String TRACKS = "frost_tracks";
    static final String HALL = "frost_hall";
    static final String STONE = "frost_stone";
    static final String LAKE = "frost_lake";
    static final String LAKE_STONE = "frost_lake_stone";
    private static final String HOUSE = "frost_house";
    static final String HOUSE_SITE = "frost_house_site";
    private static final String HALL_SITE = "frost_hall_site";
    private static final String HALL_LOW = "frost_hall_low";
    private static final String HALL_HIGH = "frost_hall_high";
    private static final String SHAFT_LOW = "frost_shaft_low";
    private static final String SHAFT_HIGH = "frost_shaft_high";
    private static final String LAKE_SITE = "frost_lake_site";
    private static final String LAKE_ORIGIN = "frost_lake_origin";
    private static final String SNOWFIELD = "frost_snowfield_";
    private static final String ALONG = "frost_along";
    private static final String LAKE_TURN = "frost_lake_turn";
    static final String LAID = "frost_laid";
    private static final String ROOF = "frost_roof";
    private static final String DOOR = "frost_door";
    private static final String HEARTH = "frost_hearth";
    private static final String STAIR = "frost_stair";
    private static final String INSIDE = "frost_inside";
    private static final String SHORE = "frost_shore";
    private static final String RIM = "frost_rim";
    private static final String PRINTS = "frost_prints";
    private static final String SLEEPER = "sleeper";
    private static final String GREY_COAT = "grey_coat";
    private static final ResourceLocation COLD = Wayfarer.id("hall/cold");
    private static final ResourceLocation HOUSE_TEMPLATE = Wayfarer.id("frost/house");
    private static final ResourceLocation LAKE_TEMPLATE = Wayfarer.id("frost/lake");
    private static final String[] DEAD = {"frost_dead_01", "frost_dead_02", "frost_dead_03", "frost_dead_04", "frost_dead_05", "frost_dead_06",
            "frost_dead_07", "frost_dead_08", "frost_dead_09", "frost_dead_10", "frost_dead_11", "frost_dead_12"};
    private static final String[] SLEEPERS = {"frost_01", "frost_02", "frost_03", "frost_04"};
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/frost.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_frost", Comparator.comparingLong(ChunkPos::toLong));
    private static final int GRID = 64;
    private static final int LAKE_AROUND_BLOCKS = 64;
    private static final int LAKE_FAN_STEP = 16;
    private static final int LAKE_MARGIN = 4;
    private static final int LAKE_RAISED_COST = 4;
    private static final int LAKE_ROUGH_COST = 16;
    private static final int LAKE_ROUGH_MOST = 8;
    private static final int LAKE_STEP = 128;
    private static final int LAKE_SNOW_BONUS = 32;
    private static final int INSIDE_SAMPLE = 32;
    private static final int EDGE_STEP = 8;
    private static final int TURN_STEP_DEGREES = 15;
    private static final int TURN_MOST_DEGREES = 75;
    private static final int TRAIL_TURN_DEGREES = 60;
    private static final int TRAIL_SAMPLE = 32;
    private static final int ROUGH = 4;
    private static final int SOAKED = 8;
    private static final int LOAD_RADIUS = 7;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final double BEND = 7;
    private static final double WAVE = 96;
    private static final double STRIDE = 1;
    private static final double FOOT = 0.3;
    private static final double DRAG_STEP = 2;
    private static final int BLOOD_EVERY = 13;
    private static final float PRINT_WIDTH = 0.25f;
    private static final float PRINT_LENGTH = 0.45f;
    private static final float DRAG_WIDTH = 0.35f;
    private static final float BLOOD_SIZE = 0.15f;
    private static final int DRIFT_ACROSS = 3;
    private static final int BAND_WIDTH = 9;
    private static final int BAND_DEPTH = 3;
    private static final int BANDS = 3;
    private static final int NAVE_FROM = 10;
    private static final int NAVE_EVERY = 3;
    private static final int[] NAVE_REACHES = {4, 5, 3};
    private static final int KEEP_CLEAR = 4;
    private static final int SEAT_NEAR = 3;
    private static final int SEAT_FAR = 6;
    private static final int STAIR_SEAT = 4;
    private static final int COATS = 3;
    private static final int COATS_OUT = 20;
    private static final double PRINT_LANE = 0.8;
    private static final double PRINT_STEP = 1.2;

    private Frost() {}

    private record Mark(Vec3 at, Vec3 along, Kind kind) {}

    private enum Kind { PRINT, DRAG, BLOOD, DRIFT }

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(HOUSE_SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.Frost balance = Balance.get().frost();
        Home.Aim aim = Home.aim(Caravan.last(server), Caravan.way(server),
                balance.searchBlocks() + balance.hallBlocks());
        BlockPos from = aim.from();
        Unseen.Heights heights = new Unseen.Heights(level);
        Optional<BlockPos> snow = snow(heights, from, aim.way(), balance);
        Vec3 inward = snow.map(cell -> Vec3.atLowerCornerOf(cell.subtract(from)).multiply(1, 0, 1).normalize()).orElse(aim.way());
        BlockPos house = snow.map(cell -> edge(heights, cell, inward)).orElseGet(() -> ground(heights, from, aim.way(), balance.searchBlocks() / 2));
        if (!snowy(heights, house.getX(), house.getZ())) {
            progress.place(SNOWFIELD + HOUSE, house);
        }
        BlockPos hall = hall(heights, house, inward, balance);
        if (!snowy(heights, hall.getX(), hall.getZ())) {
            progress.place(SNOWFIELD + HALL, hall);
        }
        Vec3 along = along(heights, house, inward, balance);
        Vec3 tracks = trail(house, along, balance.trailBlocks());
        progress.place(TRACKS, new BlockPos((int) Math.floor(tracks.x), heights.surface((int) Math.floor(tracks.x), (int) Math.floor(tracks.z)),
                (int) Math.floor(tracks.z)));
        progress.place(HOUSE_SITE, house);
        progress.place(HALL_SITE, hall);
        progress.yaws.put(ALONG, (float) Math.atan2(along.z, along.x));
        progress.setDirty();
        LOGGER.info("the Frost Walkers' winter house lies at {}{}, their hall at {}, the tracks cross the way at {}", house,
                snow.isPresent() ? " at the snow's edge" : " in a snowfield the quest makes", hall, progress.place(TRACKS).orElseThrow());
    }

    static void lake(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.place(LAKE_SITE).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.Frost balance = Balance.get().frost();
        BlockPos from = progress.place(HALL_SITE).orElseGet(() -> Caravan.last(server));
        Home.Aim aim = Home.aim(from, new Vec3(1, 0, 0), balance.lakeBlocks());
        Unseen.Heights heights = new Unseen.Heights(level);
        BlockPos site = hollow(heights, aim.from(), aim.way(), balance);
        if (!snowy(heights, site.getX(), site.getZ())) {
            progress.place(SNOWFIELD + LAKE, site);
        }
        progress.place(LAKE_SITE, site);
        progress.yaws.put(LAKE_TURN, (float) facing(aim.way().scale(-1)).ordinal());
        progress.setDirty();
        LOGGER.info("the frozen lake lies at {}", site);
    }

    private static BlockPos hollow(Unseen.Heights heights, BlockPos from, Vec3 way, Balance.Frost balance) {
        return hollow(heights, from, way, balance, LAKE_ROUGH_MOST).or(() -> hollow(heights, from, way, balance, Integer.MAX_VALUE))
                .orElseGet(() -> ground(heights, from, way, balance.lakeBlocks()));
    }

    private static Optional<BlockPos> hollow(Unseen.Heights heights, BlockPos from, Vec3 way, Balance.Frost balance, int roughest) {
        int reach = PLAN.get().getAsJsonObject("lake").getAsJsonArray("size").get(0).getAsInt() / 2 + LAKE_MARGIN;
        BlockPos best = null;
        double most = Double.NEGATIVE_INFINITY;
        for (int turned = -balance.lakeFanDegrees(); turned <= balance.lakeFanDegrees(); turned += LAKE_FAN_STEP) {
            Vec3 along = way.yRot((float) Math.toRadians(turned));
            for (int distance = balance.lakeBlocks() - balance.lakeSpreadBlocks(); distance <= balance.lakeBlocks() + balance.lakeSpreadBlocks(); distance += LAKE_STEP) {
                Vec3 at = Vec3.atLowerCornerOf(from).add(along.scale(distance));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                Optional<int[]> spread = spread(heights, x, z, reach, roughest);
                if (spread.isEmpty()) {
                    continue;
                }
                int y = heights.surface(x, z);
                double around = 0;
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    around += heights.surface(x + side.getStepX() * LAKE_AROUND_BLOCKS, z + side.getStepZ() * LAKE_AROUND_BLOCKS);
                }
                around /= 4;
                double score = -(spread.get()[1] - spread.get()[0]) * LAKE_ROUGH_COST - Math.max(0, y - around) * LAKE_RAISED_COST
                        + (snowy(heights, x, z) ? LAKE_SNOW_BONUS : 0) - Math.abs(distance - balance.lakeBlocks()) / (double) LAKE_STEP;
                if (score > most) {
                    most = score;
                    best = new BlockPos(x, y, z);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<int[]> spread(Unseen.Heights heights, int x, int z, int reach, int roughest) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int[] at : new int[][] {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}, {reach / 2, reach / 2}, {-reach / 2, -reach / 2},
                {reach / 2, -reach / 2}, {-reach / 2, reach / 2}}) {
            if (heights.wet(x + at[0], z + at[1])) {
                return Optional.empty();
            }
            int y = heights.surface(x + at[0], z + at[1]);
            low = Math.min(low, y);
            high = Math.max(high, y);
            if (high - low > roughest) {
                return Optional.empty();
            }
        }
        return Optional.of(new int[] {low, high});
    }

    private static Optional<BlockPos> snow(Unseen.Heights heights, BlockPos from, Vec3 way, Balance.Frost balance) {
        Vec3 side = new Vec3(-way.z, 0, way.x);
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (int ahead = balance.aheadBlocks(); ahead <= balance.searchBlocks(); ahead += GRID) {
            for (int across = -balance.sideBlocks(); across <= balance.sideBlocks(); across += GRID) {
                double cost = ahead + Math.abs(across) * 2.0;
                if (cost >= nearest) {
                    continue;
                }
                Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(ahead)).add(side.scale(across));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (snowy(heights, x, z) && snowy(heights, x + INSIDE_SAMPLE, z) && snowy(heights, x - INSIDE_SAMPLE, z)
                        && snowy(heights, x, z + INSIDE_SAMPLE) && snowy(heights, x, z - INSIDE_SAMPLE) && flat(heights, x, z)) {
                    best = new BlockPos(x, heights.surface(x, z), z);
                    nearest = cost;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static boolean snowy(Unseen.Heights heights, int x, int z) {
        int y = heights.surface(x, z);
        return !heights.wet(x, z) && Unseen.snowy(heights.biome(x, y, z), new BlockPos(x, y, z));
    }

    static boolean flat(Unseen.Heights heights, int x, int z) {
        int middle = heights.surface(x, z);
        for (int[] step : new int[][] {{INSIDE_SAMPLE, 0}, {-INSIDE_SAMPLE, 0}, {0, INSIDE_SAMPLE}, {0, -INSIDE_SAMPLE}}) {
            if (Math.abs(heights.surface(x + step[0], z + step[1]) - middle) > ROUGH) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos edge(Unseen.Heights heights, BlockPos cell, Vec3 inward) {
        Vec3 at = Vec3.atLowerCornerOf(cell);
        for (int back = EDGE_STEP; back < Balance.get().frost().searchBlocks(); back += EDGE_STEP) {
            Vec3 next = Vec3.atLowerCornerOf(cell).subtract(inward.scale(back));
            if (!snowy(heights, (int) Math.round(next.x), (int) Math.round(next.z))) {
                break;
            }
            at = next;
        }
        for (int step = 0; step < Balance.get().frost().trailBlocks(); step += EDGE_STEP) {
            Vec3 house = at.add(inward.scale(INSIDE_SAMPLE + step));
            int x = (int) Math.round(house.x);
            int z = (int) Math.round(house.z);
            if (snowy(heights, x, z) && flat(heights, x, z)) {
                return new BlockPos(x, heights.surface(x, z), z);
            }
        }
        return cell;
    }

    private static BlockPos ground(Unseen.Heights heights, BlockPos from, Vec3 way, int ahead) {
        for (int back = 0; back < ahead / 2; back += GRID) {
            for (int sign : back == 0 ? new int[] {1} : new int[] {-1, 1}) {
                Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(ahead + back * sign));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (!heights.wet(x, z)) {
                    return new BlockPos(x, heights.surface(x, z), z);
                }
            }
        }
        Vec3 at = Vec3.atLowerCornerOf(from).add(way.scale(ahead));
        return new BlockPos((int) Math.round(at.x), heights.surface((int) Math.round(at.x), (int) Math.round(at.z)), (int) Math.round(at.z));
    }

    private static BlockPos hall(Unseen.Heights heights, BlockPos house, Vec3 inward, Balance.Frost balance) {
        for (int turned = 0; turned <= TURN_MOST_DEGREES; turned += TURN_STEP_DEGREES) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 at = Vec3.atLowerCornerOf(house).add(inward.yRot((float) Math.toRadians(turned * sign)).scale(balance.hallBlocks()));
                int x = (int) Math.round(at.x);
                int z = (int) Math.round(at.z);
                if (snowy(heights, x, z) && flat(heights, x, z)) {
                    return new BlockPos(x, heights.surface(x, z), z);
                }
            }
        }
        Vec3 at = Vec3.atLowerCornerOf(house).add(inward.scale(balance.hallBlocks()));
        int x = (int) Math.round(at.x);
        int z = (int) Math.round(at.z);
        return new BlockPos(x, heights.surface(x, z), z);
    }

    private static Vec3 along(Unseen.Heights heights, BlockPos house, Vec3 inward, Balance.Frost balance) {
        Vec3 best = inward.scale(-1);
        int most = Integer.MIN_VALUE;
        for (int turned = TURN_STEP_DEGREES * 2; turned <= TRAIL_TURN_DEGREES * 3 / 2; turned += TURN_STEP_DEGREES) {
            for (int sign : new int[] {1, -1}) {
                Vec3 along = inward.scale(-1).yRot((float) Math.toRadians(turned * sign));
                int score = -Math.abs(turned - TRAIL_TURN_DEGREES) / TURN_STEP_DEGREES;
                int last = house.getY();
                for (int s = TRAIL_SAMPLE; s <= balance.trailBlocks(); s += TRAIL_SAMPLE) {
                    Vec3 at = trail(house, along, s);
                    int x = (int) Math.floor(at.x);
                    int z = (int) Math.floor(at.z);
                    int y = heights.surface(x, z);
                    score += (snowy(heights, x, z) ? 1 : 0) - (heights.wet(x, z) ? SOAKED : 0) - Math.max(0, Math.abs(y - last) - ROUGH);
                    last = y;
                }
                if (score > most) {
                    most = score;
                    best = along;
                }
            }
        }
        return best;
    }

    private static Vec3 trail(BlockPos house, Vec3 along, double s) {
        Vec3 across = new Vec3(-along.z, 0, along.x);
        double start = PLAN.get().getAsJsonObject("house").getAsJsonArray("size").get(2).getAsInt() / 2.0 + 2;
        double bend = BEND * Math.sin(s * Math.PI * 2 / WAVE) * Math.min(1, s / WAVE);
        return Vec3.atBottomCenterOf(house).add(along.scale(start + s)).add(across.scale(bend));
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        progress.place(HOUSE_SITE).filter(site -> progress.place(HOUSE).isEmpty() && loaded(level, site)).ifPresent(site -> house(server, site));
        progress.place(HALL_SITE).filter(site -> progress.place(HALL_LOW).isEmpty() && loaded(level, site)).ifPresent(site -> hall(server, site));
        progress.place(LAKE_SITE).filter(site -> progress.place(LAKE_ORIGIN).isEmpty() && loaded(level, site)).ifPresent(site -> lake(server, site));
        if (progress.place(HOUSE).isPresent()) {
            lay(server, level);
        }
    }

    static boolean loaded(ServerLevel level, BlockPos site) {
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

    static void release(ServerLevel level, BlockPos site) {
        level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(site), LOAD_RADIUS, new ChunkPos(site));
    }

    private static void house(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(SNOWFIELD + HOUSE).isPresent()) {
            snowfield(level, site);
        }
        Vec3 along = direction(progress.yaws.get(ALONG));
        Rotation turn = facing(along);
        JsonObject plan = PLAN.get().getAsJsonObject("house");
        BlockPos centre = new BlockPos(local(plan.getAsJsonArray("size")).getX() / 2, 0, local(plan.getAsJsonArray("size")).getZ() / 2);
        BlockPos origin = site.atY(Ground.land(level, site.getX(), site.getZ())).subtract(centre);
        Camps.build(level, HOUSE_TEMPLATE, origin, turn);
        StructurePlaceSettings settings = Camps.settings(level, HOUSE_TEMPLATE, turn);
        float yaw = yaw(along);
        progress.place(HOUSE, origin);
        progress.ward(NAME, level.getStructureManager().getOrCreate(HOUSE_TEMPLATE).getBoundingBox(settings, origin));
        progress.place(NAME, origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("door")))));
        progress.place("seat_" + ROOF, origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("roof")))));
        progress.yaws.put("seat_" + ROOF, yaw);
        BlockPos hearth = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("hearth"))));
        BlockPos beside = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("hearth_seat"))));
        progress.place("seat_" + HEARTH, beside);
        progress.yaws.put("seat_" + HEARTH, (float) Math.toDegrees(Math.atan2(beside.getX() - hearth.getX(), hearth.getZ() - beside.getZ())));
        progress.place(NAME).ifPresent(door -> {
            progress.place("seat_" + DOOR, door);
            progress.yaws.put("seat_" + DOOR, yaw);
        });
        progress.setDirty();
        BlockPos kill = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("kill"))));
        Fallen.lay(level, EntityType.GOAT, Vec3.atBottomCenterOf(kill), yaw + 90, true, Steps.CLUE + NAME);
        release(level, site);
        Standing.keep(server, NAME);
        LOGGER.info("the Frost Walkers' winter house stands at {}, its door at {}", origin, progress.place(NAME).orElseThrow());
        Journal.syncAll(server);
    }

    private static void hall(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(SNOWFIELD + HALL).isPresent()) {
            snowfield(level, site);
        }
        Places.Hall raised = Places.raise(level, COLD, site);
        Places.empty(level, raised.stone());
        BoundingBox box = raised.box();
        BoundingBox shaft = raised.shaft();
        BlockPos stair = raised.threshold();
        BlockPos stone = raised.stone();
        progress.place(HALL, stair);
        progress.place(STONE, stone);
        progress.ward(HALL, box);
        progress.place(HALL_LOW, new BlockPos(box.minX(), box.minY(), box.minZ()));
        progress.place(HALL_HIGH, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
        progress.place(SHAFT_LOW, new BlockPos(shaft.minX(), shaft.minY(), shaft.minZ()));
        progress.place(SHAFT_HIGH, new BlockPos(shaft.maxX(), shaft.maxY(), shaft.maxZ()));
        Vec3 away = new Vec3(stair.getX() + 0.5 - shaft.getCenter().getX() - 0.5, 0, stair.getZ() + 0.5 - shaft.getCenter().getZ() - 0.5).normalize();
        BoundingBox cover = shaft.inflatedBy(1);
        Vec3 seat = Vec3.atBottomCenterOf(stair).add(away.scale(STAIR_SEAT));
        for (int out = STAIR_SEAT; cover.intersects(Mth.floor(seat.x), Mth.floor(seat.z), Mth.floor(seat.x), Mth.floor(seat.z)) && out < STAIR_SEAT * 4; out++) {
            seat = Vec3.atBottomCenterOf(stair).add(away.scale(out));
        }
        BlockPos stairSeat = BlockPos.containing(seat.x, Ground.land(level, (int) Math.floor(seat.x), (int) Math.floor(seat.z)) + 1,
                seat.z);
        progress.place("seat_" + STAIR, stairSeat);
        progress.yaws.put("seat_" + STAIR, yaw(Vec3.atLowerCornerOf(stair.subtract(stairSeat)).multiply(1, 0, 1)));
        BlockPos inside = floor(level, box, stone, BlockPos.containing(shaft.getCenter().getX(), stone.getY(), shaft.getCenter().getZ())).orElse(stone.above());
        progress.place("seat_" + INSIDE, inside);
        progress.yaws.put("seat_" + INSIDE, yaw(Vec3.atLowerCornerOf(stone.subtract(inside)).multiply(1, 0, 1)));
        progress.setDirty();
        List<Dead> dead = dead(level, box, stone, shaft);
        for (int index = 0; index < dead.size(); index++) {
            People.spawn(level, DEAD[index], Vec3.atBottomCenterOf(dead.get(index).at()), dead.get(index).yaw(), "laid_out");
        }
        prints(level, shaft, stone);
        release(level, site);
        Standing.keep(server, HALL);
        LOGGER.info("the Frost Walkers' hall stands at {}, its stair at {}, its empty stone at {}, {} dead along its walls", box, stair, stone, dead.size());
        Journal.syncAll(server);
    }

    private record Dead(BlockPos at, float yaw) {}

    private static List<Dead> dead(Level level, BoundingBox box, BlockPos stone, BoundingBox shaft) {
        Vec3 centre = Vec3.atBottomCenterOf(stone);
        Vec3 axis = new Vec3(shaft.getCenter().getX() + 0.5 - centre.x, 0, shaft.getCenter().getZ() + 0.5 - centre.z).normalize();
        Vec3 across = new Vec3(-axis.z, 0, axis.x);
        List<Dead> laid = new ArrayList<>();
        for (int row = 0; laid.size() < DEAD.length && row < DEAD.length; row++) {
            for (int sign : new int[] {1, -1}) {
                for (int reach : NAVE_REACHES) {
                    Vec3 at = centre.add(axis.scale(NAVE_FROM + row * NAVE_EVERY)).add(across.scale(sign * reach));
                    Optional<BlockPos> cell = BlockPos.betweenClosedStream(BlockPos.containing(at.x, stone.getY() - KEEP_CLEAR, at.z), BlockPos.containing(at.x,
                            stone.getY(), at.z)).filter(pos -> box.isInside(pos) && !shaft.inflatedBy(1).isInside(pos) && Ground.standable(level, Vec3.atBottomCenterOf(pos)))
                            .map(BlockPos::immutable).findFirst();
                    if (cell.isPresent() && laid.size() < DEAD.length) {
                        laid.add(new Dead(cell.get(), yaw(across.scale(-sign))));
                        break;
                    }
                }
            }
        }
        return laid;
    }

    private static boolean near(Level level, BlockPos pos, int reach, Predicate<BlockState> what) {
        return BlockPos.betweenClosedStream(pos.offset(-reach, -1, -reach), pos.offset(reach, 1, reach)).anyMatch(at -> what.test(level.getBlockState(at)));
    }

    private static Optional<BlockPos> floor(Level level, BoundingBox box, BlockPos stone, BlockPos door) {
        Vec3 toward = Vec3.atLowerCornerOf(door.subtract(stone)).multiply(1, 0, 1).normalize();
        BlockPos front = BlockPos.containing(Vec3.atLowerCornerOf(stone).add(toward.scale(SEAT_NEAR + 1)));
        BlockPos best = null;
        int nearest = Integer.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-SEAT_FAR, -SEAT_FAR, -SEAT_FAR), stone.offset(SEAT_FAR, -2, SEAT_FAR))) {
            int distance = Math.abs(pos.getX() - front.getX()) + Math.abs(pos.getZ() - front.getZ());
            if (distance < nearest && box.isInside(pos) && Ground.standable(level, Vec3.atBottomCenterOf(pos))) {
                best = pos.immutable();
                nearest = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static void prints(ServerLevel level, BoundingBox shaft, BlockPos stone) {
        Vec3 from = new Vec3(shaft.getCenter().getX() + 0.5, shaft.minY() + 2, shaft.getCenter().getZ() + 0.5);
        Vec3 to = Vec3.atBottomCenterOf(stone);
        Vec3 line = to.subtract(from).multiply(1, 0, 1);
        Vec3 along = line.normalize();
        Vec3 across = new Vec3(-along.z, 0, along.x);
        BlockState mark = Blocks.GRAY_CONCRETE_POWDER.defaultBlockState();
        int index = 0;
        for (double s = 0; s < line.length() - 2; s += PRINT_STEP, index++) {
            for (int lane = -1; lane <= 1; lane++) {
                Vec3 at = from.add(along.scale(s + lane * 0.4)).add(across.scale(lane * PRINT_LANE + (index % 2 == 0 ? FOOT : -FOOT)));
                Optional<Double> y = floorAt(level, BlockPos.containing(at.x, from.y + 1, at.z), (int) from.y - KEEP_CLEAR);
                if (y.isPresent()) {
                    Clues.mark(level, new Vec3(at.x, y.get() + 0.01, at.z), mark, PRINT_WIDTH, PRINT_LENGTH, along, PRINTS);
                }
            }
        }
    }

    private static Optional<Double> floorAt(Level level, BlockPos top, int lowest) {
        for (BlockPos pos = top; pos.getY() > lowest; pos = pos.below()) {
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                return Optional.of((double) pos.getY());
            }
        }
        return Optional.empty();
    }

    private static void lake(MinecraftServer server, BlockPos site) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(SNOWFIELD + LAKE).isPresent()) {
            snowfield(level, site);
        }
        Rotation turn = Rotation.values()[progress.yaws.getOrDefault(LAKE_TURN, 0f).intValue()];
        JsonObject plan = PLAN.get().getAsJsonObject("lake");
        BlockPos size = local(plan.getAsJsonArray("size"));
        BlockPos origin = site.atY(Ground.land(level, site.getX(), site.getZ()) - plan.get("surface").getAsInt()).offset(-size.getX() / 2, 0, -size.getZ() / 2);
        Camps.build(level, LAKE_TEMPLATE, origin, turn);
        StructurePlaceSettings settings = Camps.settings(level, LAKE_TEMPLATE, turn);
        BlockPos shore = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("shore"))));
        BlockPos stone = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("stone"))));
        progress.place(LAKE_ORIGIN, origin);
        progress.place(LAKE, shore);
        progress.place(LAKE_STONE, stone);
        BlockPos rim = origin.offset(StructureTemplate.calculateRelativePosition(settings, local(plan.getAsJsonArray("rim"))));
        progress.place("seat_" + SHORE, shore);
        progress.yaws.put("seat_" + SHORE, yaw(Vec3.atLowerCornerOf(stone.subtract(shore)).multiply(1, 0, 1)));
        progress.place("seat_" + RIM, rim);
        progress.yaws.put("seat_" + RIM, yaw(Vec3.atLowerCornerOf(stone.subtract(rim)).multiply(1, 0, 1)));
        progress.setDirty();
        release(level, site);
        Standing.keep(server, LAKE);
        LOGGER.info("the frozen lake lies at {}, the shard under its ice at {}", origin, stone);
        Journal.syncAll(server);
    }

    private static void lay(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        Set<Long> laid = new HashSet<>();
        for (long chunk : progress.scratch(LAID).getLongArray(LAID)) {
            laid.add(chunk);
        }
        Map<Long, List<Mark>> marks = new LinkedHashMap<>();
        marks(progress).forEach(mark -> marks.computeIfAbsent(ChunkPos.asLong(BlockPos.containing(mark.at())), key -> new ArrayList<>()).add(mark));
        boolean changed = false;
        for (Map.Entry<Long, List<Mark>> chunk : marks.entrySet()) {
            if (laid.contains(chunk.getKey()) || level.getChunkSource().getChunkNow(ChunkPos.getX(chunk.getKey()), ChunkPos.getZ(chunk.getKey())) == null
                    || !level.areEntitiesLoaded(chunk.getKey())) {
                continue;
            }
            chunk.getValue().forEach(mark -> lay(level, mark));
            laid.add(chunk.getKey());
            changed = true;
        }
        if (changed) {
            progress.scratch(LAID).put(LAID, new LongArrayTag(laid.stream().mapToLong(Long::longValue).toArray()));
            progress.setDirty();
        }
    }

    private static List<Mark> marks(Progress progress) {
        Balance.Frost balance = Balance.get().frost();
        BlockPos house = progress.place(HOUSE_SITE).orElseThrow();
        Vec3 along = direction(progress.yaws.get(ALONG));
        List<Mark> marks = new ArrayList<>();
        double length = balance.trailBlocks() + balance.trailPastBlocks();
        int step = 0;
        for (double s = 0; s < length; s += STRIDE, step++) {
            Vec3 here = trail(house, along, s);
            Vec3 ahead = trail(house, along, s + STRIDE).subtract(here).normalize();
            Vec3 across = new Vec3(-ahead.z, 0, ahead.x);
            marks.add(new Mark(here.add(across.scale(step % 2 == 0 ? FOOT : -FOOT)), ahead.scale(-1), Kind.PRINT));
            if (step % (int) Math.round(DRAG_STEP / STRIDE) == 0) {
                marks.add(new Mark(here.add(ahead.scale(DRAG_STEP / 2)), ahead, Kind.DRAG));
            }
            if (step % BLOOD_EVERY == BLOOD_EVERY / 2) {
                marks.add(new Mark(here.add(across.scale(step % 3 == 0 ? FOOT * 2 : -FOOT)), ahead, Kind.BLOOD));
            }
            if (step > 0 && (int) Math.floor(s / balance.driftEveryBlocks()) != (int) Math.floor((s - STRIDE) / balance.driftEveryBlocks())) {
                int side = (int) Math.floor(s / balance.driftEveryBlocks()) % 2 == 0 ? 1 : -1;
                for (int dx = 0; dx < 3; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        marks.add(new Mark(here.add(ahead.scale(dx)).add(across.scale(side * (DRIFT_ACROSS + dz))), ahead, Kind.DRIFT));
                    }
                }
            }
        }
        progress.place(HALL_SITE).ifPresent(hall -> {
            Vec3 from = Vec3.atBottomCenterOf(house);
            Vec3 line = Vec3.atBottomCenterOf(hall).subtract(from).multiply(1, 0, 1);
            Vec3 way = line.normalize();
            Vec3 across = new Vec3(-way.z, 0, way.x);
            for (int band = 1; band <= BANDS; band++) {
                Vec3 middle = from.add(line.scale(band / (double) (BANDS + 1)));
                for (int a = -BAND_WIDTH / 2; a <= BAND_WIDTH / 2; a++) {
                    for (int d = 0; d < BAND_DEPTH; d++) {
                        marks.add(new Mark(middle.add(across.scale(a)).add(way.scale(d)), way, Kind.DRIFT));
                    }
                }
            }
        });
        return marks;
    }

    private static void lay(ServerLevel level, Mark mark) {
        int x = (int) Math.floor(mark.at().x);
        int z = (int) Math.floor(mark.at().z);
        BlockPos ground = new BlockPos(x, Ground.land(level, x, z), z);
        BlockState below = level.getBlockState(ground);
        if (!level.getFluidState(ground).isEmpty() || below.is(Blocks.ICE) || below.is(BlockTags.LEAVES)) {
            return;
        }
        BlockState above = level.getBlockState(ground.above());
        boolean snow = above.is(Blocks.SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW);
        if (mark.kind() == Kind.DRIFT) {
            if (snow) {
                level.setBlock(ground, Blocks.POWDER_SNOW.defaultBlockState(), FLAGS);
                if (above.is(Blocks.SNOW)) {
                    level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            return;
        }
        double y = above.is(Blocks.SNOW) ? ground.getY() + 1 + (above.getValue(SnowLayerBlock.LAYERS) - 1) / 8.0 : ground.getY() + 1;
        Vec3 at = new Vec3(mark.at().x, y + 0.01, mark.at().z);
        BlockState trodden = snow ? Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        switch (mark.kind()) {
            case PRINT -> Clues.mark(level, at, trodden, PRINT_WIDTH, PRINT_LENGTH, mark.along(), NAME);
            case DRAG -> Clues.mark(level, at, trodden, DRAG_WIDTH, (float) DRAG_STEP, mark.along(), NAME);
            case BLOOD -> Clues.mark(level, at.add(0, 0.005, 0), Blocks.RED_CONCRETE.defaultBlockState(), BLOOD_SIZE, BLOOD_SIZE, mark.along(), NAME);
            default -> {}
        }
    }

    private static void snowfield(ServerLevel level, BlockPos centre) {
        int radius = Balance.get().frost().snowRadiusBlocks();
        Holder<Biome> snowy = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!inside(dx, dz, radius)) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                BlockPos ground = new BlockPos(x, Ground.land(level, x, z), z);
                BlockState state = level.getBlockState(ground);
                for (BlockPos above = ground.above(); level.getBlockState(above).canBeReplaced() && !level.getBlockState(above).isAir()
                        && level.getFluidState(above).isEmpty(); above = above.above()) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
                }
                if (level.getFluidState(ground).isSource()) {
                    level.setBlock(ground, Blocks.ICE.defaultBlockState(), FLAGS);
                } else if (state.isFaceSturdy(level, ground, Direction.UP)) {
                    if (state.hasProperty(SnowyDirtBlock.SNOWY)) {
                        level.setBlock(ground, state.setValue(SnowyDirtBlock.SNOWY, true), FLAGS);
                    }
                    level.setBlock(ground.above(), Blocks.SNOW.defaultBlockState(), FLAGS);
                }
            }
        }
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int cx = (centre.getX() - radius) >> 4; cx <= (centre.getX() + radius) >> 4; cx++) {
            for (int cz = (centre.getZ() - radius) >> 4; cz <= (centre.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> inside(QuartPos.toBlock(qx) - centre.getX(), QuartPos.toBlock(qz) - centre.getZ(), radius)
                        ? snowy : chunk.getNoiseBiome(qx, qy, qz), level.getChunkSource().randomState().sampler());
                chunk.setUnsaved(true);
                chunks.add(chunk);
            }
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    private static boolean inside(int x, int z, int radius) {
        double wobble = Math.sin(Math.atan2(z, x) * 5) * radius / 8.0;
        return x * x + z * z <= (radius + wobble) * (radius + wobble);
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        Progress progress = Progress.of(server);
        BoundingBox box = BoundingBox.fromCorners(progress.place(HALL_LOW).orElseThrow(), progress.place(HALL_HIGH).orElseThrow());
        if (!Memories.copy(server, dream, box)) {
            return false;
        }
        BlockPos stone = progress.place(STONE).orElseThrow();
        if (dream.getBlockEntity(stone) instanceof CallingStoneBlockEntity calling) {
            calling.hold(Optional.of(COLD));
        }
        BoundingBox shaft = BoundingBox.fromCorners(progress.place(SHAFT_LOW).orElseThrow(), progress.place(SHAFT_HIGH).orElseThrow());
        List<Dead> sleepers = dead(dream, box, stone, shaft);
        for (int index = 0; index < Math.min(SLEEPERS.length, sleepers.size()); index++) {
            People.spawn(dream, SLEEPERS[index], Vec3.atBottomCenterOf(sleepers.get(index).at()), sleepers.get(index).yaw(), "sleeping", Memories.ROLE + SLEEPER);
        }
        BlockPos stair = progress.place(HALL).orElseThrow();
        Vec3 out = Vec3.atLowerCornerOf(progress.place(HOUSE_SITE).orElse(stair).subtract(stair)).multiply(1, 0, 1).normalize();
        Vec3 across = new Vec3(-out.z, 0, out.x);
        for (int index = 0; index < COATS; index++) {
            Vec3 at = Vec3.atBottomCenterOf(stair).add(out.scale(COATS_OUT + index % 2 * 2)).add(across.scale((index - 1) * 2));
            int x = (int) Math.floor(at.x);
            int z = (int) Math.floor(at.z);
            Mob coat = (index == 1 ? EntityType.PILLAGER : EntityType.VINDICATOR).create(dream);
            coat.moveTo(at.x, Ground.land(dream, x, z) + 1, at.z, yaw(out.scale(-1)), 0);
            coat.setYHeadRot(yaw(out.scale(-1)));
            coat.setNoAi(true);
            coat.setInvulnerable(true);
            coat.addTag(Memories.ROLE + GREY_COAT);
            dream.addFreshEntity(coat);
        }
        return true;
    }

    static void unbuild(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        switch (name) {
            case NAME -> progress.places.remove(HOUSE);
            case HALL, STONE -> progress.places.remove(HALL_LOW);
            case TRACKS -> progress.scratch(LAID).remove(LAID);
            default -> progress.places.remove(LAKE_ORIGIN);
        }
        progress.setDirty();
    }

    static boolean built(MinecraftServer server) {
        return Progress.of(server).place(HALL_LOW).isPresent();
    }

    @SubscribeEvent
    public static void onReadLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != Level.OVERWORLD
                || !(player.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity)) {
            return;
        }
        Progress progress = Progress.of(player.server);
        if (progress.place(HALL_LOW).isEmpty() || !BoundingBox.fromCorners(progress.place(HALL_LOW).get(), progress.place(HALL_HIGH).orElseThrow())
                .isInside(event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Halls.openBook(player, COLD);
    }

    static Rotation facing(Vec3 along) {
        Rotation best = Rotation.NONE;
        double most = -Double.MAX_VALUE;
        for (Rotation turn : Rotation.values()) {
            BlockPos front = StructureTemplate.transform(new BlockPos(0, 0, 1), Mirror.NONE, turn, BlockPos.ZERO);
            double toward = front.getX() * along.x + front.getZ() * along.z;
            if (toward > most) {
                most = toward;
                best = turn;
            }
        }
        return best;
    }

    private static Vec3 direction(float radians) {
        return new Vec3(Math.cos(radians), 0, Math.sin(radians));
    }

    static float yaw(Vec3 toward) {
        return (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
    }

    private static BlockPos local(JsonArray xyz) {
        return Prologue.vector(xyz);
    }
}
