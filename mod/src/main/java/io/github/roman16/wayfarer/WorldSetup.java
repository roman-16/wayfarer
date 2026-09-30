package io.github.roman16.wayfarer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
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
        Camps.place(level, Wayfarer.id("last_camp"), level.getSharedSpawnPos());
    }
}
