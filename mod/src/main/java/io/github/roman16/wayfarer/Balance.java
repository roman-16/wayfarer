package io.github.roman16.wayfarer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Balance(
        After after,
        Chase chase,
        Clock clock,
        Compass compass,
        Crossing crossing,
        Daylight daylight,
        Diggers diggers,
        Dragon dragon,
        Duel duel,
        Errands errands,
        Escort escort,
        FarSight farSight,
        Fire fire,
        FirstCamp firstCamp,
        FirstVillage firstVillage,
        Flare flare,
        Ford ford,
        Forge forge,
        Frost frost,
        Gate gate,
        Gaze gaze,
        Graphics graphics,
        Halls halls,
        Hold hold,
        Hunger hunger,
        Join join,
        Loot loot,
        Marsh marsh,
        Memory memory,
        Ore ore,
        Prologue prologue,
        ReadingHouse readingHouse,
        Scenes scenes,
        Sea sea,
        Sense sense,
        Shine shine,
        Shipyard shipyard,
        Sight sight,
        Sleep sleep,
        Smoke smoke,
        Spawns spawns,
        Stare stare,
        Stillness stillness,
        Story story,
        Strongholds strongholds,
        Structures structures,
        Talk talk,
        Tent tent,
        Tide tide,
        Tollbridge tollbridge,
        ToolGate toolGate,
        Voyage voyage,
        Waystones waystones,
        Weather weather,
        WendCamp wendCamp,
        World world,
        Worm worm) {

    public record Clock(int afternoonSeconds, int caravanBlocks, int dawnSeconds, String dusk, int duskRunSeconds, String evening, String night, String road,
            String runFrom) {}

    public record After(int homeBlocks) {}

    public record Chase(int caughtBlocks, int farBlocks, int fastSixteenths, int nearBlocks, int slowSixteenths, int startBlocks) {}

    public record Compass(int exactBlocks, int refreshTicks, int trailBlocks) {}

    public record Daylight(int crumbleSeconds) {}

    public record Diggers(int aheadBlocks) {}

    public record Dragon(int blindAimBlocks, int healthBase, int healthPerPlayer, int healthPerShard, int sightShards) {}

    public record Duel(int healthBase, int healthPerPlayer) {}

    public record Errands(int aheadBlocks, int airshipCoal, int searchChunks, int snowReachBlocks) {}

    public record Escort(int followBlocks, int leashBlocks) {}

    public record Fire(int circleBlocks, int lineSeconds, int restBlocks) {}

    public record FirstCamp(int aheadBlocks, int lakeBlocks) {}

    public record FirstVillage(int aheadBlocks, int searchBlocks) {}

    public record Flare(int charges, int reachBlocks) {}

    public record Gate(int reachBlocks) {}

    public record Gaze(List<Integer> capacitySeconds, int drainFactor, int huntIntervalSeconds, int huntPhantoms) {}

    public record Graphics(List<Integer> farViewChunks, List<String> farViewDetail, List<String> farViewThreads, List<String> heldLight, List<String> profiles,
                           List<Integer> renderChunks, List<Integer> simulationChunks) {}

    public record Hold(int blowTicks, int calmSeconds, int doorStrength, int firstWave, int keepHealth, int waveGrowth, int waveSeconds, int waves) {}

    public record Hunger(float exhaustionFactor, float rowingExhaustionPerBlock, float warmExhaustionFactor) {}

    public record Join(int awayBlocks) {}

    public record Loot(double keptShare) {}


    public record Prologue(int awayBlocks, int campSearchBlocks, int campStepBlocks) {}

    public record Sea(int beastHealth, int beastOneIn, int beastOneInLeast, int checkSeconds, int flinchSeconds, int openWaterBlocks, int ramDamage, int ramSeconds,
            int shinePerStep) {}

    public record Scenes(int askBlocks, int everySeconds, int farthestBlocks, int goneBlocks, int groupBlocks, int jitterSeconds, int nearestBlocks,
            int voiceGapSeconds, int wolvesSeconds) {}

    public record Sense(int glowSeconds, int reachBlocks) {}

    public record Halls(int actThreeFromBlocks, int actThreeLegBlocks, int actTwoLegBlocks, int nearestBlocks, int reachBlocks) {}

    public record FarSight(int markBlocks, int marks, int zoomSixteenths) {}

    public record Shipyard(int wreckBlocks) {}

    public record WendCamp(int aheadBlocks) {}

    public record Worm(int biteBlocks, int biteDamage, int biteSeconds, int downSeconds, int fleeBlocks, int groundBlocks, int health, int healthPerPlayer, int lairBlocks,
            int leaveSeconds, double scale, int upSeconds) {}

    public record Stare(int coneDegrees, int fleeBlocks, int fleeSeconds, int reachBlocks) {}

    public record Tide(int stoneBlocks) {}

    public record Voyage(int besideBlocks, int calmSeconds, int firstWave, int landfallBlocks, int leftSeconds, int listingDamage, int reachBlocks, int riseBlocks,
            int waveGrowth, int waveSeconds, int waves) {}

    public record Shine(List<Integer> act, int bellFound, int foundShine, int glowLight, double noticeBlocksPerShine, int perShard, int spawnEverySeconds, int spawnLimit,
            int spawnShine) {}

    public record Sight(int reachBlocks) {}

    public record Forge(int veinLumps) {}

    public record Frost(int aheadBlocks, int darkSightSixteenths, int driftEveryBlocks, int hallBlocks, int houseBlocks, int lakeBlocks, int leaveBlocks,
            int mineBlocks, int mineFloor, int mineWallBlocks, int searchBlocks, int shoreBlocks, int sideBlocks, int snowRadiusBlocks, int stairBlocks,
            int stoneBlocks, int trailBlocks, int trailPastBlocks) {}

    public record Crossing(int firstWave, int retreatSeconds, int waveGrowth, int waveSeconds, int waves) {}

    public record Ford(int aheadBlocks, int creekReachBlocks, int noticeBlocks, int outpostBlocks, int searchBlocks, int sideBlocks, int widestRiverBlocks,
            int widthBlocks) {}

    public record Marsh(int aheadBlocks, int bogBlocks, int bogRadiusBlocks, int cellarBlocks, int hallBlocks, int nearBlocks, int noticeBlocks, int searchBlocks,
            int shunnedSeconds, int sideBlocks) {}

    public record Memory(int arrivalBlocks, int fadeTicks, int fireBlocks, int togetherBlocks) {}

    public record Ore(Map<String, Double> shares) {}

    public record ReadingHouse(int aheadBlocks, int searchBlocks) {}

    public record Sleep(int playersPercentage) {}

    public record Smoke(int columnBlocks, int puffTicks, int widthBlocks) {}

    public record Spawns(int creatureCap) {}

    public record Stillness(int dimmingDays, int hordeDays, int radiusBlocks) {}

    public record Story(int arriveBlocks, int nearBlocks, int restSeconds, int visitBlocks) {}

    public record Strongholds(int count, int jitterBlocks, int landSearchBlocks, int radiusBlocks) {}

    public record Structures(double spreadFactor, List<String> spreadFeatures) {}

    public record Talk(int againSeconds, int charactersPerSecond, int leastSeconds, int pauseSeconds, int reachBlocks, int startBlocks, int waitSeconds) {}

    public record Tent(int fireBlocks, int reminderBlocks) {}

    public record Tollbridge(int aheadBlocks, int ashEveryTicks, int ashFlakes, int ashReachBlocks, int clearBlocks, int leastBuildings, int radiusBlocks,
                             int waitSeconds) {}

    public record ToolGate(int slowdown) {}

    public record Waystones(int blocksPerFood) {}

    public record Weather(int daysBetweenRain, int rainMostSeconds) {}

    public record World(int coastBlocks, double highest, int homeBlocks, int lastSeaPastRingBlocks, double lastSeaSink, double lift, double outerSink, int seaBlocks,
            double seaFloor, double seaSink, int shoreBlocks) {}

    private static final Bundled<Balance> BALANCE = Bundled.of(() -> Bundled.read("/wayfarer/balance.toml", reader -> load(new TomlParser().parse(reader))));

    public static Balance get() {
        return BALANCE.get();
    }

    private static Balance load(Config toml) {
        return new Balance(
                new After(toml.getInt("after.home_blocks")),
                new Chase(toml.getInt("chase.caught_blocks"), toml.getInt("chase.far_blocks"), toml.getInt("chase.fast_sixteenths"), toml.getInt("chase.near_blocks"),
                        toml.getInt("chase.slow_sixteenths"), toml.getInt("chase.start_blocks")),
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
                new Crossing(toml.getInt("crossing.first_wave"), toml.getInt("crossing.retreat_seconds"), toml.getInt("crossing.wave_growth"),
                        toml.getInt("crossing.wave_seconds"), toml.getInt("crossing.waves")),
                new Daylight(toml.getInt("daylight.crumble_seconds")),
                new Diggers(toml.getInt("diggers.ahead_blocks")),
                new Dragon(
                        toml.getInt("dragon.blind_aim_blocks"),
                        toml.getInt("dragon.health_base"),
                        toml.getInt("dragon.health_per_player"),
                        toml.getInt("dragon.health_per_shard"),
                        toml.getInt("dragon.sight_shards")),
                new Duel(toml.getInt("duel.health_base"), toml.getInt("duel.health_per_player")),
                new Errands(toml.getInt("errands.ahead_blocks"), toml.getInt("errands.airship_coal"), toml.getInt("errands.search_chunks"), toml.getInt("errands.snow_reach_blocks")),
                new Escort(toml.getInt("escort.follow_blocks"), toml.getInt("escort.leash_blocks")),
                new FarSight(toml.getInt("far_sight.mark_blocks"), toml.getInt("far_sight.marks"), toml.getInt("far_sight.zoom_sixteenths")),
                new Fire(
                        toml.getInt("fire.circle_blocks"),
                        toml.getInt("fire.line_seconds"),
                        toml.getInt("fire.rest_blocks")),
                new FirstCamp(toml.getInt("first_camp.ahead_blocks"), toml.getInt("first_camp.lake_blocks")),
                new FirstVillage(toml.getInt("first_village.ahead_blocks"), toml.getInt("first_village.search_blocks")),
                new Flare(toml.getInt("flare.charges"), toml.getInt("flare.reach_blocks")),
                new Ford(toml.getInt("ford.ahead_blocks"), toml.getInt("ford.creek_reach_blocks"), toml.getInt("ford.notice_blocks"),
                        toml.getInt("ford.outpost_blocks"), toml.getInt("ford.search_blocks"), toml.getInt("ford.side_blocks"),
                        toml.getInt("ford.widest_river_blocks"), toml.getInt("ford.width_blocks")),
                new Forge(toml.getInt("forge.vein_lumps")),
                new Frost(toml.getInt("frost.ahead_blocks"), toml.getInt("frost.dark_sight_sixteenths"), toml.getInt("frost.drift_every_blocks"),
                        toml.getInt("frost.hall_blocks"), toml.getInt("frost.house_blocks"), toml.getInt("frost.lake_blocks"), toml.getInt("frost.leave_blocks"),
                        toml.getInt("frost.mine_blocks"), toml.getInt("frost.mine_floor"), toml.getInt("frost.mine_wall_blocks"), toml.getInt("frost.search_blocks"), toml.getInt("frost.shore_blocks"), toml.getInt("frost.side_blocks"),
                        toml.getInt("frost.snow_radius_blocks"), toml.getInt("frost.stair_blocks"), toml.getInt("frost.stone_blocks"), toml.getInt("frost.trail_blocks"),
                        toml.getInt("frost.trail_past_blocks")),
                new Gate(toml.getInt("gate.reach_blocks")),
                new Gaze(
                        toml.<List<Number>>get("gaze.capacity_seconds").stream().map(Number::intValue).toList(),
                        toml.getInt("gaze.drain_factor"),
                        toml.getInt("gaze.hunt_interval_seconds"),
                        toml.getInt("gaze.hunt_phantoms")),
                new Graphics(
                        toml.<List<Number>>get("graphics.far_view_chunks").stream().map(Number::intValue).toList(),
                        toml.get("graphics.far_view_detail"),
                        toml.get("graphics.far_view_threads"),
                        toml.get("graphics.held_light"),
                        toml.get("graphics.profiles"),
                        toml.<List<Number>>get("graphics.render_chunks").stream().map(Number::intValue).toList(),
                        toml.<List<Number>>get("graphics.simulation_chunks").stream().map(Number::intValue).toList()),
                new Halls(toml.getInt("halls.act_three_from_blocks"), toml.getInt("halls.act_three_leg_blocks"), toml.getInt("halls.act_two_leg_blocks"),
                        toml.getInt("halls.nearest_blocks"), toml.getInt("halls.reach_blocks")),
                new Hold(
                        toml.getInt("hold.blow_ticks"),
                        toml.getInt("hold.calm_seconds"),
                        toml.getInt("hold.door_strength"),
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
                new Loot(toml.<Number>get("loot.kept_share").doubleValue()),
                new Marsh(toml.getInt("marsh.ahead_blocks"), toml.getInt("marsh.bog_blocks"), toml.getInt("marsh.bog_radius_blocks"),
                        toml.getInt("marsh.cellar_blocks"), toml.getInt("marsh.hall_blocks"), toml.getInt("marsh.near_blocks"), toml.getInt("marsh.notice_blocks"),
                        toml.getInt("marsh.search_blocks"), toml.getInt("marsh.shunned_seconds"), toml.getInt("marsh.side_blocks")),
                new Memory(toml.getInt("memory.arrival_blocks"), toml.getInt("memory.fade_ticks"), toml.getInt("memory.fire_blocks"),
                        toml.getInt("memory.together_blocks")),
                new Ore(toml.<Config>get("ore").entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(UnmodifiableConfig.Entry::getKey, entry -> entry.<Number>getValue().doubleValue()))),
                new Prologue(toml.getInt("prologue.away_blocks"), toml.getInt("prologue.camp_search_blocks"), toml.getInt("prologue.camp_step_blocks")),
                new ReadingHouse(toml.getInt("reading_house.ahead_blocks"), toml.getInt("reading_house.search_blocks")),
                new Scenes(toml.getInt("scenes.ask_blocks"), toml.getInt("scenes.every_seconds"), toml.getInt("scenes.farthest_blocks"),
                        toml.getInt("scenes.gone_blocks"), toml.getInt("scenes.group_blocks"), toml.getInt("scenes.jitter_seconds"),
                        toml.getInt("scenes.nearest_blocks"), toml.getInt("scenes.voice_gap_seconds"), toml.getInt("scenes.wolves_seconds")),
                new Sea(
                        toml.getInt("sea.beast_health"),
                        toml.getInt("sea.beast_one_in"),
                        toml.getInt("sea.beast_one_in_least"),
                        toml.getInt("sea.check_seconds"),
                        toml.getInt("sea.flinch_seconds"),
                        toml.getInt("sea.open_water_blocks"),
                        toml.getInt("sea.ram_damage"),
                        toml.getInt("sea.ram_seconds"),
                        toml.getInt("sea.shine_per_step")),
                new Sense(toml.getInt("sense.glow_seconds"), toml.getInt("sense.reach_blocks")),
                new Shine(
                        toml.<List<Number>>get("shine.act").stream().map(Number::intValue).toList(),
                        toml.getInt("shine.bell_found"),
                        toml.getInt("shine.found_shine"),
                        toml.getInt("shine.glow_light"),
                        toml.<Number>get("shine.notice_blocks_per_shine").doubleValue(),
                        toml.getInt("shine.per_shard"),
                        toml.getInt("shine.spawn_every_seconds"),
                        toml.getInt("shine.spawn_limit"),
                        toml.getInt("shine.spawn_shine")),
                new Shipyard(toml.getInt("shipyard.wreck_blocks")),
                new Sight(toml.getInt("sight.reach_blocks")),
                new Sleep(toml.getInt("sleep.players_percentage")),
                new Smoke(toml.getInt("smoke.column_blocks"), toml.getInt("smoke.puff_ticks"), toml.getInt("smoke.width_blocks")),
                new Spawns(toml.getInt("spawns.creature_cap")),
                new Stare(toml.getInt("stare.cone_degrees"), toml.getInt("stare.flee_blocks"), toml.getInt("stare.flee_seconds"), toml.getInt("stare.reach_blocks")),
                new Stillness(toml.getInt("stillness.dimming_days"), toml.getInt("stillness.horde_days"), toml.getInt("stillness.radius_blocks")),
                new Story(toml.getInt("story.arrive_blocks"), toml.getInt("story.near_blocks"), toml.getInt("story.rest_seconds"),
                        toml.getInt("story.visit_blocks")),
                new Strongholds(toml.getInt("strongholds.count"), toml.getInt("strongholds.jitter_blocks"), toml.getInt("strongholds.land_search_blocks"),
                        toml.getInt("strongholds.radius_blocks")),
                new Structures(toml.<Number>get("structures.spread_factor").doubleValue(), toml.<List<String>>get("structures.spread_features")),
                new Talk(
                        toml.getInt("talk.again_seconds"),
                        toml.getInt("talk.characters_per_second"),
                        toml.getInt("talk.least_seconds"),
                        toml.getInt("talk.pause_seconds"),
                        toml.getInt("talk.reach_blocks"),
                        toml.getInt("talk.start_blocks"),
                        toml.getInt("talk.wait_seconds")),
                new Tent(toml.getInt("tent.fire_blocks"), toml.getInt("tent.reminder_blocks")),
                new Tide(toml.getInt("tide.stone_blocks")),
                new Tollbridge(toml.getInt("tollbridge.ahead_blocks"), toml.getInt("tollbridge.ash_every_ticks"), toml.getInt("tollbridge.ash_flakes"),
                        toml.getInt("tollbridge.ash_reach_blocks"), toml.getInt("tollbridge.clear_blocks"), toml.getInt("tollbridge.least_buildings"),
                        toml.getInt("tollbridge.radius_blocks"),
                        toml.getInt("tollbridge.wait_seconds")),
                new ToolGate(toml.getInt("tool_gate.slowdown")),
                new Voyage(
                        toml.getInt("voyage.beside_blocks"),
                        toml.getInt("voyage.calm_seconds"),
                        toml.getInt("voyage.first_wave"),
                        toml.getInt("voyage.landfall_blocks"),
                        toml.getInt("voyage.left_seconds"),
                        toml.getInt("voyage.listing_damage"),
                        toml.getInt("voyage.reach_blocks"),
                        toml.getInt("voyage.rise_blocks"),
                        toml.getInt("voyage.wave_growth"),
                        toml.getInt("voyage.wave_seconds"),
                        toml.getInt("voyage.waves")),
                new Waystones(toml.getInt("waystones.blocks_per_food")),
                new Weather(toml.getInt("weather.days_between_rain"), toml.getInt("weather.rain_most_seconds")),
                new WendCamp(toml.getInt("wend_camp.ahead_blocks")),
                new World(
                        toml.getInt("world.coast_blocks"),
                        toml.<Number>get("world.highest").doubleValue(),
                        toml.getInt("world.home_blocks"),
                        toml.getInt("world.last_sea_past_ring_blocks"),
                        toml.<Number>get("world.last_sea_sink").doubleValue(),
                        toml.<Number>get("world.lift").doubleValue(),
                        toml.<Number>get("world.outer_sink").doubleValue(),
                        toml.getInt("world.sea_blocks"),
                        toml.<Number>get("world.sea_floor").doubleValue(),
                        toml.<Number>get("world.sea_sink").doubleValue(),
                        toml.getInt("world.shore_blocks")),
                new Worm(
                        toml.getInt("worm.bite_blocks"),
                        toml.getInt("worm.bite_damage"),
                        toml.getInt("worm.bite_seconds"),
                        toml.getInt("worm.down_seconds"),
                        toml.getInt("worm.flee_blocks"),
                        toml.getInt("worm.ground_blocks"),
                        toml.getInt("worm.health"),
                        toml.getInt("worm.health_per_player"),
                        toml.getInt("worm.lair_blocks"),
                        toml.getInt("worm.leave_seconds"),
                        toml.<Number>get("worm.scale").doubleValue(),
                        toml.getInt("worm.up_seconds")));
    }
}
