package io.github.roman16.wayfarer;

import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Background {
    public static final boolean ON = Boolean.getBoolean(Wayfarer.MODID + ".background");
    private static final long IDLE_MINUTES = 30;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long used = System.nanoTime();

    private Background() {}

    public static void use() {
        used = System.nanoTime();
    }

    public static boolean idle() {
        return ON && System.nanoTime() - used > TimeUnit.MINUTES.toNanos(IDLE_MINUTES);
    }

    public static void leave(String what) {
        LOGGER.info("{} stops after {} minutes without a batch", what, IDLE_MINUTES);
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        use();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer() && server.isRunning() && idle()) {
            leave("the dev server");
            server.halt(false);
        }
    }
}
