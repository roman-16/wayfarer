package io.github.roman16.wayfarer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Weather {
    private static final String DATA = Wayfarer.MODID + "_weather";
    private static final int HELD_TICKS = 2;
    private static final int TICKS_PER_SECOND = 20;

    private Weather() {}

    private static final class Last extends SavedData {
        private long rainedDay = Long.MIN_VALUE / 2;
        private boolean raining;

        static Last of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Last::new, Last::load, null), DATA);
        }

        private static Last load(CompoundTag tag, HolderLookup.Provider registries) {
            Last last = new Last();
            last.rainedDay = tag.getLong("rainedDay");
            last.raining = tag.getBoolean("raining");
            return last;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("rainedDay", rainedDay);
            tag.putBoolean("raining", raining);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevelData data = server.getWorldData().overworldData();
        Balance.Weather balance = Balance.get().weather();
        int most = balance.rainMostSeconds() * TICKS_PER_SECOND;
        if (data.isRaining() && data.getRainTime() > most) {
            data.setRainTime(most);
        }
        if (data.isThundering() && data.getThunderTime() > most) {
            data.setThunderTime(most);
        }
        Last last = Last.of(server);
        long today = Math.floorDiv(server.overworld().getDayTime(), Clock.DAY);
        if (data.isRaining() != last.raining) {
            last.raining = data.isRaining();
            last.rainedDay = today;
            last.setDirty();
        }
        if (!data.isRaining() && today < last.rainedDay + balance.daysBetweenRain()) {
            data.setRainTime(Math.max(data.getRainTime(), HELD_TICKS));
            data.setThunderTime(Math.max(data.getThunderTime(), HELD_TICKS));
        }
    }
}
