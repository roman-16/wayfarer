package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class WendCamp {
    static final String NAME = "wend_camp";
    static final String CHAIR = "wend_camp_chair";
    static final ResourceLocation NOW = Wayfarer.id("wend_camp/now");
    static final ResourceLocation THEN = Wayfarer.id("wend_camp/then");
    private static final String SPOT = "wend_camp_spot";
    private static final String ORIGIN = "wend_camp_origin";
    private static final String TURN = "wend_camp_turn";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> PLAN = Bundled.json("/wayfarer/wend_camp.json");
    private static final int SAMPLE = 64;
    private static final int TURN_STEP = 15;
    private static final int WIDEST = 90;

    private WendCamp() {}

    static void find(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        if (progress.place(SPOT).isPresent()) {
            return;
        }
        int ahead = Balance.get().wendCamp().aheadBlocks();
        BlockPos from = Caravan.last(server);
        Vec3 way = Caravan.way(server);
        Unseen.Heights heights = new Unseen.Heights(level);
        BlockPos spot = null;
        BlockPos dry = null;
        for (int turned = 0; turned <= WIDEST && spot == null; turned += TURN_STEP) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                for (int out = ahead; out >= ahead / 2 && spot == null; out -= SAMPLE) {
                    int x = from.getX() + (int) Math.round(bent.x * out);
                    int z = from.getZ() + (int) Math.round(bent.z * out);
                    if (heights.wet(x, z)) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x, heights.surface(x, z), z);
                    dry = dry == null ? at : dry;
                    Holder<Biome> biome = heights.biome(x, at.getY(), z);
                    if (!Unseen.snowy(biome, at)) {
                        spot = at;
                    }
                }
                if (spot != null) {
                    break;
                }
            }
        }
        if (spot == null) {
            spot = dry;
        }
        if (spot == null) {
            int x = from.getX() + (int) Math.round(way.x * ahead);
            int z = from.getZ() + (int) Math.round(way.z * ahead);
            spot = new BlockPos(x, heights.surface(x, z), z);
        }
        Rotation turn = Frost.facing(Vec3.atLowerCornerOf(from.subtract(spot)).multiply(1, 0, 1).normalize());
        progress.place(SPOT, spot);
        progress.place(NAME, spot);
        progress.yaws.put(TURN, (float) turn.ordinal());
        progress.setDirty();
        LOGGER.info("his last camp lies at {}", spot);
    }

    static boolean ready(MinecraftServer server) {
        return Progress.of(server).place(ORIGIN).isPresent();
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> spot = progress.place(SPOT);
        ServerLevel level = Engine.overworld(server);
        if (spot.isEmpty() || progress.place(ORIGIN).isPresent() || !Halls.loaded(level, spot.get())) {
            return;
        }
        Rotation turn = turn(server);
        BlockPos origin = Camps.place(level, NOW, spot.get(), turn);
        progress.place(ORIGIN, origin);
        progress.place(NAME, world(server, "hearth"));
        progress.place(CHAIR, world(server, "chair"));
        progress.ward(NAME, level.getStructureManager().getOrCreate(NOW).getBoundingBox(Camps.settings(level, NOW, turn), origin));
        progress.setDirty();
        Halls.release(level, spot.get());
        Clues.marker(level, world(server, "chair"), "journal");
        Clues.marker(level, world(server, "hearth"), "hearthstone");
        LOGGER.info("his last camp stands at {}, turned {}", origin, turn);
        Journal.syncAll(server);
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        Camps.build(dream, THEN, origin(server), turn(server));
        return true;
    }

    static BlockPos origin(MinecraftServer server) {
        return Progress.of(server).place(ORIGIN).orElseThrow(() -> new IllegalStateException("his last camp is not built yet"));
    }

    static Rotation turn(MinecraftServer server) {
        return Rotation.values()[Progress.of(server).yaws.getOrDefault(TURN, 0f).intValue()];
    }

    static BlockPos world(MinecraftServer server, String key) {
        ServerLevel level = Engine.overworld(server);
        return origin(server).offset(StructureTemplate.calculateRelativePosition(Camps.settings(level, NOW, turn(server)),
                Prologue.vector(PLAN.get().getAsJsonArray(key))));
    }
}
