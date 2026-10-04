package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class ClockSpike {
    private static final int CHECK_TICKS = 20;
    private static final long DAY = 24000;
    private static final int GAMEMASTER = 2;
    private static final long HOUR = 1000;
    private static final float HOLD = 1f / 1024;
    private static final int MORNING_HOUR = 6;
    private static final int OPEN_SKY = 15;
    private static final double STILL_BLOCKS = 1;
    private static final int TICKS_PER_SECOND = 20;
    private static final float VANILLA = -1;
    private static final Map<UUID, Rest> rests = new HashMap<>();
    private static Mode mode = Mode.OFF;
    private static long from;
    private static long target;
    private static double pace = 1;
    private static long day;
    @Nullable
    private static BlockPos caravan;
    private static long monsters = Long.MAX_VALUE;
    private static long eveningTicks;

    private ClockSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("clock")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("caravan").then(Commands.argument("camp", BlockPosArgument.blockPos())
                                .executes(context -> caravan(context.getSource(), BlockPosArgument.getBlockPos(context, "camp")))))
                        .then(Commands.literal("night").executes(context -> night(context.getSource())))
                        .then(Commands.literal("off").executes(context -> off(context.getSource())))
                        .then(Commands.literal("road").executes(context -> road(context.getSource())))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))));
    }

    private static int road(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        road(level);
        say(source.getServer(), "clock: on the road, easing from %s to %s".formatted(clock(from), clock(target)));
        return 1;
    }

    private static void road(ServerLevel level) {
        mode = Mode.ROAD;
        from = level.getDayTime();
        day = Math.floorDiv(from, DAY);
        target = day * DAY + ticks(Balance.INSTANCE.clock().road());
        pace = 1;
        caravan = null;
        monsters = Long.MAX_VALUE;
        rests.clear();
        speed(level);
    }

    private static int caravan(CommandSourceStack source, BlockPos camp) {
        if (mode != Mode.ROAD) {
            say(source.getServer(), "clock failed: make for the caravan needs the clock on the road, it is %s".formatted(name(mode)));
            return 0;
        }
        caravan = camp;
        Balance.Clock clock = Balance.INSTANCE.clock();
        say(source.getServer(), "clock: make for the caravan at %d %d; from %s the evening begins once %d%% of the team is within %d blocks, before it once they have rested at its fire"
                .formatted(camp.getX(), camp.getZ(), clock.runFrom(), Balance.INSTANCE.sleep().playersPercentage(), clock.caravanBlocks()));
        return 1;
    }

    private static int night(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        mode = Mode.NIGHT;
        from = level.getDayTime();
        day = Math.floorDiv(from, DAY);
        target = (day + 1) * DAY;
        pace = 1;
        speed(level);
        say(source.getServer(), "clock: a hard night, running on at normal speed from %s to dawn".formatted(clock(from)));
        return 1;
    }

    private static int off(CommandSourceStack source) {
        mode = Mode.OFF;
        caravan = null;
        source.getServer().overworld().setDayTimePerTick(VANILLA);
        source.getServer().overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, source.getServer());
        say(source.getServer(), "clock: off, the day and the night run as in vanilla");
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        long now = level.getDayTime();
        String text = mode == Mode.OFF ? "off, %s at vanilla speed".formatted(clock(now))
                : "%s, %s (%d), %.3f of normal speed, easing from %s to %s".formatted(name(mode), clock(now), Math.floorMod(now, DAY),
                        level.getDayTimePerTick(), clock(from), clock(target));
        say(source.getServer(), "clock: " + text);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (mode == Mode.OFF) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getDayTime();
        if (mode == Mode.EVENING) {
            eveningTicks++;
            if (now >= monsters) {
                say(server, "clock: monsters can come out now, at %s, %.1f s after the evening began".formatted(clock(now),
                        eveningTicks / (double) TICKS_PER_SECOND));
                monsters = Long.MAX_VALUE;
            }
        }
        if (Math.floorDiv(now, DAY) > day) {
            road(level);
            say(server, "clock: dawn, a new day on the road, easing from %s to %s".formatted(clock(from), clock(target)));
            return;
        }
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        if (mode == Mode.ROAD && caravan != null) {
            evening(server, level, now);
        }
        speed(level);
    }

    private static void evening(MinecraftServer server, ServerLevel level, long now) {
        Balance.Clock clock = Balance.INSTANCE.clock();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        int share = Balance.INSTANCE.sleep().playersPercentage();
        if (Math.floorMod(now, DAY) >= ticks(clock.runFrom())) {
            int reach = clock.caravanBlocks();
            long near = players.stream().filter(player -> near(player, level, reach)).count();
            if (near * 100 >= (long) players.size() * share) {
                begin(server, level, now, clock.duskRunSeconds(), "the evening, %d of %d within %d blocks of the caravan".formatted(near, players.size(), reach));
            }
            return;
        }
        Balance.Fire fire = Balance.INSTANCE.fire();
        long rested = players.stream().filter(player -> rested(player, level, fire)).count();
        if (rested * 100 >= (long) players.size() * share) {
            begin(server, level, now, clock.afternoonSeconds(), "the afternoon passes at the fire, %d of %d rested there".formatted(rested, players.size()));
        }
    }

    private static boolean near(ServerPlayer player, ServerLevel level, int blocks) {
        return player.level() == level && player.distanceToSqr(caravan.getX() + 0.5, player.getY(), caravan.getZ() + 0.5) <= (double) blocks * blocks;
    }

    private static boolean rested(ServerPlayer player, ServerLevel level, Balance.Fire fire) {
        if (!near(player, level, fire.restBlocks())) {
            rests.remove(player.getUUID());
            return false;
        }
        Rest before = rests.get(player.getUUID());
        boolean still = before != null && before.at.distanceTo(player.position()) < STILL_BLOCKS;
        int seconds = still ? before.seconds + CHECK_TICKS / TICKS_PER_SECOND : 0;
        rests.put(player.getUUID(), new Rest(still ? before.at : player.position(), seconds));
        return seconds >= fire.restSeconds();
    }

    private static void begin(MinecraftServer server, ServerLevel level, long now, int seconds, String why) {
        mode = Mode.EVENING;
        from = now;
        target = Math.floorDiv(now, DAY) * DAY + ticks(Balance.INSTANCE.clock().evening());
        long out = monsterHour(level, now, target);
        long gap = target - from;
        pace = out <= now ? 1 : gap / (double) (TICKS_PER_SECOND * seconds) * Math.log(gap / (double) (target - out));
        monsters = out;
        eveningTicks = 0;
        rests.clear();
        say(server, "clock: %s; easing from %s to %s at %.2f times normal speed, so monsters can come out at %s in %d s"
                .formatted(why, clock(from), clock(target), pace, clock(out), out <= now ? 0 : seconds));
    }

    private static long monsterHour(ServerLevel level, long now, long until) {
        if (level.isThundering()) {
            return now;
        }
        int light = level.dimensionType().monsterSpawnLightTest().getMaxValue();
        for (long time = now; time < until; time++) {
            if (OPEN_SKY - darken(level, time) <= light) {
                return time;
            }
        }
        return until;
    }

    private static int darken(ServerLevel level, long time) {
        double rain = 1.0 - level.getRainLevel(1.0F) * 5.0F / 16.0;
        double thunder = 1.0 - level.getThunderLevel(1.0F) * 5.0F / 16.0;
        double sun = 0.5 + 2.0 * Mth.clamp(Mth.cos(level.dimensionType().timeOfDay(time) * (float) (Math.PI * 2)), -0.25, 0.25);
        return (int) ((1.0 - sun * rain * thunder) * 11.0);
    }

    private static void speed(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, level.getServer());
        if (mode == Mode.NIGHT) {
            level.setDayTimePerTick(1);
            return;
        }
        long left = target - level.getDayTime();
        float speed = left <= 0 ? HOLD : (float) Math.max(HOLD, pace * left / (target - from));
        level.setDayTimePerTick(speed);
    }

    private static long ticks(String clock) {
        String[] parts = clock.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return Math.floorMod(hour - MORNING_HOUR, 24) * HOUR + minute * HOUR / 60;
    }

    private static String clock(long time) {
        long ticks = Math.floorMod(time, DAY);
        long minutes = (ticks * 60 / HOUR + MORNING_HOUR * 60) % (24 * 60);
        return "%02d:%02d".formatted(minutes / 60, minutes % 60);
    }

    private static String name(Mode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private record Rest(Vec3 at, int seconds) {}

    private enum Mode {
        EVENING,
        NIGHT,
        OFF,
        ROAD
    }
}
