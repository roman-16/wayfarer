package io.github.roman16.wayfarer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Clock {
    public static final long DAY = 24000;
    private static final int CHECK_TICKS = 20;
    private static final long HOUR = 1000;
    private static final float HOLD = 1f / 1024;
    private static final int MORNING_HOUR = 6;
    private static final int TICKS_PER_SECOND = 20;
    private static final float VANILLA = -1;
    private static final Set<UUID> rested = new HashSet<>();
    private static Mode mode = Mode.OFF;
    private static long from;
    private static long target;
    private static double pace = 1;
    private static long day;
    @Nullable
    private static String after;
    @Nullable
    private static BlockPos caravan;

    private Clock() {}

    private enum Mode {
        EASE,
        EVENING,
        NIGHTFALL,
        OFF,
        PASS
    }

    public static boolean storied(MinecraftServer server) {
        return server.getGameRules().getBoolean(Wayfarer.STORY) && !Journey.over(server);
    }

    public static void road(ServerLevel level) {
        ease(level, Balance.get().clock().road());
        caravan = null;
    }

    public static void ease(ServerLevel level, String hour) {
        mode = Mode.EASE;
        from = level.getDayTime();
        day = Math.floorDiv(from, DAY);
        target = next(from, hour);
        pace = 1;
        after = null;
        speed(level);
    }

    public static long pass(ServerLevel level, String hour, int seconds, @Nullable String then) {
        long until = next(level.getDayTime(), hour);
        until(level, until, seconds, then);
        return until;
    }

    public static void until(ServerLevel level, long until, int seconds, @Nullable String then) {
        long now = level.getDayTime();
        if (now >= until) {
            arrive(level, now, then);
            return;
        }
        mode = Mode.PASS;
        from = now;
        day = Math.floorDiv(from, DAY);
        target = until;
        pace = (target - from) / (double) (seconds * TICKS_PER_SECOND);
        after = then;
        speed(level);
    }

    public static void jump(ServerLevel level, String hour, @Nullable String then) {
        long at = next(level.getDayTime(), hour);
        level.setDayTime(at);
        arrive(level, at, then);
    }

    private static void arrive(ServerLevel level, long now, @Nullable String then) {
        if (then != null) {
            ease(level, then);
            return;
        }
        mode = Mode.EASE;
        from = now;
        target = now;
        day = Math.floorDiv(now, DAY);
        pace = 1;
        after = null;
        speed(level);
    }

    public static void caravan(ServerLevel level, BlockPos camp) {
        caravan = camp.immutable();
        rested.clear();
    }

    public static boolean rested(ServerPlayer player) {
        return rested.contains(player.getUUID());
    }

    public static boolean morning(ServerLevel level) {
        return storied(level.getServer()) && Math.floorMod(level.getDayTime(), DAY) < ticks(Balance.get().clock().runFrom());
    }

    public static boolean atCaravan(ServerPlayer player) {
        return caravan != null && near(player, player.serverLevel(), Balance.get().fire().circleBlocks());
    }

    public static boolean afternoon(ServerLevel level) {
        return mode == Mode.EASE && caravan != null && Math.floorMod(level.getDayTime(), DAY) < ticks(Balance.get().clock().runFrom());
    }

    public static boolean nightfall() {
        return mode == Mode.NIGHTFALL || mode == Mode.EVENING;
    }

    public static boolean passed() {
        return mode != Mode.PASS;
    }

    public static boolean evening() {
        return mode == Mode.EVENING;
    }

    public static long next(long now, String hour) {
        long at = Math.floorDiv(now, DAY) * DAY + ticks(hour);
        return at <= now ? at + DAY : at;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStarted(ServerStartedEvent event) {
        mode = Mode.OFF;
        caravan = null;
        rested.clear();
        ServerLevel level = event.getServer().overworld();
        if (storied(event.getServer())) {
            road(level);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (!storied(server)) {
            if (mode != Mode.OFF) {
                mode = Mode.OFF;
                level.setDayTimePerTick(VANILLA);
            }
            return;
        }
        long now = level.getDayTime();
        if (mode == Mode.OFF || Math.floorDiv(now, DAY) > day && mode != Mode.PASS && mode != Mode.NIGHTFALL) {
            road(level);
            return;
        }
        if (mode == Mode.PASS && now >= target) {
            arrive(level, now, after);
            return;
        }
        if (mode == Mode.NIGHTFALL && now >= target || mode == Mode.EASE && now >= target && Math.floorMod(now, DAY) >= ticks(Balance.get().clock().evening())) {
            mode = Mode.EVENING;
            caravan = null;
            rested.clear();
            speed(level);
            return;
        }
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        if (mode == Mode.EASE) {
            evening(server, level, now);
        }
        speed(level);
    }

    private static void evening(MinecraftServer server, ServerLevel level, long now) {
        Balance.Clock clock = Balance.get().clock();
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator()).toList();
        if (players.isEmpty()) {
            return;
        }
        int share = Balance.get().sleep().playersPercentage();
        long hour = Math.floorMod(now, DAY);
        if (hour >= ticks(clock.runFrom())) {
            int reach = clock.caravanBlocks();
            long near = players.stream().filter(player -> caravan != null && near(player, level, reach) || camped(player)).count();
            if (near * 100 >= (long) players.size() * share) {
                begin(level, now, clock.duskRunSeconds());
            }
            return;
        }
        if (hour < ticks(clock.afternoon())) {
            return;
        }
        long sat = players.stream().filter(player -> rested.contains(player.getUUID()) || camped(player)).count();
        if (sat * 100 >= (long) players.size() * share) {
            begin(level, now, clock.afternoonSeconds());
        }
    }

    @SubscribeEvent
    public static void onRest(RestEvent event) {
        if (mode != Mode.EASE) {
            return;
        }
        if (caravan != null && event.fire().closerThan(caravan, Balance.get().fire().restBlocks())) {
            rested.add(event.player().getUUID());
        }
    }

    public static boolean camped(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return Fire.resting(player) && Fire.nearest(level, player.position(), Balance.get().fire().restBlocks())
                .filter(fire -> Tents.pitchedNear(level, fire, Balance.get().tent().fireBlocks())).isPresent();
    }

    private static boolean near(ServerPlayer player, ServerLevel level, int blocks) {
        return player.level() == level && player.distanceToSqr(caravan.getX() + 0.5, player.getY(), caravan.getZ() + 0.5) <= (double) blocks * blocks;
    }

    static void begin(ServerLevel level, long now, int seconds) {
        from = now;
        target = Math.floorDiv(now, DAY) * DAY + ticks(Balance.get().clock().evening());
        mode = now >= target ? Mode.EVENING : Mode.NIGHTFALL;
        pace = (target - from) / (double) (seconds * TICKS_PER_SECOND);
        caravan = null;
        rested.clear();
        speed(level);
    }

    private static void speed(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, level.getServer());
        if (mode == Mode.PASS || mode == Mode.NIGHTFALL) {
            level.setDayTimePerTick((float) pace);
            return;
        }
        if (mode == Mode.EVENING) {
            level.setDayTimePerTick(Nights.hard(level.getServer()) ? VANILLA : HOLD);
            return;
        }
        long left = target - level.getDayTime();
        float speed = left <= 0 || target == from ? HOLD : (float) Math.max(HOLD, pace * left / (target - from));
        level.setDayTimePerTick(speed);
    }

    public static long ticks(String clock) {
        String[] parts = clock.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return Math.floorMod(hour - MORNING_HOUR, 24) * HOUR + minute * HOUR / 60;
    }
}
