package io.github.roman16.wayfarer;

import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Weather {
    private static final int CHECK_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;

    private Weather() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        ServerLevelData data = event.getServer().getWorldData().overworldData();
        int most = Balance.get().weather().rainMostSeconds() * TICKS_PER_SECOND;
        if (data.isRaining() && data.getRainTime() > most) {
            data.setRainTime(most);
        }
        if (data.isThundering() && data.getThunderTime() > most) {
            data.setThunderTime(most);
        }
    }
}
