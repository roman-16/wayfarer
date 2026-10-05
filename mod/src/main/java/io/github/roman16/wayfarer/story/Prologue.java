package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public final class Prologue {
    public static final String LAST_CAMP = "last_camp";
    private static final String STRANGER = "stranger";
    private static final JsonObject CAMP = json("/wayfarer/last_camp.json");

    private Prologue() {}

    public static void lastCamp(ServerLevel level, BlockPos origin) {
        Progress progress = Progress.of(level.getServer());
        progress.place(LAST_CAMP, origin.offset(vector(CAMP.getAsJsonArray("fire"))));
        JsonArray stranger = CAMP.getAsJsonArray(STRANGER);
        People.person(level, STRANGER, Vec3.atLowerCornerOf(origin).add(stranger.get(0).getAsDouble(), stranger.get(1).getAsDouble(),
                stranger.get(2).getAsDouble()), stranger.get(3).getAsFloat(), "rest");
        JsonArray wake = CAMP.getAsJsonArray("wake");
        BlockPos spawn = origin.offset(wake.get(0).getAsInt(), wake.get(1).getAsInt(), wake.get(2).getAsInt());
        level.setDefaultSpawnPos(spawn, wake.get(3).getAsFloat());
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
    }

    static BlockPos grave(ServerLevel level) {
        BlockPos origin = Engine.at(level.getServer(), LAST_CAMP).subtract(vector(CAMP.getAsJsonArray("fire"))).offset(vector(CAMP.getAsJsonArray("grave")));
        StructureTemplate grave = level.getStructureManager().getOrCreate(Wayfarer.id("last_camp/grave"));
        grave.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        return origin.offset(vector(CAMP.getAsJsonArray("sign")));
    }

    static BlockPos vector(JsonArray xyz) {
        return new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
    }

    static JsonObject json(String name) {
        try (var stream = Prologue.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException(name + " is missing from the mod jar; run just generate");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
