package io.github.roman16.wayfarer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record Balance(
        Clock clock,
        Compass compass,
        Daylight daylight,
        Difficulty difficulty,
        Fire fire,
        Gate gate,
        Gaze gaze,
        Hunger hunger,
        Leads leads,
        Nights nights,
        Sight sight,
        Sleep sleep,
        Stillness stillness,
        Structures structures,
        Unlocks unlocks) {

    public record Clock(int caravanBlocks, int duskRunSeconds, String evening, String road) {}

    public record Compass(int exactBlocks, int farRoundingBlocks, int refreshTicks, int researchAfterBlocks, int trailBlocks) {}

    public record Daylight(int crumbleSeconds) {}

    public record Difficulty(float perEye) {}

    public record Fire(int circleBlocks, int lineSeconds, int restBlocks, int restSeconds) {}

    public record Gate(int bandSize, int reachBlocks) {}

    public record Gaze(List<Integer> capacitySeconds, int drainFactor, int huntIntervalSeconds, int huntPhantoms) {}

    public record Hunger(float exhaustionFactor, float rowingExhaustionPerBlock, float warmExhaustionFactor) {}

    public record Leads(int campBlocks, int campReachBlocks, int searchRings) {}

    public record Nights(int bloodMoonEvery, int hordeEvery) {}

    public record Sight(int reachBlocks, int shards) {}

    public record Sleep(int playersPercentage) {}

    public record Stillness(int dimmingDays, int hordeDays, int radiusBlocks) {}

    public record Structures(double spreadFactor, List<String> spreadFeatures) {}

    public record Unlocks(int aircraftEyes, int sailingShipEyes) {}

    public static final Balance INSTANCE = load();

    private static Balance load() {
        try (var stream = Balance.class.getResourceAsStream("/wayfarer/balance.toml")) {
            if (stream == null) {
                throw new IllegalStateException("balance.toml is missing from the mod jar");
            }
            Config toml = new TomlParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new Balance(
                    new Clock(
                            toml.getInt("clock.caravan_blocks"),
                            toml.getInt("clock.dusk_run_seconds"),
                            toml.get("clock.evening"),
                            toml.get("clock.road")),
                    new Compass(
                            toml.getInt("compass.exact_blocks"),
                            toml.getInt("compass.far_rounding_blocks"),
                            toml.getInt("compass.refresh_ticks"),
                            toml.getInt("compass.research_after_blocks"),
                            toml.getInt("compass.trail_blocks")),
                    new Daylight(toml.getInt("daylight.crumble_seconds")),
                    new Difficulty(toml.<Number>get("difficulty.per_eye").floatValue()),
                    new Fire(
                            toml.getInt("fire.circle_blocks"),
                            toml.getInt("fire.line_seconds"),
                            toml.getInt("fire.rest_blocks"),
                            toml.getInt("fire.rest_seconds")),
                    new Gate(toml.getInt("gate.band_size"), toml.getInt("gate.reach_blocks")),
                    new Gaze(
                            toml.<List<Number>>get("gaze.capacity_seconds").stream().map(Number::intValue).toList(),
                            toml.getInt("gaze.drain_factor"),
                            toml.getInt("gaze.hunt_interval_seconds"),
                            toml.getInt("gaze.hunt_phantoms")),
                    new Hunger(
                            toml.<Number>get("hunger.exhaustion_factor").floatValue(),
                            toml.<Number>get("hunger.rowing_exhaustion_per_block").floatValue(),
                            toml.<Number>get("hunger.warm_exhaustion_factor").floatValue()),
                    new Leads(toml.getInt("leads.camp_blocks"), toml.getInt("leads.camp_reach_blocks"), toml.getInt("leads.search_rings")),
                    new Nights(toml.getInt("nights.blood_moon_every"), toml.getInt("nights.horde_every")),
                    new Sight(toml.getInt("sight.reach_blocks"), toml.getInt("sight.shards")),
                    new Sleep(toml.getInt("sleep.players_percentage")),
                    new Stillness(toml.getInt("stillness.dimming_days"), toml.getInt("stillness.horde_days"), toml.getInt("stillness.radius_blocks")),
                    new Structures(toml.<Number>get("structures.spread_factor").doubleValue(), toml.<List<String>>get("structures.spread_features")),
                    new Unlocks(toml.getInt("unlocks.aircraft_eyes"), toml.getInt("unlocks.sailing_ship_eyes")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
