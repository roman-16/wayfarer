package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class Prologue {
    public static final String LAST_CAMP = "last_camp";
    static final String TENTS = "nomadictents";
    private static final String COLD_TENT = "yurt";
    private static final int UPROOTED = 4;
    @Nullable
    private static MinecraftServer tentFor;
    private static String tent = COLD_TENT;
    private static final String STRANGER = "stranger";
    private static final String DYING = "sitting_leaning";
    private static final int SIGN_LINES = 4;
    private static final int SIGN_WIDTH = 15;
    private static final Bundled<JsonObject> CAMP = Bundled.json("/wayfarer/last_camp.json");

    private Prologue() {}

    public static String tent() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return COLD_TENT;
        }
        if (server != tentFor) {
            tentFor = server;
            BlockPos camp = Progress.of(server).place(LAST_CAMP).orElseGet(() -> Engine.overworld(server).getSharedSpawnPos());
            tent = tent(Engine.overworld(server).getBiome(camp));
        }
        return tent;
    }

    private static String tent(Holder<Biome> land) {
        if (land.is(Tags.Biomes.IS_DESERT) || land.is(BiomeTags.IS_BADLANDS)) {
            return "bedouin";
        }
        if (land.is(BiomeTags.IS_SAVANNA)) {
            return "indlu";
        }
        if (land.is(BiomeTags.IS_JUNGLE) || land.is(Tags.Biomes.IS_SWAMP)) {
            return "shamiyana";
        }
        if (land.is(Tags.Biomes.IS_COLD) || land.is(Tags.Biomes.IS_SNOWY) || land.is(BiomeTags.IS_MOUNTAIN)) {
            return COLD_TENT;
        }
        return "tepee";
    }

    public static void lastCamp(ServerLevel level, BlockPos origin) {
        Progress progress = Progress.of(level.getServer());
        progress.place(LAST_CAMP, origin.offset(vector(CAMP.get().getAsJsonArray("fire"))));
        BoundingBox camp = level.getStructureManager().getOrCreate(Wayfarer.id(LAST_CAMP)).getBoundingBox(new StructurePlaceSettings(), origin);
        progress.ward(LAST_CAMP, camp);
        for (BlockPos at : BlockPos.betweenClosed(camp.minX() - UPROOTED, camp.minY(), camp.minZ() - UPROOTED, camp.maxX() + UPROOTED, camp.maxY(), camp.maxZ() + UPROOTED)) {
            BlockState plant = level.getBlockState(at);
            if (plant.canBeReplaced() && !plant.isAir() && plant.getFluidState().isEmpty() && !plant.canSurvive(level, at)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, AABB.of(camp).inflate(UPROOTED)).forEach(Entity::discard);
        JsonArray stranger = CAMP.get().getAsJsonArray(STRANGER);
        People.person(level, STRANGER, Vec3.atLowerCornerOf(origin).add(stranger.get(0).getAsDouble(), stranger.get(1).getAsDouble(),
                stranger.get(2).getAsDouble()), stranger.get(3).getAsFloat(), DYING);
        JsonArray wake = CAMP.get().getAsJsonArray("wake");
        BlockPos spawn = origin.offset(wake.get(0).getAsInt(), wake.get(1).getAsInt(), wake.get(2).getAsInt());
        level.setDefaultSpawnPos(spawn, wake.get(3).getAsFloat());
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
    }

    static boolean past(MinecraftServer server, ServerLevel dream) {
        BlockPos origin = Engine.at(server, LAST_CAMP).subtract(vector(CAMP.get().getAsJsonArray("fire")));
        dream.getStructureManager().getOrCreate(Wayfarer.id(LAST_CAMP)).placeInWorld(dream, origin, origin, new StructurePlaceSettings(), dream.random, Block.UPDATE_CLIENTS);
        return true;
    }

    static BlockPos grave(ServerLevel level) {
        BlockPos origin = Engine.at(level.getServer(), LAST_CAMP).subtract(vector(CAMP.get().getAsJsonArray("fire"))).offset(vector(CAMP.get().getAsJsonArray("grave")));
        StructureTemplate grave = level.getStructureManager().getOrCreate(Wayfarer.id("last_camp/grave"));
        grave.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        return origin.offset(vector(CAMP.get().getAsJsonArray("sign")));
    }

    public static void engrave(ServerLevel level, BlockPos sign, String words) {
        if (!(level.getBlockEntity(sign) instanceof SignBlockEntity stone)) {
            return;
        }
        if (words.isEmpty()) {
            level.removeBlock(sign, false);
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String word : words.split(" ")) {
            if (!lines.isEmpty() && lines.getLast().length() + 1 + word.length() <= SIGN_WIDTH) {
                lines.set(lines.size() - 1, lines.getLast() + " " + word);
            } else {
                lines.add(word);
            }
        }
        SignText text = new SignText();
        for (int line = 0; line < Math.min(lines.size(), SIGN_LINES); line++) {
            text = text.setMessage(line, Component.literal(lines.get(line)));
        }
        stone.setText(text, true);
        stone.setWaxed(true);
        stone.setChanged();
        level.sendBlockUpdated(sign, stone.getBlockState(), stone.getBlockState(), Block.UPDATE_ALL);
    }

    static BlockPos vector(JsonArray xyz) {
        return new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
    }

}
