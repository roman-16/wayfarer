package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.ShrinePedestalBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class TideFolk {
    static final String NAME = "tide";
    static final String HALL = "tide_hall";
    private static final String VILLAGE = "tide_village";
    private static final String SECOND = "tide_second";
    private static final String[] WARDENS = {"tide_warden_1", "tide_warden_2"};
    private static final ResourceLocation TEMPLATE = Wayfarer.id("tide/village");
    private static final HallSite SITE = new HallSite(NAME, Wayfarer.id("shrine/black"), NAME, 2);
    private static final ResourceLocation SECOND_SHRINE = Wayfarer.id("shrine/exotic");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/tide.json");
    private static final int BESIDE = 20;
    private static final int LOOK = 24;
    private static final int HALL_SEAT = 3;
    private static final int[] DAIS = {2, 1};

    private TideFolk() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.find(server, progress.place(Caravan.NAME).or(() -> progress.place(Voyage.FAR_SHORE)).orElseGet(() -> Caravan.last(server)));
    }

    static void reset() {
        SITE.reset();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return name.equals(HALL) ? progress.place(HALL).isPresent() : progress.place(VILLAGE).isPresent();
    }

    static void tick(MinecraftServer server) {
        SITE.tick(server);
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.entrance(progress).filter(top -> (progress.place(VILLAGE).isEmpty() || progress.place(HALL).isEmpty()) && Halls.loaded(level, top)).ifPresent(top -> {
            if (progress.place(VILLAGE).isEmpty()) {
                village(server, level, top);
            }
            if (progress.place(HALL).isEmpty()) {
                hall(server, level);
            }
            Halls.release(level, top);
        });
    }

    private static Vec3 seaward(ServerLevel level, BlockPos top) {
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        Vec3 best = new Vec3(0, 0, 1);
        int wettest = -1;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int wet = 0;
            for (int out = LOOK / 3; out <= LOOK; out += LOOK / 3) {
                BlockPos at = top.relative(side, out);
                BlockPos surface = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - 1, at.getZ());
                if (surface.getY() < sea && level.getFluidState(surface).is(FluidTags.WATER)) {
                    wet++;
                }
            }
            if (wet > wettest) {
                wettest = wet;
                best = new Vec3(side.getStepX(), 0, side.getStepZ());
            }
        }
        return best;
    }

    private static void village(MinecraftServer server, ServerLevel level, BlockPos top) {
        Progress progress = Progress.of(server);
        Vec3 sea = seaward(level, top);
        Vec3 along = new Vec3(-sea.z, 0, sea.x);
        Rotation turn = Frost.facing(sea);
        BlockPos centre = BlockPos.containing(Vec3.atLowerCornerOf(top).add(along.scale(BESIDE)));
        BlockPos origin = Camps.place(level, TEMPLATE, centre, turn);
        StructurePlaceSettings settings = Camps.settings(level, TEMPLATE, turn);
        JsonObject plan = PLAN.get();
        seat(progress, origin, settings, turn, "tide_morwen", plan.getAsJsonArray("morwen"));
        JsonArray wardens = plan.getAsJsonArray("wardens");
        for (int index = 0; index < WARDENS.length; index++) {
            BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(wardens.get(index).getAsJsonArray())));
            Vec3 toward = Vec3.atLowerCornerOf(top.subtract(at)).multiply(1, 0, 1).normalize().add(sea.scale(-1)).normalize();
            progress.place("seat_" + WARDENS[index], at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ())));
            progress.yaws.put("seat_" + WARDENS[index], Frost.yaw(toward));
        }
        progress.place(VILLAGE, origin);
        progress.place(NAME, origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(plan.getAsJsonArray("morwen")))));
        progress.ward(NAME, level.getStructureManager().getOrCreate(TEMPLATE).getBoundingBox(settings, origin));
        progress.setDirty();
        LOGGER.info("the Tide Folk's village stands at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    private static void seat(Progress progress, BlockPos origin, StructurePlaceSettings settings, Rotation turn, String name, JsonArray spot) {
        BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, Prologue.vector(spot)));
        BlockPos facing = StructureTemplate.transform(new BlockPos(spot.get(3).getAsInt(), 0, spot.get(4).getAsInt()), Mirror.NONE, turn, BlockPos.ZERO);
        progress.place("seat_" + name, at);
        progress.yaws.put("seat_" + name, Frost.yaw(Vec3.atLowerCornerOf(facing)));
    }

    private static void hall(MinecraftServer server, ServerLevel level) {
        Progress progress = Progress.of(server);
        BlockPos low = SITE.low(progress).orElseThrow();
        BlockPos high = SITE.high(progress).orElseThrow();
        Optional<BlockPos> stone = Halls.stone(level, low, high);
        if (stone.isEmpty()) {
            LOGGER.error("the Tide Folk failed: their hall between {} and {} has no calling stone", low, high);
            progress.place(HALL, SITE.entrance(progress).orElse(low));
            progress.setDirty();
            return;
        }
        BlockPos pedestal = stone.get();
        BlockPos beside = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int out : DAIS) {
                BlockPos at = pedestal.relative(side, out);
                if (beside == null && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                    beside = at;
                }
            }
        }
        if (beside != null) {
            level.setBlock(beside, Wayfarer.SHRINE_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(beside) instanceof ShrinePedestalBlockEntity second) {
                second.hold(Journey.claimed(server).contains(SECOND_SHRINE) ? Optional.empty() : Optional.of(SECOND_SHRINE));
            }
            progress.place(SECOND, beside);
        }
        Vec3 inward = Vec3.atLowerCornerOf(SITE.entrance(progress).orElse(pedestal).subtract(pedestal)).multiply(1, 0, 1).normalize();
        Vec3 aside = beside != null ? Vec3.atLowerCornerOf(pedestal.subtract(beside)).normalize() : new Vec3(-inward.z, 0, inward.x);
        BlockPos seat = BlockPos.containing(Vec3.atLowerCornerOf(pedestal).add(aside.scale(HALL_SEAT)));
        progress.place("seat_tide_inside", seat);
        progress.yaws.put("seat_tide_inside", Frost.yaw(inward));
        progress.place(HALL, pedestal);
        progress.setDirty();
        LOGGER.info("the Tide Folk's stone is at {}, the Drowned City's beside it at {}", pedestal, beside);
    }
}
