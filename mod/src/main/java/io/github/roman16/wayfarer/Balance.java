package io.github.roman16.wayfarer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record Balance(
        Compass compass,
        Difficulty difficulty,
        Gaze gaze,
        Hunger hunger,
        Nights nights,
        Sleep sleep,
        Unlocks unlocks) {

    public record Compass(int exactBlocks, int farRoundingBlocks, int refreshTicks, int researchAfterBlocks, int trailBlocks) {}

    public record Difficulty(float perEye) {}

    public record Gaze(List<Integer> capacitySeconds, int drainFactor, int huntIntervalSeconds, int huntPhantoms) {}

    public record Hunger(float exhaustionFactor) {}

    public record Nights(int bloodMoonEvery, int hordeEvery) {}

    public record Sleep(int playersPercentage) {}

    public record Unlocks(int aircraftEyes, int sailingShipEyes) {}

    public static final Balance INSTANCE = load();

    private static Balance load() {
        try (var stream = Balance.class.getResourceAsStream("/wayfarer/balance.toml")) {
            if (stream == null) {
                throw new IllegalStateException("balance.toml is missing from the mod jar");
            }
            Config toml = new TomlParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new Balance(
                    new Compass(
                            toml.getInt("compass.exact_blocks"),
                            toml.getInt("compass.far_rounding_blocks"),
                            toml.getInt("compass.refresh_ticks"),
                            toml.getInt("compass.research_after_blocks"),
                            toml.getInt("compass.trail_blocks")),
                    new Difficulty(toml.<Number>get("difficulty.per_eye").floatValue()),
                    new Gaze(
                            toml.<List<Number>>get("gaze.capacity_seconds").stream().map(Number::intValue).toList(),
                            toml.getInt("gaze.drain_factor"),
                            toml.getInt("gaze.hunt_interval_seconds"),
                            toml.getInt("gaze.hunt_phantoms")),
                    new Hunger(toml.<Number>get("hunger.exhaustion_factor").floatValue()),
                    new Nights(toml.getInt("nights.blood_moon_every"), toml.getInt("nights.horde_every")),
                    new Sleep(toml.getInt("sleep.players_percentage")),
                    new Unlocks(toml.getInt("unlocks.aircraft_eyes"), toml.getInt("unlocks.sailing_ship_eyes")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
