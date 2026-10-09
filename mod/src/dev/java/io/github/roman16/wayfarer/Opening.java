package io.github.roman16.wayfarer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Opening {
    private static final int OWNER = 4;
    private static final float HURRIED = 1024;
    private static long startGame;
    private static long startDay;
    private static Phase phase = Phase.RUNNING;
    private static float pace;
    private static int target;

    private enum Phase { HELD, RUNNING, STEPPING }

    private Opening() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("still")
                        .requires(source -> source.hasPermission(OWNER))
                        .executes(context -> hold(context.getSource()))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(context -> still(context.getSource(), IntegerArgumentType.getInteger(context, "ticks"))))));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        startGame = event.getServer().overworld().getGameTime();
        startDay = event.getServer().overworld().getDayTime();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().tickCount = (int) (event.getEntity().level().getGameTime() - startGame);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        phase = Phase.RUNNING;
        pace = 0;
    }

    public static boolean held(MinecraftServer server) {
        if (phase == Phase.HELD && !server.tickRateManager().isFrozen()) {
            phase = Phase.RUNNING;
        }
        return phase == Phase.HELD;
    }

    public static boolean ticked(MinecraftServer server) {
        ServerTickRateManager rate = server.tickRateManager();
        if (phase == Phase.STEPPING && !rate.isSteppingForward()) {
            place(server);
        }
        return held(server);
    }

    private static int hold(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerTickRateManager rate = server.tickRateManager();
        rate.setFrozen(true);
        if (pace > 0) {
            rate.setTickRate(pace);
            pace = 0;
        }
        server.forceTimeSynchronization();
        phase = Phase.HELD;
        source.sendSuccess(() -> Component.literal("still: the world stands where it is"), false);
        return 1;
    }

    private static int still(CommandSourceStack source, int ticks) {
        MinecraftServer server = source.getServer();
        long ran = server.overworld().getGameTime() - startGame;
        if (ran > ticks) {
            source.sendFailure(Component.literal("still failed: the world ran %d ticks since it opened, more than %d".formatted(ran, ticks)));
            return 0;
        }
        ServerTickRateManager rate = server.tickRateManager();
        rate.setFrozen(true);
        target = ticks;
        if (ran < ticks) {
            phase = Phase.STEPPING;
            pace = rate.tickrate();
            rate.setTickRate(HURRIED);
            rate.stepGameIfPaused((int) (ticks - ran));
        } else {
            place(server);
        }
        source.sendSuccess(() -> Component.literal("still: the world stands %d ticks after it opened, %d of them run now".formatted(ticks, ticks - ran)), false);
        return 1;
    }

    private static void place(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ((ServerLevelData) overworld.getLevelData()).setGameTime(startGame + target);
        overworld.setDayTimeFraction(0);
        overworld.setDayTime(startDay + target);
        server.forceTimeSynchronization();
        phase = Phase.HELD;
    }
}
