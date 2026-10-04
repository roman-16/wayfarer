package io.github.roman16.wayfarer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Balance(
        Clock clock,
        Compass compass,
        Daylight daylight,
        Difficulty difficulty,
        Dragon dragon,
        Fire fire,
        Flare flare,
        Gate gate,
        Gaze gaze,
        Hunger hunger,
        Nights nights,
        Ore ore,
        Sea sea,
        Shine shine,
        Sight sight,
        Sleep sleep,
        Stillness stillness,
        Structures structures,
        Tent tent,
        ToolGate toolGate,
        Unlocks unlocks,
        Waystones waystones) {

    public record Clock(int afternoonSeconds, int caravanBlocks, int duskRunSeconds, String evening, String road, String runFrom) {}

    public record Compass(int exactBlocks, int farRoundingBlocks, int refreshTicks, int researchAfterBlocks, int trailBlocks) {}

    public record Daylight(int crumbleSeconds) {}

    public record Difficulty(float perEye) {}

    public record Dragon(int blindAimBlocks, int healthBase, int healthPerPlayer, int healthPerShard, int sightShards) {}

    public record Fire(int circleBlocks, int lineSeconds, int restBlocks, int restSeconds) {}

    public record Flare(int charges, int reachBlocks) {}

    public record Gate(int reachBlocks) {}

    public record Gaze(List<Integer> capacitySeconds, int drainFactor, int huntIntervalSeconds, int huntPhantoms) {}

    public record Hunger(float exhaustionFactor, float rowingExhaustionPerBlock, float warmExhaustionFactor) {}

    public record Nights(int bloodMoonEvery, int hordeEvery) {}

    public record Sea(int beastOneIn, int beastOneInLeast, int checkSeconds, int openWaterBlocks, int ramDamage, int ramSeconds, int shinePerStep) {}

    public record Shine(List<Integer> act, double noticeBlocksPerShine, int perShard, int remedy, int spawnEverySeconds, int spawnLimit, int spawnShine) {}

    public record Sight(int reachBlocks, int shards) {}

    public record Ore(Map<String, Double> shares) {}

    public record Sleep(int playersPercentage) {}

    public record Stillness(int dimmingDays, int hordeDays, int radiusBlocks) {}

    public record Structures(double spreadFactor, List<String> spreadFeatures) {}

    public record Tent(int fireBlocks) {}

    public record ToolGate(int slowdown) {}

    public record Unlocks(int aircraftEyes, int sailingShipEyes) {}

    public record Waystones(int blocksPerFood) {}

    public static final Balance INSTANCE = load();

    private static Balance load() {
        try (var stream = Balance.class.getResourceAsStream("/wayfarer/balance.toml")) {
            if (stream == null) {
                throw new IllegalStateException("balance.toml is missing from the mod jar");
            }
            Config toml = new TomlParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new Balance(
                    new Clock(
                            toml.getInt("clock.afternoon_seconds"),
                            toml.getInt("clock.caravan_blocks"),
                            toml.getInt("clock.dusk_run_seconds"),
                            toml.get("clock.evening"),
                            toml.get("clock.road"),
                            toml.get("clock.run_from")),
                    new Compass(
                            toml.getInt("compass.exact_blocks"),
                            toml.getInt("compass.far_rounding_blocks"),
                            toml.getInt("compass.refresh_ticks"),
                            toml.getInt("compass.research_after_blocks"),
                            toml.getInt("compass.trail_blocks")),
                    new Daylight(toml.getInt("daylight.crumble_seconds")),
                    new Difficulty(toml.<Number>get("difficulty.per_eye").floatValue()),
                    new Dragon(
                            toml.getInt("dragon.blind_aim_blocks"),
                            toml.getInt("dragon.health_base"),
                            toml.getInt("dragon.health_per_player"),
                            toml.getInt("dragon.health_per_shard"),
                            toml.getInt("dragon.sight_shards")),
                    new Fire(
                            toml.getInt("fire.circle_blocks"),
                            toml.getInt("fire.line_seconds"),
                            toml.getInt("fire.rest_blocks"),
                            toml.getInt("fire.rest_seconds")),
                    new Flare(toml.getInt("flare.charges"), toml.getInt("flare.reach_blocks")),
                    new Gate(toml.getInt("gate.reach_blocks")),
                    new Gaze(
                            toml.<List<Number>>get("gaze.capacity_seconds").stream().map(Number::intValue).toList(),
                            toml.getInt("gaze.drain_factor"),
                            toml.getInt("gaze.hunt_interval_seconds"),
                            toml.getInt("gaze.hunt_phantoms")),
                    new Hunger(
                            toml.<Number>get("hunger.exhaustion_factor").floatValue(),
                            toml.<Number>get("hunger.rowing_exhaustion_per_block").floatValue(),
                            toml.<Number>get("hunger.warm_exhaustion_factor").floatValue()),
                    new Nights(toml.getInt("nights.blood_moon_every"), toml.getInt("nights.horde_every")),
                    new Ore(toml.<Config>get("ore").entrySet().stream()
                            .collect(Collectors.toUnmodifiableMap(UnmodifiableConfig.Entry::getKey, entry -> entry.<Number>getValue().doubleValue()))),
                    new Sea(
                            toml.getInt("sea.beast_one_in"),
                            toml.getInt("sea.beast_one_in_least"),
                            toml.getInt("sea.check_seconds"),
                            toml.getInt("sea.open_water_blocks"),
                            toml.getInt("sea.ram_damage"),
                            toml.getInt("sea.ram_seconds"),
                            toml.getInt("sea.shine_per_step")),
                    new Shine(
                            toml.<List<Number>>get("shine.act").stream().map(Number::intValue).toList(),
                            toml.<Number>get("shine.notice_blocks_per_shine").doubleValue(),
                            toml.getInt("shine.per_shard"),
                            toml.getInt("shine.remedy"),
                            toml.getInt("shine.spawn_every_seconds"),
                            toml.getInt("shine.spawn_limit"),
                            toml.getInt("shine.spawn_shine")),
                    new Sight(toml.getInt("sight.reach_blocks"), toml.getInt("sight.shards")),
                    new Sleep(toml.getInt("sleep.players_percentage")),
                    new Stillness(toml.getInt("stillness.dimming_days"), toml.getInt("stillness.horde_days"), toml.getInt("stillness.radius_blocks")),
                    new Structures(toml.<Number>get("structures.spread_factor").doubleValue(), toml.<List<String>>get("structures.spread_features")),
                    new Tent(toml.getInt("tent.fire_blocks")),
                    new ToolGate(toml.getInt("tool_gate.slowdown")),
                    new Unlocks(toml.getInt("unlocks.aircraft_eyes"), toml.getInt("unlocks.sailing_ship_eyes")),
                    new Waystones(toml.getInt("waystones.blocks_per_food")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
