package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class FirstCamp {
    static final String NAME = "first_camp";
    static final ResourceLocation NOW = Wayfarer.id("first_camp/now");
    static final ResourceLocation THEN = Wayfarer.id("first_camp/then");
    private static final String SPOT = "first_camp_spot";
    private static final String ORIGIN = "first_camp_origin";
    private static final String TURN = "first_camp_turn";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/first_camp.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_first_camp", Comparator.comparingLong(ChunkPos::toLong));
    private static final String[] WAYS = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
    private static final int SAMPLE = 32;
    private static final int SHORE = 16;
    private static final int LOAD_RADIUS = 1;

    private FirstCamp() {}

    static void find(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(SPOT).isPresent()) {
            return;
        }
        BlockPos from = progress.place(Caravan.NAME).orElseGet(level::getSharedSpawnPos);
        Vec3 way = Caravan.way(server);
        Balance.FirstCamp balance = Balance.get().firstCamp();
        int aheadX = from.getX() + (int) Math.round(way.x * balance.aheadBlocks());
        int aheadZ = from.getZ() + (int) Math.round(way.z * balance.aheadBlocks());
        Unseen.Heights heights = new Unseen.Heights(level);
        BlockPos best = null;
        BlockPos lake = null;
        double nearest = Double.MAX_VALUE;
        for (int dx = -balance.lakeBlocks(); dx <= balance.lakeBlocks(); dx += SAMPLE) {
            for (int dz = -balance.lakeBlocks(); dz <= balance.lakeBlocks(); dz += SAMPLE) {
                int x = aheadX + dx;
                int z = aheadZ + dz;
                double distance = (double) dx * dx + dz * dz;
                if (distance >= nearest || !heights.wet(x, z)) {
                    continue;
                }
                BlockPos water = new BlockPos(x, heights.surface(x, z), z);
                Holder<Biome> biome = heights.biome(x, water.getY(), z);
                if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || Unseen.snowy(biome, water)) {
                    continue;
                }
                BlockPos shore = shore(heights, x, z);
                if (shore != null) {
                    best = shore;
                    lake = new BlockPos(x, shore.getY(), z);
                    nearest = distance;
                }
            }
        }
        BlockPos spot = best != null ? best : new BlockPos(aheadX, heights.surface(aheadX, aheadZ), aheadZ);
        Rotation turn = lake == null ? Rotation.NONE : facing(spot, lake);
        int half = PLAN.get().get("size").getAsInt() / 2;
        BlockPos centre = spot.offset(StructureTemplate.transform(new BlockPos(0, 0, -half), Mirror.NONE, turn, BlockPos.ZERO));
        progress.place(NAME, centre);
        progress.place(SPOT, centre);
        progress.yaws.put(TURN, (float) turn.ordinal());
        progress.setDirty();
        LOGGER.info("his first camp lies at {}{}", centre, lake != null ? ", by a lake" : ", with no lake near");
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> spot = progress.place(SPOT);
        if (spot.isEmpty() || progress.place(ORIGIN).isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        ChunkPos chunk = new ChunkPos(spot.get());
        level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        for (int x = chunk.x - LOAD_RADIUS; x <= chunk.x + LOAD_RADIUS; x++) {
            for (int z = chunk.z - LOAD_RADIUS; z <= chunk.z + LOAD_RADIUS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return;
                }
            }
        }
        Rotation turn = turn(server);
        BlockPos origin = Camps.place(level, NOW, spot.get(), turn);
        progress.place(ORIGIN, origin);
        progress.place(NAME, world(server, "hearth"));
        progress.setDirty();
        level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
        Clues.marker(level, pine(server).get(1), "notches");
        Clues.marker(level, world(server, "stake"), "stake");
        Clues.marker(level, world(server, "hearth"), "hearthstone");
        Clues.marker(level, world(server, "sack"), "sack");
        LOGGER.info("his first camp stands at {}, turned {}", origin, turn);
    }

    static boolean built(MinecraftServer server) {
        return Progress.of(server).place(ORIGIN).isPresent();
    }

    static BlockPos origin(MinecraftServer server) {
        return Progress.of(server).place(ORIGIN).orElseThrow(() -> new IllegalStateException("his first camp is not built yet"));
    }

    static BlockPos spot(MinecraftServer server) {
        return Progress.of(server).place(SPOT).orElseThrow(() -> new IllegalStateException("his first camp has no spot yet"));
    }

    static Rotation turn(MinecraftServer server) {
        return Rotation.values()[Progress.of(server).yaws.getOrDefault(TURN, 0f).intValue()];
    }

    static BlockPos world(MinecraftServer server, String key) {
        return world(server, at(PLAN.get().getAsJsonArray(key)));
    }

    static List<BlockPos> pine(MinecraftServer server) {
        return PLAN.get().getAsJsonArray("pine").asList().stream().map(JsonElement::getAsJsonArray).map(FirstCamp::at).map(rel -> world(server, rel)).toList();
    }

    static List<BlockPos> fallen(MinecraftServer server) {
        return PLAN.get().getAsJsonArray("fallen").asList().stream().map(JsonElement::getAsJsonArray).map(FirstCamp::at).map(rel -> world(server, rel)).toList();
    }

    static String direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        return WAYS[Math.floorMod((int) Math.round(angle / 45), WAYS.length)];
    }

    private static BlockPos world(MinecraftServer server, BlockPos relative) {
        ServerLevel level = Engine.overworld(server);
        return origin(server).offset(StructureTemplate.calculateRelativePosition(Camps.settings(level, NOW, turn(server)), relative));
    }

    private static BlockPos at(JsonArray position) {
        return new BlockPos(position.get(0).getAsInt(), position.get(1).getAsInt(), position.get(2).getAsInt());
    }

    private static Rotation facing(BlockPos spot, BlockPos lake) {
        int dx = lake.getX() - spot.getX();
        int dz = lake.getZ() - spot.getZ();
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        }
        return dx >= 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
    }


    private static BlockPos shore(Unseen.Heights heights, int x, int z) {
        for (int reach = SHORE; reach <= SAMPLE * 2; reach += SHORE) {
            for (int step = 0; step < WAYS.length; step++) {
                double angle = Math.PI * 2 * step / WAYS.length;
                int sx = x + (int) Math.round(Math.cos(angle) * reach);
                int sz = z + (int) Math.round(Math.sin(angle) * reach);
                BlockPos land = new BlockPos(sx, heights.surface(sx, sz), sz);
                if (!heights.wet(sx, sz) && !Unseen.snowy(heights.biome(sx, land.getY(), sz), land)) {
                    return land;
                }
            }
        }
        return null;
    }
}
