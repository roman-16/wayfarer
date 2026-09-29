package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class WorldSetup {
    private static final String NAME = "wayfarer_world";

    private WorldSetup() {}

    private static final class Done extends SavedData {
        boolean done;

        static Done load(CompoundTag tag, HolderLookup.Provider registries) {
            Done state = new Done();
            state.done = tag.getBoolean("done");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("done", done);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Done state = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Done::new, Done::load, null), NAME);
        if (state.done) {
            return;
        }
        server.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(Balance.INSTANCE.sleep().playersPercentage(), server);
        server.setDifficulty(net.minecraft.world.Difficulty.HARD, true);
        placeLastCamp(server.overworld());
        state.done = true;
        state.setDirty();
    }

    private static void placeLastCamp(ServerLevel level) {
        StructureTemplate camp = level.getStructureManager().getOrCreate(Wayfarer.id("last_camp"));
        Vec3i size = camp.getSize();
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() - size.getX() / 2;
        int z = spawn.getZ() - size.getZ() / 2;
        level.getChunk(spawn);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos origin = new BlockPos(x, y - 1, z);
        camp.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
    }
}
