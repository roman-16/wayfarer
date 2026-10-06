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
        Diggers diggers,
        Dragon dragon,
        Fire fire,
        FirstCamp firstCamp,
        Flare flare,
        Gate gate,
        Gaze gaze,
        Hold hold,
        Hunger hunger,
        Join join,
        Ore ore,
        Prologue prologue,
        Scenes scenes,
        Sea sea,
        Sense sense,
        Shine shine,
        Sight sight,
        Sleep sleep,
        Spawns spawns,
        Stillness stillness,
        Story story,
        Structures structures,
        Talk talk,
        Tent tent,
        Tollbridge tollbridge,
        ToolGate toolGate,
        Unlocks unlocks,
        Waystones waystones) {

    public record Clock(int afternoonSeconds, int caravanBlocks, int dawnSeconds, String dusk, int duskRunSeconds, String evening, String night, String road,
            String runFrom) {}

    public record Compass(int exactBlocks, int refreshTicks, int trailBlocks) {}

    public record Daylight(int crumbleSeconds) {}

    public record Diggers(int aheadBlocks) {}

    public record Dragon(int blindAimBlocks, int healthBase, int healthPerPlayer, int healthPerShard, int sightShards) {}

    public record Fire(int circleBlocks, int lineSeconds, int restBlocks) {}

    public record FirstCamp(int aheadBlocks, int lakeBlocks) {}

    public record Flare(int charges, int reachBlocks) {}

    public record Gate(int reachBlocks) {}

    public record Gaze(List<Integer> capacitySeconds, int drainFactor, int huntIntervalSeconds, int huntPhantoms) {}

    public record Hold(int firstWave, int keepHealth, int waveGrowth, int waveSeconds, int waves) {}

    public record Hunger(float exhaustionFactor, float rowingExhaustionPerBlock, float warmExhaustionFactor) {}

    public record Join(int awayBlocks) {}


    public record Prologue(int awayBlocks) {}

    public record Sea(int beastOneIn, int beastOneInLeast, int checkSeconds, int openWaterBlocks, int ramDamage, int ramSeconds, int shinePerStep) {}

    public record Scenes(int askBlocks, int everySeconds, int farthestBlocks, int goneBlocks, int groupBlocks, int jitterSeconds, int nearestBlocks,
            int voiceGapSeconds, int wolvesSeconds) {}

    public record Sense(int glowSeconds, int reachBlocks) {}

    public record Shine(List<Integer> act, double noticeBlocksPerShine, int perShard, int spawnEverySeconds, int spawnLimit, int spawnShine) {}

    public record Sight(int reachBlocks, int shards) {}

    public record Ore(Map<String, Double> shares) {}

    public record Sleep(int playersPercentage) {}

    public record Spawns(int creatureCap) {}

    public record Stillness(int dimmingDays, int hordeDays, int radiusBlocks) {}

    public record Story(int arriveBlocks, int nearBlocks) {}

    public record Structures(double spreadFactor, List<String> spreadFeatures) {}

    public record Talk(int charactersPerSecond, int leastSeconds, int reachBlocks, int startBlocks) {}

    public record Tent(int fireBlocks) {}

    public record Tollbridge(int aheadBlocks, int clearBlocks, int leastBuildings, int radiusBlocks, int waitSeconds) {}

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
                            toml.getInt("clock.dawn_seconds"),
                            toml.get("clock.dusk"),
                            toml.getInt("clock.dusk_run_seconds"),
                            toml.get("clock.evening"),
                            toml.get("clock.night"),
                            toml.get("clock.road"),
                            toml.get("clock.run_from")),
                    new Compass(
                            toml.getInt("compass.exact_blocks"),
                            toml.getInt("compass.refresh_ticks"),
                            toml.getInt("compass.trail_blocks")),
                    new Daylight(toml.getInt("daylight.crumble_seconds")),
                    new Diggers(toml.getInt("diggers.ahead_blocks")),
                    new Dragon(
                            toml.getInt("dragon.blind_aim_blocks"),
                            toml.getInt("dragon.health_base"),
                            toml.getInt("dragon.health_per_player"),
                            toml.getInt("dragon.health_per_shard"),
                            toml.getInt("dragon.sight_shards")),
                    new Fire(
                            toml.getInt("fire.circle_blocks"),
                            toml.getInt("fire.line_seconds"),
                            toml.getInt("fire.rest_blocks")),
                    new FirstCamp(toml.getInt("first_camp.ahead_blocks"), toml.getInt("first_camp.lake_blocks")),
                    new Flare(toml.getInt("flare.charges"), toml.getInt("flare.reach_blocks")),
                    new Gate(toml.getInt("gate.reach_blocks")),
                    new Gaze(
                            toml.<List<Number>>get("gaze.capacity_seconds").stream().map(Number::intValue).toList(),
                            toml.getInt("gaze.drain_factor"),
                            toml.getInt("gaze.hunt_interval_seconds"),
                            toml.getInt("gaze.hunt_phantoms")),
                    new Hold(
                            toml.getInt("hold.first_wave"),
                            toml.getInt("hold.keep_health"),
                            toml.getInt("hold.wave_growth"),
                            toml.getInt("hold.wave_seconds"),
                            toml.getInt("hold.waves")),
                    new Hunger(
                            toml.<Number>get("hunger.exhaustion_factor").floatValue(),
                            toml.<Number>get("hunger.rowing_exhaustion_per_block").floatValue(),
                            toml.<Number>get("hunger.warm_exhaustion_factor").floatValue()),
                    new Join(toml.getInt("join.away_blocks")),
                    new Ore(toml.<Config>get("ore").entrySet().stream()
                            .collect(Collectors.toUnmodifiableMap(UnmodifiableConfig.Entry::getKey, entry -> entry.<Number>getValue().doubleValue()))),
                    new Prologue(toml.getInt("prologue.away_blocks")),
                    new Scenes(toml.getInt("scenes.ask_blocks"), toml.getInt("scenes.every_seconds"), toml.getInt("scenes.farthest_blocks"),
                            toml.getInt("scenes.gone_blocks"), toml.getInt("scenes.group_blocks"), toml.getInt("scenes.jitter_seconds"),
                            toml.getInt("scenes.nearest_blocks"), toml.getInt("scenes.voice_gap_seconds"), toml.getInt("scenes.wolves_seconds")),
                    new Sea(
                            toml.getInt("sea.beast_one_in"),
                            toml.getInt("sea.beast_one_in_least"),
                            toml.getInt("sea.check_seconds"),
                            toml.getInt("sea.open_water_blocks"),
                            toml.getInt("sea.ram_damage"),
                            toml.getInt("sea.ram_seconds"),
                            toml.getInt("sea.shine_per_step")),
                    new Sense(toml.getInt("sense.glow_seconds"), toml.getInt("sense.reach_blocks")),
                    new Shine(
                            toml.<List<Number>>get("shine.act").stream().map(Number::intValue).toList(),
                            toml.<Number>get("shine.notice_blocks_per_shine").doubleValue(),
                            toml.getInt("shine.per_shard"),
                            toml.getInt("shine.spawn_every_seconds"),
                            toml.getInt("shine.spawn_limit"),
                            toml.getInt("shine.spawn_shine")),
                    new Sight(toml.getInt("sight.reach_blocks"), toml.getInt("sight.shards")),
                    new Sleep(toml.getInt("sleep.players_percentage")),
                    new Spawns(toml.getInt("spawns.creature_cap")),
                    new Stillness(toml.getInt("stillness.dimming_days"), toml.getInt("stillness.horde_days"), toml.getInt("stillness.radius_blocks")),
                    new Story(toml.getInt("story.arrive_blocks"), toml.getInt("story.near_blocks")),
                    new Structures(toml.<Number>get("structures.spread_factor").doubleValue(), toml.<List<String>>get("structures.spread_features")),
                    new Talk(
                            toml.getInt("talk.characters_per_second"),
                            toml.getInt("talk.least_seconds"),
                            toml.getInt("talk.reach_blocks"),
                            toml.getInt("talk.start_blocks")),
                    new Tent(toml.getInt("tent.fire_blocks")),
                    new Tollbridge(toml.getInt("tollbridge.ahead_blocks"), toml.getInt("tollbridge.clear_blocks"), toml.getInt("tollbridge.least_buildings"),
                            toml.getInt("tollbridge.radius_blocks"),
                            toml.getInt("tollbridge.wait_seconds")),
                    new ToolGate(toml.getInt("tool_gate.slowdown")),
                    new Unlocks(toml.getInt("unlocks.aircraft_eyes"), toml.getInt("unlocks.sailing_ship_eyes")),
                    new Waystones(toml.getInt("waystones.blocks_per_food")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
