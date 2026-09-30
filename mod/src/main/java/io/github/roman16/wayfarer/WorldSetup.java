package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class WorldSetup {
    private static final String NAME = "wayfarer_world";
    private static final int CLEARANCE = 10;

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
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int column = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                for (int fill = column; fill < origin.getY(); fill++) {
                    level.setBlock(new BlockPos(x + dx, fill, z + dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                for (int clear = origin.getY() + 1; clear <= origin.getY() + CLEARANCE; clear++) {
                    level.setBlock(new BlockPos(x + dx, clear, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        camp.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
    }
}
