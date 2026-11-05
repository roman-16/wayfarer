package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Sense;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

final class Sweep {
    private static final int DIRECTIONS = 16;
    private static final float STEP_DEGREES = 360f / DIRECTIONS;
    private static final int SETTLE_TICKS = 2;
    private static final int READ_TICKS = 32;
    private static final int EXPIRY_TICKS = 2;
    private static final int TICKS_PER_SECOND = 20;
    private static final float CLAMPED_DEGREES = 4;
    private static final int UNSEEN = Integer.MIN_VALUE;
    private static final List<String> POINTS = List.of("south", "south-south-west", "south-west", "west-south-west", "west", "west-north-west", "north-west",
            "north-north-west", "north", "north-north-east", "north-east", "east-north-east", "east", "east-south-east", "south-east", "south-south-east");
    private static final List<String> WORDS = List.of("guttering", "fading", "listening", "glowing", "flaring");

    private Sweep() {}

    static void read(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        KeyMapping sense = Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.getName().equals(Sense.KEY)).findFirst()
                .orElseThrow(() -> new IllegalStateException("ember failed: no key mapping " + Sense.KEY));
        long window = (long) Balance.get().sense().glowSeconds() * TICKS_PER_SECOND;
        int[] heats = new int[DIRECTIONS];
        int[] index = {0};
        long[] turnedAt = {-1};
        long[] pressedAt = {-1};
        CompassPayload[] last = {HudState.compass};
        float[] health = {player.getHealth()};
        String[] found = {null};
        long[] facedAt = {-1};
        Driver.after(() -> {
            if (player.getHealth() < health[0] && !Body.answering()) {
                Driver.stop("ember: stopped: you were hurt, health " + Math.round(player.getHealth()) + " of " + Math.round(player.getMaxHealth()) + ": "
                        + Senses.lastKind());
                return false;
            }
            health[0] = player.getHealth();
            if (Body.busy()) {
                turnedAt[0] = -1;
                Driver.progress();
                return false;
            }
            CompassPayload reading = HudState.compass;
            long now = minecraft.level.getGameTime();
            if (reading.mode() != CompassPayload.Mode.FAR) {
                if (pressedAt[0] < 0) {
                    KeyMapping.click(sense.getKey());
                    pressedAt[0] = now;
                    Driver.progress();
                    return false;
                }
                if (now < pressedAt[0] + READ_TICKS) {
                    return false;
                }
                Driver.note("ember: the compass reads " + reading.mode().name().toLowerCase(Locale.ROOT) + ", so the ember shows no pull; held up for its light");
                return true;
            }
            if (found[0] != null) {
                if (now >= pressedAt[0] + window - READ_TICKS && now >= pressedAt[0] + window + EXPIRY_TICKS) {
                    KeyMapping.click(sense.getKey());
                    pressedAt[0] = now;
                }
                if (reading == last[0] || now < facedAt[0] + SETTLE_TICKS) {
                    last[0] = reading;
                    return false;
                }
                Driver.note("ember: " + found[0]);
                Body.faced();
                return true;
            }
            if (turnedAt[0] < 0) {
                if (pressedAt[0] < 0 || now >= pressedAt[0] + window - READ_TICKS) {
                    if (pressedAt[0] >= 0 && now < pressedAt[0] + window + EXPIRY_TICKS) {
                        return false;
                    }
                    KeyMapping.click(sense.getKey());
                    pressedAt[0] = now;
                }
                player.setYRot(index[0] * STEP_DEGREES);
                player.setXRot(0);
                turnedAt[0] = now;
                last[0] = reading;
                Driver.progress();
                return false;
            }
            if (reading == last[0]) {
                return false;
            }
            last[0] = reading;
            if (now < turnedAt[0] + SETTLE_TICKS) {
                return false;
            }
            boolean clamped = Math.abs(Mth.wrapDegrees(player.getYRot() - index[0] * STEP_DEGREES)) > CLAMPED_DEGREES;
            heats[index[0]] = clamped ? UNSEEN : reading.heat();
            index[0]++;
            turnedAt[0] = -1;
            Driver.progress();
            if (index[0] < DIRECTIONS) {
                return false;
            }
            found[0] = summary(player, heats);
            facedAt[0] = now;
            return false;
        }, "holding up the ember, turning", null);
    }

    private static String summary(LocalPlayer player, int[] heats) {
        int best = Arrays.stream(heats).max().orElse(UNSEEN);
        if (best == UNSEEN) {
            return "nothing could be read from here";
        }
        int[] run = longestRun(heats, best);
        float middle = (run[0] + (run[1] - 1) / 2f) * STEP_DEGREES;
        player.setYRot(middle);
        player.setXRot(0);
        String arcs = IntStream.rangeClosed(-2, 2).map(heat -> -heat).filter(heat -> Arrays.stream(heats).anyMatch(value -> value == heat))
                .mapToObj(heat -> WORDS.get(heat + 2) + " " + arc(heats, heat)).collect(Collectors.joining("; "));
        String unseen = Arrays.stream(heats).anyMatch(value -> value == UNSEEN) ? "; out of view " + arc(heats, UNSEEN) : "";
        return WORDS.get(best + 2) + " most towards " + point(middle) + " (yaw " + Math.round(Mth.wrapDegrees(middle)) + "), facing it now; " + arcs + unseen;
    }

    private static int[] longestRun(int[] heats, int heat) {
        int start = 0;
        int length = 0;
        for (int from = 0; from < DIRECTIONS; from++) {
            int run = 0;
            while (run < DIRECTIONS && heats[(from + run) % DIRECTIONS] == heat) {
                run++;
            }
            if (run > length) {
                start = from;
                length = run;
            }
        }
        return new int[] {start, length};
    }

    private static String arc(int[] heats, int heat) {
        int first = IntStream.range(0, DIRECTIONS).filter(direction -> heats[direction] != heat).findFirst().orElse(-1);
        if (first < 0) {
            return "all round";
        }
        List<String> stretches = new ArrayList<>();
        for (int offset = 1; offset <= DIRECTIONS; offset++) {
            int direction = (first + offset) % DIRECTIONS;
            if (heats[direction] != heat || heats[(direction + DIRECTIONS - 1) % DIRECTIONS] == heat) {
                continue;
            }
            int end = direction;
            while (heats[(end + 1) % DIRECTIONS] == heat) {
                end = (end + 1) % DIRECTIONS;
            }
            stretches.add(end == direction ? POINTS.get(direction) : "from " + POINTS.get(direction) + " to " + POINTS.get(end));
        }
        return String.join(" and ", stretches);
    }

    private static String point(float yaw) {
        return POINTS.get(Math.floorMod(Math.round(yaw / STEP_DEGREES), DIRECTIONS));
    }
}
