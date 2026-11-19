package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.LastSea;
import io.github.roman16.wayfarer.RestEvent;
import io.github.roman16.wayfarer.Sea;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.worm.GreatWorm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Deeds {
    static final String LONG_ROAD = "the_long_road";
    static final String MANY_FIRES = "many_fires";
    static final String EVERY_VILLAGE = "every_village";
    static final String EDGE = "the_edge_of_the_map";
    static final String HELD = "held";
    static final String KNOWN = "known_on_the_road";
    static final String OLD_FRIENDS = "old_friends";
    static final String DICE = "the_dice";
    static final String UNTOUCHED = "untouched";
    static final String HANDS_CLEAN = "hands_clean";
    static final String DEEP_QUIET = "deep_quiet";
    static final String SLOW_WAY = "the_slow_way";
    static final String LIGHT_POCKETS = "light_pockets";
    static final String ALL_TWELVE = "all_twelve";
    static final String NOBODY_LEFT = "nobody_left_behind";
    static final String ALONE = "alone";
    static final List<String> ALL = List.of(LONG_ROAD, MANY_FIRES, EVERY_VILLAGE, EDGE, HELD, KNOWN, OLD_FRIENDS, DICE, UNTOUCHED, HANDS_CLEAN, DEEP_QUIET,
            SLOW_WAY, LIGHT_POCKETS, ALL_TWELVE, NOBODY_LEFT, ALONE);
    private static final String DATA = Wayfarer.MODID + "_deeds";
    private static final String SOUND = "deed";
    private static final String FRAME = "the_doors";
    private static final String HOLLOW = "the_hollow";
    private static final String EASY_NPC = "easy_npc";
    private static final List<String> HERDER = List.of("gift", "warning");
    private static final List<String> ACTS = List.of("1", "2", "3");
    private static final List<TagKey<Structure>> VILLAGES = List.of(TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/village")),
            TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/town")));
    private static final int CHECK_TICKS = 20;
    private static final int WALK_TICKS = 400;
    private static final int CENTIMETRES = 100;
    private static final Map<UUID, City> cities = new HashMap<>();
    private static final Map<UUID, Long> nights = new HashMap<>();
    private static final Set<UUID> struck = new HashSet<>();

    private Deeds() {}

    private static final class City {
        private final StructureStart start;
        private boolean heart;
        private boolean woke;

        private City(StructureStart start) {
            this.start = start;
        }
    }

    private static final class State extends SavedData {
        private final Map<UUID, Map<String, Long>> earned = new LinkedHashMap<>();
        private final Map<String, Long> shared = new LinkedHashMap<>();
        private final Map<UUID, List<Long>> fires = new LinkedHashMap<>();
        private final Map<UUID, Set<Long>> villages = new LinkedHashMap<>();
        private final Map<UUID, Set<Long>> scenes = new LinkedHashMap<>();
        private final Map<UUID, Set<String>> herder = new LinkedHashMap<>();
        private final Map<UUID, Integer> dice = new LinkedHashMap<>();
        private final Map<UUID, Integer> hardNights = new LinkedHashMap<>();
        private final Set<UUID> folded = new LinkedHashSet<>();
        private final Set<String> bloodied = new LinkedHashSet<>();

        static State of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(State::new, State::load, null), DATA);
        }

        private static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            CompoundTag players = tag.getCompound("players");
            for (String key : players.getAllKeys()) {
                UUID player = UUID.fromString(key);
                CompoundTag own = players.getCompound(key);
                CompoundTag deeds = own.getCompound("earned");
                Map<String, Long> earned = new LinkedHashMap<>();
                deeds.getAllKeys().forEach(deed -> earned.put(deed, deeds.getLong(deed)));
                state.earned.put(player, earned);
                List<Long> fires = new ArrayList<>();
                own.getList("fires", Tag.TAG_LONG).forEach(fire -> fires.add(((LongTag) fire).getAsLong()));
                state.fires.put(player, fires);
                Set<Long> villages = new LinkedHashSet<>();
                own.getList("villages", Tag.TAG_LONG).forEach(village -> villages.add(((LongTag) village).getAsLong()));
                state.villages.put(player, villages);
                Set<Long> scenes = new LinkedHashSet<>();
                own.getList("scenes", Tag.TAG_LONG).forEach(scene -> scenes.add(((LongTag) scene).getAsLong()));
                state.scenes.put(player, scenes);
                Set<String> herder = new LinkedHashSet<>();
                own.getList("herder", Tag.TAG_STRING).forEach(variant -> herder.add(variant.getAsString()));
                state.herder.put(player, herder);
                state.dice.put(player, own.getInt("dice"));
                state.hardNights.put(player, own.getInt("hardNights"));
                if (own.getBoolean("folded")) {
                    state.folded.add(player);
                }
            }
            CompoundTag shared = tag.getCompound("shared");
            shared.getAllKeys().forEach(deed -> state.shared.put(deed, shared.getLong(deed)));
            tag.getList("bloodied", Tag.TAG_STRING).forEach(act -> state.bloodied.add(act.getAsString()));
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            Set<UUID> everyone = new LinkedHashSet<>(earned.keySet());
            everyone.addAll(fires.keySet());
            everyone.addAll(villages.keySet());
            everyone.addAll(scenes.keySet());
            everyone.addAll(herder.keySet());
            everyone.addAll(dice.keySet());
            everyone.addAll(hardNights.keySet());
            everyone.addAll(folded);
            CompoundTag players = new CompoundTag();
            for (UUID player : everyone) {
                CompoundTag own = new CompoundTag();
                CompoundTag deeds = new CompoundTag();
                earned.getOrDefault(player, Map.of()).forEach(deeds::putLong);
                own.put("earned", deeds);
                ListTag fireList = new ListTag();
                fires.getOrDefault(player, List.of()).forEach(fire -> fireList.add(LongTag.valueOf(fire)));
                own.put("fires", fireList);
                ListTag villageList = new ListTag();
                villages.getOrDefault(player, Set.of()).forEach(village -> villageList.add(LongTag.valueOf(village)));
                own.put("villages", villageList);
                ListTag sceneList = new ListTag();
                scenes.getOrDefault(player, Set.of()).forEach(scene -> sceneList.add(LongTag.valueOf(scene)));
                own.put("scenes", sceneList);
                ListTag herderList = new ListTag();
                herder.getOrDefault(player, Set.of()).forEach(variant -> herderList.add(StringTag.valueOf(variant)));
                own.put("herder", herderList);
                own.putInt("dice", dice.getOrDefault(player, 0));
                own.putInt("hardNights", hardNights.getOrDefault(player, 0));
                own.putBoolean("folded", folded.contains(player));
                players.put(player.toString(), own);
            }
            tag.put("players", players);
            CompoundTag sharedTag = new CompoundTag();
            shared.forEach(sharedTag::putLong);
            tag.put("shared", sharedTag);
            ListTag bloodiedList = new ListTag();
            bloodied.forEach(act -> bloodiedList.add(StringTag.valueOf(act)));
            tag.put("bloodied", bloodiedList);
            return tag;
        }
    }

    static JsonArray view(ServerPlayer player) {
        State state = State.of(player.server);
        Map<String, Long> known = new HashMap<>(state.shared);
        known.putAll(state.earned.getOrDefault(player.getUUID(), Map.of()));
        JsonArray deeds = new JsonArray();
        for (String deed : ALL) {
            Long day = known.get(deed);
            if (day == null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("day", day);
            entry.addProperty("key", deed);
            deeds.add(entry);
        }
        return deeds;
    }

    static void earn(ServerPlayer player, String deed) {
        State state = State.of(player.server);
        Map<String, Long> earned = state.earned.computeIfAbsent(player.getUUID(), key -> new LinkedHashMap<>());
        if (earned.containsKey(deed) || state.shared.containsKey(deed)) {
            return;
        }
        earned.put(deed, day(player.server));
        state.setDirty();
        announce(player, deed);
        Journal.sync(player);
    }

    static void share(MinecraftServer server, String deed) {
        State state = State.of(server);
        if (state.shared.containsKey(deed)) {
            return;
        }
        state.shared.put(deed, day(server));
        state.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!state.earned.getOrDefault(player.getUUID(), Map.of()).containsKey(deed)) {
                announce(player, deed);
            }
        }
        Journal.syncAll(server);
    }

    private static void announce(ServerPlayer player, String deed) {
        Banners.show(player, Component.translatable("wayfarer.banner.deed"), Component.translatable("wayfarer.deed." + deed));
        Sounds.play(player, SOUND);
    }

    private static long day(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), Clock.DAY) + 1;
    }

    static void scene(ServerPlayer player, BlockPos centre) {
        State state = State.of(player.server);
        if (state.scenes.computeIfAbsent(player.getUUID(), key -> new LinkedHashSet<>()).add(centre.asLong())) {
            state.setDirty();
            if (state.scenes.get(player.getUUID()).size() >= Balance.get().deeds().scenes()) {
                earn(player, KNOWN);
            }
        }
    }

    static void herder(ServerPlayer player, String variant) {
        State state = State.of(player.server);
        Set<String> met = state.herder.computeIfAbsent(player.getUUID(), key -> new LinkedHashSet<>());
        if (met.add(variant)) {
            state.setDirty();
            if (met.containsAll(HERDER)) {
                earn(player, OLD_FRIENDS);
            }
        }
    }

    static void diced(ServerPlayer player) {
        State state = State.of(player.server);
        int won = state.dice.merge(player.getUUID(), 1, Integer::sum);
        state.setDirty();
        if (won >= Balance.get().deeds().diceWins()) {
            earn(player, DICE);
        }
    }

    public static void folded(ServerPlayer player) {
        State state = State.of(player.server);
        if (state.folded.add(player.getUUID())) {
            state.setDirty();
        }
    }

    public static void hardNight(ServerPlayer player) {
        nights.put(player.getUUID(), Math.floorDiv(player.server.overworld().getDayTime(), Clock.DAY));
    }

    public static void dawn(MinecraftServer server) {
        State state = State.of(server);
        long today = Math.floorDiv(server.overworld().getDayTime(), Clock.DAY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long night = nights.get(player.getUUID());
            if (night != null && night == today - 1) {
                int held = state.hardNights.merge(player.getUUID(), 1, Integer::sum);
                state.setDirty();
                if (held >= Balance.get().deeds().hardNights()) {
                    earn(player, HELD);
                }
            }
        }
        nights.clear();
    }

    static void begun(MinecraftServer server, Story.Quest quest) {
        if (!quest.id().equals(FRAME)) {
            return;
        }
        int shards = Journey.claimed(server).size();
        if (shards <= Balance.get().deeds().lightShards()) {
            share(server, LIGHT_POCKETS);
        }
        if (shards == Doors.road()) {
            share(server, ALL_TWELVE);
        }
        if (Steps.alone(Progress.of(server))) {
            share(server, ALONE);
        }
        State state = State.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!state.folded.contains(player.getUUID())) {
                earn(player, SLOW_WAY);
            }
        }
    }

    static void done(MinecraftServer server, Story.Quest quest) {
        Progress progress = Progress.of(server);
        if (quest.id().equals(HOLLOW)) {
            String prefix = HOLLOW + "/";
            if (progress.choices.entrySet().stream().noneMatch(entry -> entry.getKey().startsWith(prefix) && entry.getValue().equals(Steps.LOST))) {
                share(server, NOBODY_LEFT);
            }
        }
        String act = quest.act();
        if (!quest.kind().equals("main") || !ACTS.contains(act)) {
            return;
        }
        boolean whole = Story.get().quests().stream().filter(other -> other.act().equals(act) && other.kind().equals("main")).allMatch(other -> progress.done(other.id()));
        if (whole && !State.of(server).bloodied.contains(act)) {
            share(server, HANDS_CLEAN);
        }
    }

    @SubscribeEvent
    public static void onRest(RestEvent event) {
        ServerPlayer player = event.player();
        State state = State.of(player.server);
        Balance.Deeds balance = Balance.get().deeds();
        List<Long> fires = state.fires.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
        BlockPos fire = event.fire();
        double apart = (double) balance.fireApartBlocks() * balance.fireApartBlocks();
        if (fires.stream().map(BlockPos::of).allMatch(other -> Steps.horizontal(other, fire) >= apart)) {
            fires.add(fire.asLong());
            state.setDirty();
            if (fires.size() >= balance.fires()) {
                earn(player, MANY_FIRES);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (dead instanceof ServerPlayer player) {
            nights.remove(player.getUUID());
        }
        if (dead instanceof GreatWorm) {
            int reach = Balance.get().deeds().wormBlocks();
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(dead) <= (double) reach * reach && !struck.contains(player.getUUID())) {
                    earn(player, UNTOUCHED);
                }
            }
            struck.clear();
        }
        if (event.getSource().getEntity() instanceof Player && person(dead) && Engine.running(server)) {
            Engine.main(server).map(active -> active.quest().act()).filter(ACTS::contains).ifPresent(act -> {
                State state = State.of(server);
                if (state.bloodied.add(act)) {
                    state.setDirty();
                }
            });
        }
    }

    private static boolean person(Entity entity) {
        EntityType<?> type = entity.getType();
        return entity instanceof Player || type.is(EntityTypeTags.ILLAGER) || type == EntityType.VILLAGER || type == EntityType.WANDERING_TRADER
                || type == EntityType.WITCH || BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(EASY_NPC);
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof GreatWorm) {
            struck.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Warden warden) || event.loadedFromDisk()) {
            return;
        }
        for (City city : cities.values()) {
            if (city.start.getBoundingBox().isInside(warden.blockPosition())) {
                city.woke = true;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Balance.Deeds balance = Balance.get().deeds();
        State state = State.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (server.getTickCount() % WALK_TICKS == 0) {
                long walked = (long) player.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                        + player.getStats().getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
                if (walked >= (long) balance.walkedBlocks() * CENTIMETRES) {
                    earn(player, LONG_ROAD);
                }
            }
            if (player.level().dimension() != Level.OVERWORLD) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            for (TagKey<Structure> tag : VILLAGES) {
                StructureStart village = level.structureManager().getStructureWithPieceAt(player.blockPosition(), tag);
                if (village.isValid() && state.villages.computeIfAbsent(player.getUUID(), key -> new LinkedHashSet<>()).add(village.getChunkPos().toLong())) {
                    state.setDirty();
                    if (state.villages.get(player.getUUID()).size() >= balance.villages()) {
                        earn(player, EVERY_VILLAGE);
                    }
                }
            }
            city(player, level, balance);
            if (Sea.afloat(player) && LastSea.past(level, player.getX(), player.getZ()).orElse(0) >= balance.edgeBlocks()) {
                earn(player, EDGE);
            }
        }
    }

    private static void city(ServerPlayer player, ServerLevel level, Balance.Deeds balance) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure -> structure.is(BuiltinStructures.ANCIENT_CITY));
        City city = cities.get(player.getUUID());
        if (start.isValid()) {
            if (city == null || city.start != start) {
                city = new City(start);
                cities.put(player.getUUID(), city);
            }
            BlockPos heart = city.start.getBoundingBox().getCenter();
            if (Steps.horizontal(player.blockPosition(), heart) <= (double) balance.cityHeartBlocks() * balance.cityHeartBlocks()) {
                city.heart = true;
            }
            return;
        }
        if (city != null) {
            cities.remove(player.getUUID());
            if (city.heart && !city.woke) {
                earn(player, DEEP_QUIET);
            }
        }
    }
}
