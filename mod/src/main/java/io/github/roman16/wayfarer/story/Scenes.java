package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Footing;
import io.github.roman16.wayfarer.Handover;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Leaving;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Sea;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Scenes {
    static final String TAG = Wayfarer.MODID + "_scene";
    static final String CACHE = "cache";
    private static final double ROLL_LENGTH = 4;
    private static final int ROLL_SPLASHES = 64;
    private static final double ROWING = 3;
    private static final int BRING_BLOCKS = 16;
    private static final int LAND_NEAR = 16;
    private static final String NAME = "wayfarer_scenes";
    private static final String GAZEBOUND = "gazebound";
    private static final String RUMOUR = "rumour";
    private static final String STRANGER = "stranger";
    private static final String TRAVELLER = "traveller";
    private static final String CACHE_QUEST = "the_cache";
    private static final String STORY_DONE = "ash";
    private static final String PULL = "the_pull";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Bundled<JsonObject> TEXT = Bundled.json("/wayfarer/scenes.json");
    private static final List<String> ROAD = List.of("cart", "herder", "pedlar", "questioning", "ridge", TRAVELLER, "trunk", "wolves");
    private static final List<String> SEA = List.of("fishers", "flotsam", "grey_sails", "swell");
    private static final List<String> LIVING = List.of("road_01", "road_02", "road_03", "road_04", "road_05", "road_10", "road_11");
    private static final List<String> DEAD = List.of("road_dead_01", "road_dead_02");
    private static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/ruin"));
    private static final int ANGLE_STEP = 15;
    private static final int APART = 256;
    private static final int CHECK_TICKS = 20;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int FOLLOW_BLOCKS = 40;
    private static final int GORGE = 4;
    private static final int GORGE_REACH = 8;
    private static final int HEADING_BLOCKS = 16;
    private static final int HIGHER = 4;
    private static final int LEAVE_BLOCKS = 64;
    private static final int RECENT = 3;
    private static final int ROAD_FROM = 1000;
    private static final int RUIN_CHUNKS = 48;
    private static final int RUMOUR_AHEAD = 192;
    private static final int RETRY_SECONDS = 16;
    private static final int RIMS = 2;
    private static final int UNEVEN = 1;
    private static final int SCAN_STEP = 16;
    private static final float SKEW_DEGREES = 32;
    private static final int TELEPORT_BLOCKS = 8;
    private static final int TICKS_PER_SECOND = 20;
    private static final int TRACKS = 9;
    private static final int WATER_REACH = 24;
    private static final int WIDEST = 60;
    private static final Map<UUID, BlockPos> anchors = new HashMap<>();
    private static final Map<UUID, BlockPos> last = new HashMap<>();
    private static final Map<UUID, Vec3> headings = new HashMap<>();

    private Scenes() {}

    private record Spot(BlockPos centre, Rotation turn) {}

    static final class Scene {
        private String kind = "";
        private BlockPos centre = BlockPos.ZERO;
        private Rotation turn = Rotation.NONE;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final List<UUID> entities = new ArrayList<>();
        private final Set<UUID> greeted = new HashSet<>();
        private final Map<UUID, Double> nearest = new HashMap<>();
        private boolean seen;
        private boolean keep;
        private String state = "";
        private UUID leader = new UUID(0, 0);
        private long placed;

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("kind", kind);
            tag.putLong("centre", centre.asLong());
            tag.putString("turn", turn.name());
            ListTag blocks = new ListTag();
            originals.forEach((pos, block) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.put("state", NbtUtils.writeBlockState(block));
                blocks.add(entry);
            });
            tag.put("originals", blocks);
            tag.put("entities", uuids(entities));
            tag.put("greeted", uuids(greeted));
            CompoundTag closest = new CompoundTag();
            nearest.forEach((player, distance) -> closest.putDouble(player.toString(), distance));
            tag.put("nearest", closest);
            tag.putBoolean("seen", seen);
            tag.putBoolean("keep", keep);
            tag.putString("state", state);
            tag.putUUID("leader", leader);
            tag.putLong("placed", placed);
            return tag;
        }

        private static Scene load(CompoundTag tag, HolderLookup.Provider registries) {
            Scene scene = new Scene();
            scene.kind = tag.getString("kind");
            scene.centre = BlockPos.of(tag.getLong("centre"));
            scene.turn = Rotation.valueOf(tag.getString("turn"));
            for (Tag entry : tag.getList("originals", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) entry;
                scene.originals.put(BlockPos.of(block.getLong("pos")), NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), block.getCompound("state")));
            }
            scene.entities.addAll(uuids(tag.getList("entities", Tag.TAG_INT_ARRAY)));
            scene.greeted.addAll(uuids(tag.getList("greeted", Tag.TAG_INT_ARRAY)));
            CompoundTag closest = tag.getCompound("nearest");
            closest.getAllKeys().forEach(player -> scene.nearest.put(UUID.fromString(player), closest.getDouble(player)));
            scene.seen = tag.getBoolean("seen");
            scene.keep = tag.getBoolean("keep");
            scene.state = tag.getString("state");
            scene.leader = tag.getUUID("leader");
            scene.placed = tag.getLong("placed");
            return scene;
        }
    }

    static final class Ledger extends SavedData {
        private final List<Scene> scenes = new ArrayList<>();
        private final Map<UUID, Long> due = new HashMap<>();
        private final List<String> recent = new ArrayList<>();
        private final Set<String> asked = new HashSet<>();
        private long taken = -1;
        private boolean packFound;
        private long voiceAt = Long.MIN_VALUE;
        @Nullable
        private BlockPos rumour;
        private long rumourDay;

        static Ledger of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ledger::new, Ledger::load, null), NAME);
        }

        private static Ledger load(CompoundTag tag, HolderLookup.Provider registries) {
            Ledger ledger = new Ledger();
            tag.getList("scenes", Tag.TAG_COMPOUND).forEach(entry -> ledger.scenes.add(Scene.load((CompoundTag) entry, registries)));
            CompoundTag due = tag.getCompound("due");
            due.getAllKeys().forEach(player -> ledger.due.put(UUID.fromString(player), due.getLong(player)));
            tag.getList("recent", Tag.TAG_STRING).forEach(entry -> ledger.recent.add(entry.getAsString()));
            tag.getList("asked", Tag.TAG_STRING).forEach(entry -> ledger.asked.add(entry.getAsString()));
            ledger.taken = tag.getLong("taken");
            ledger.packFound = tag.getBoolean("pack_found");
            ledger.voiceAt = tag.getLong("voice_at");
            if (tag.contains("rumour")) {
                ledger.rumour = BlockPos.of(tag.getLong("rumour"));
                ledger.rumourDay = tag.getLong("rumour_day");
            }
            return ledger;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag scenes = new ListTag();
            this.scenes.forEach(scene -> scenes.add(scene.save()));
            tag.put("scenes", scenes);
            CompoundTag due = new CompoundTag();
            this.due.forEach((player, at) -> due.putLong(player.toString(), at));
            tag.put("due", due);
            ListTag recent = new ListTag();
            this.recent.forEach(kind -> recent.add(StringTag.valueOf(kind)));
            tag.put("recent", recent);
            ListTag asked = new ListTag();
            this.asked.forEach(kind -> asked.add(StringTag.valueOf(kind)));
            tag.put("asked", asked);
            tag.putLong("taken", taken);
            tag.putBoolean("pack_found", packFound);
            tag.putLong("voice_at", voiceAt);
            if (rumour != null) {
                tag.putLong("rumour", rumour.asLong());
                tag.putLong("rumour_day", rumourDay);
            }
            return tag;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!Engine.running(server)) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        List<ServerPlayer> players = level.players().stream().filter(player -> !player.isSpectator() && player.isAlive()).toList();
        players.forEach(Scenes::track);
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Ledger ledger = Ledger.of(server);
        for (Scene scene : List.copyOf(ledger.scenes)) {
            watch(server, level, ledger, scene, players);
        }
        rumour(server, level, ledger, players);
        if (!onTheRoad(server, level)) {
            return;
        }
        long now = level.getGameTime();
        for (List<ServerPlayer> group : groups(players)) {
            group.forEach(player -> ledger.due.computeIfAbsent(player.getUUID(), key -> now + rhythm(level.getRandom())));
            boolean waiting = group.stream().anyMatch(player -> ledger.due.get(player.getUUID()) > now);
            boolean near = ledger.scenes.stream().anyMatch(scene -> !scene.keep
                    && group.stream().anyMatch(player -> scene.centre.closerToCenterThan(player.position(), APART)));
            if (!waiting && !near) {
                group.stream().filter(player -> headings.containsKey(player.getUUID())).findFirst().ifPresent(leader -> {
                    List<String> kinds = Sea.afloat(leader) && Sea.open(level, leader.blockPosition()) ? sea(ledger, level) : kinds(server, ledger, level.getRandom());
                    if (!place(server, level, ledger, leader, group, players, kinds)) {
                        group.forEach(player -> ledger.due.put(player.getUUID(), now + RETRY_SECONDS * TICKS_PER_SECOND));
                        LOGGER.info("no road scene fits ahead of {} yet: {}", leader.getScoreboardName(), kinds);
                    }
                });
            }
        }
        ledger.setDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        anchors.clear();
        last.clear();
        headings.clear();
    }

    @SubscribeEvent
    public static void onTrade(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof WanderingTrader trader && trader.getTags().contains(TAG) && event.getEntity() instanceof ServerPlayer player) {
            scene(player.server, trader).filter(scene -> scene.greeted.add(player.getUUID())).ifPresent(scene -> {
                Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line("pedlar", text(scene.kind).get("greeting").getAsString()));
                Ledger.of(player.server).setDirty();
            });
        }
    }

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        MinecraftServer server = event.player().server;
        if (!Engine.running(server)) {
            return;
        }
        BlockPos rumour = Ledger.of(server).rumour;
        if (rumour != null) {
            event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.LEAD, "wayfarer.place." + RUMOUR, rumour.getX(), rumour.getY(), rumour.getZ()));
        }
    }

    static boolean interact(ServerPlayer player, Entity target) {
        Optional<Scene> found = scene(player.server, target);
        if (found.isEmpty()) {
            return false;
        }
        Scene scene = found.get();
        Ledger ledger = Ledger.of(player.server);
        JsonObject text = text(scene.kind);
        Optional<String> giver = People.who(target).filter(role -> role.equals("herder") || role.equals("tide_fisher"));
        if (giver.isPresent()) {
            if (scene.greeted.add(player.getUUID())) {
                boolean gift = scene.state.equals("gift");
                if (giver.get().equals("herder")) {
                    Deeds.herder(player, gift ? "gift" : "warning");
                }
                Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(giver.get(), text.get(gift ? "gift" : "warning").getAsString()));
                if (gift) {
                    for (JsonElement item : text.getAsJsonArray("gifts")) {
                        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.getAsJsonObject().get("id").getAsString())),
                                item.getAsJsonObject().get("count").getAsInt());
                        Notices.given(player, List.of(stack));
                        Handover.give(player, stack);
                    }
                }
            } else if (Talk.again(player.server, giver.get())) {
                JsonArray again = text.getAsJsonArray("again");
                Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(giver.get(), again.get(player.getRandom().nextInt(again.size())).getAsString()));
            }
            ledger.setDirty();
            return true;
        }
        if (text.has("clues")) {
            for (Map.Entry<String, JsonElement> clue : text.getAsJsonObject("clues").entrySet()) {
                if (target.getTags().contains(Steps.CLUE + clue(scene.kind, clue.getKey()))) {
                    Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(null, clue.getValue().getAsString()));
                    if (scene.kind.equals("pack")) {
                        ledger.packFound = true;
                        ledger.setDirty();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static ListTag uuids(Collection<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(NbtUtils.createUUID(id)));
        return list;
    }

    private static List<UUID> uuids(ListTag list) {
        return list.stream().map(NbtUtils::loadUUID).toList();
    }

    private static Optional<Scene> scene(MinecraftServer server, Entity entity) {
        return Ledger.of(server).scenes.stream().filter(scene -> scene.entities.contains(entity.getUUID())).findFirst();
    }

    static String voice(String kind) {
        return voice(kind, 0);
    }

    static String voice(String kind, int which) {
        return text(kind).getAsJsonArray("voice").get(which).getAsString();
    }

    private static JsonObject text(String kind) {
        return TEXT.get().getAsJsonObject(kind);
    }

    private static String clue(String kind, String key) {
        return "scene_" + kind + "_" + key;
    }

    private static boolean onTheRoad(MinecraftServer server, ServerLevel level) {
        return Progress.of(server).done(STORY_DONE) && !Hold.active(server) && Clock.morning(level)
                && Math.floorMod(level.getDayTime(), Clock.DAY) >= ROAD_FROM;
    }

    private static long rhythm(RandomSource random) {
        Balance.Scenes balance = Balance.get().scenes();
        return (long) (balance.everySeconds() - balance.jitterSeconds() + random.nextInt(2 * balance.jitterSeconds() + 1)) * TICKS_PER_SECOND;
    }

    private static void track(ServerPlayer player) {
        BlockPos at = player.blockPosition();
        BlockPos previous = last.put(player.getUUID(), at);
        if (previous == null || previous.distSqr(at) > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
            anchors.put(player.getUUID(), at);
            headings.remove(player.getUUID());
            return;
        }
        BlockPos anchor = anchors.computeIfAbsent(player.getUUID(), key -> at);
        if (anchor.distSqr(at) >= HEADING_BLOCKS * HEADING_BLOCKS) {
            headings.put(player.getUUID(), Vec3.atLowerCornerOf(at.subtract(anchor)).multiply(1, 0, 1).normalize());
            anchors.put(player.getUUID(), at);
        }
    }

    private static List<List<ServerPlayer>> groups(List<ServerPlayer> players) {
        int reach = Balance.get().scenes().groupBlocks();
        List<List<ServerPlayer>> groups = new ArrayList<>();
        for (ServerPlayer player : players) {
            List<List<ServerPlayer>> joined = groups.stream()
                    .filter(group -> group.stream().anyMatch(member -> member.distanceToSqr(player) <= (double) reach * reach)).toList();
            List<ServerPlayer> merged = new ArrayList<>(List.of(player));
            joined.forEach(merged::addAll);
            groups.removeAll(joined);
            groups.add(merged);
        }
        return groups;
    }

    private static List<String> kinds(MinecraftServer server, Ledger ledger, RandomSource random) {
        Progress progress = Progress.of(server);
        if (progress.done(PULL) && !progress.done(CACHE_QUEST) && progress.place(CACHE).isEmpty() && ledger.scenes.stream().noneMatch(scene -> scene.kind.equals(CACHE))) {
            return List.of(CACHE);
        }
        List<String> kinds = new ArrayList<>(ROAD);
        kinds.removeAll(ledger.recent);
        kinds.removeAll(ledger.asked);
        Collections.shuffle(kinds, new Random(random.nextLong()));
        long today = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
        if (ledger.taken >= 0 && today > ledger.taken && !ledger.packFound && ledger.scenes.stream().noneMatch(scene -> scene.kind.equals("pack"))) {
            kinds.addFirst("pack");
        }
        return kinds;
    }

    private static List<String> sea(Ledger ledger, ServerLevel level) {
        List<String> kinds = new ArrayList<>(SEA);
        kinds.removeAll(ledger.recent);
        Collections.shuffle(kinds, new Random(level.getRandom().nextLong()));
        return kinds;
    }

    static boolean stage(ServerPlayer player, String kind) {
        headings.put(player.getUUID(), Vec3.directionFromRotation(0, player.getYRot()));
        ServerLevel level = Engine.overworld(player.server);
        List<ServerPlayer> players = level.players().stream().filter(member -> !member.isSpectator()).toList();
        return place(player.server, level, Ledger.of(player.server), player, List.of(player), players, List.of(kind));
    }

    private static boolean place(MinecraftServer server, ServerLevel level, Ledger ledger, ServerPlayer leader, List<ServerPlayer> group,
            List<ServerPlayer> players, List<String> kinds) {
        for (String kind : kinds) {
            Optional<Spot> spot = spot(level, kind, leader, players);
            if (spot.isPresent()) {
                Scene scene = build(level, kind, spot.get(), leader);
                ledger.scenes.add(scene);
                if (ROAD.contains(kind) || SEA.contains(kind)) {
                    ledger.recent.add(kind);
                    while (ledger.recent.size() > RECENT) {
                        ledger.recent.removeFirst();
                    }
                }
                long next = level.getGameTime() + rhythm(level.getRandom());
                group.forEach(player -> ledger.due.put(player.getUUID(), next));
                ledger.setDirty();
                LOGGER.info("a road scene, {}, waits at {} ahead of {}", kind, scene.centre, leader.getScoreboardName());
                Record.event(server, "scene", leader, line -> line.addProperty("scene", kind));
                return true;
            }
        }
        return false;
    }

    private static Optional<Spot> spot(ServerLevel level, String kind, ServerPlayer leader, List<ServerPlayer> players) {
        Vec3 heading = headings.get(leader.getUUID());
        Balance.Scenes balance = Balance.get().scenes();
        boolean high = kind.equals("ridge") || kind.equals("wolves");
        boolean afloat = SEA.contains(kind);
        int from = afloat ? balance.farthestBlocks() : high ? balance.farthestBlocks() - balance.nearestBlocks() / 2 : balance.nearestBlocks();
        int to = afloat ? balance.seaBlocks() : balance.farthestBlocks();
        int radius = radius(level, kind);
        for (int distance = from; distance <= to; distance += SCAN_STEP) {
            for (int angle : angles()) {
                Vec3 way = heading.yRot((float) Math.toRadians(angle));
                int x = (int) Math.floor(leader.getX() + way.x * distance);
                int z = (int) Math.floor(leader.getZ() + way.z * distance);
                if (!level.isLoaded(new BlockPos(x, 0, z))) {
                    continue;
                }
                BlockPos ground = ground(level, x, z);
                if (afloat) {
                    if (!wet(level, ground) || !Sea.open(level, ground) || crowded(level, ground) || !fits(level, kind, ground, way)) {
                        continue;
                    }
                } else if (wet(level, ground) || !even(level, ground, radius) || inWard(level, ground, radius) || crowded(level, ground)
                        || high && ground.getY() < leader.getBlockY() + HIGHER || !fits(level, kind, ground, way)) {
                    continue;
                }
                List<Vec3> corners = corners(ground, radius);
                if (players.stream().noneMatch(player -> sees(level, player, corners))) {
                    return Optional.of(new Spot(ground, turn(leader.position(), ground)));
                }
            }
        }
        return Optional.empty();
    }

    private static int radius(ServerLevel level, String kind) {
        Vec3i size = template(level, kind).map(StructureTemplate::getSize).orElse(new Vec3i(3, 2, 3));
        return Math.max(size.getX(), size.getZ()) / 2 + 1;
    }

    private static List<Integer> angles() {
        List<Integer> angles = new ArrayList<>(List.of(0));
        for (int angle = ANGLE_STEP; angle <= WIDEST; angle += ANGLE_STEP) {
            angles.add(angle);
            angles.add(-angle);
        }
        return angles;
    }

    private static boolean fits(ServerLevel level, String kind, BlockPos ground, Vec3 way) {
        return switch (kind) {
            case "herder" -> {
                var biome = level.getBiome(ground);
                yield biome.is(Tags.Biomes.IS_PLAINS) || biome.is(Tags.Biomes.IS_SAVANNA);
            }
            case "cart" -> water(level, ground) != null;
            case "fishers" -> level.isDay() && !level.isRaining();
            case "flotsam", "grey_sails", "swell" -> true;
            case "trunk" -> gorge(level, ground, way);
            default -> !level.getBiome(ground).is(BiomeTags.IS_OCEAN) && Sites.open(level.getBiome(ground));
        };
    }

    private static boolean gorge(ServerLevel level, BlockPos ground, Vec3 way) {
        int near = ground(level, (int) Math.floor(ground.getX() - way.x * GORGE_REACH), (int) Math.floor(ground.getZ() - way.z * GORGE_REACH)).getY();
        int far = ground(level, (int) Math.floor(ground.getX() + way.x * GORGE_REACH), (int) Math.floor(ground.getZ() + way.z * GORGE_REACH)).getY();
        return near >= ground.getY() + GORGE && far >= ground.getY() + GORGE && Math.abs(near - far) <= RIMS;
    }

    @Nullable
    private static BlockPos water(ServerLevel level, BlockPos ground) {
        for (int reach = SCAN_STEP / 2; reach <= WATER_REACH; reach += SCAN_STEP / 2) {
            for (int step = 0; step < 8; step++) {
                double angle = Math.PI * step / 4;
                BlockPos at = ground(level, ground.getX() + (int) Math.round(Math.cos(angle) * reach), ground.getZ() + (int) Math.round(Math.sin(angle) * reach));
                if (wet(level, at)) {
                    return at;
                }
            }
        }
        return null;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, Ground.land(level, x, z), z);
    }

    private static boolean wet(ServerLevel level, BlockPos ground) {
        return !level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty();
    }

    private static boolean even(ServerLevel level, BlockPos ground, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                BlockPos at = ground(level, ground.getX() + dx, ground.getZ() + dz);
                if (Math.abs(at.getY() - ground.getY()) > UNEVEN || wet(level, at) || level.getBlockState(at).is(BlockTags.LEAVES)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean crowded(ServerLevel level, BlockPos ground) {
        int apart = Balance.get().scenes().nearestBlocks() / 2;
        return Ledger.of(level.getServer()).scenes.stream().anyMatch(scene -> scene.centre.closerThan(ground, apart));
    }

    private static boolean inWard(ServerLevel level, BlockPos ground, int radius) {
        BoundingBox around = new BoundingBox(ground.getX() - radius, ground.getY() - radius, ground.getZ() - radius, ground.getX() + radius,
                ground.getY() + radius, ground.getZ() + radius).inflatedBy(Balance.get().scenes().nearestBlocks());
        return Progress.of(level.getServer()).wards.values().stream().anyMatch(box -> box.intersects(around));
    }

    private static Rotation turn(Vec3 from, BlockPos to) {
        double dx = from.x - to.getX();
        double dz = from.z - to.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
        }
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    private static List<Vec3> corners(BlockPos centre, int radius) {
        List<Vec3> points = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2 * radius) {
            for (int dz = -radius; dz <= radius; dz += 2 * radius) {
                points.add(Vec3.atCenterOf(centre.offset(dx, 1, dz)));
                points.add(Vec3.atCenterOf(centre.offset(dx, 3, dz)));
            }
        }
        return points;
    }

    private static boolean sees(ServerLevel level, ServerPlayer player, List<Vec3> points) {
        Vec3 eye = player.getEyePosition();
        int farthest = Balance.get().scenes().farthestBlocks();
        for (Vec3 point : points) {
            if (point.distanceToSqr(eye) > (double) farthest * farthest || !level.isLoaded(BlockPos.containing(point))) {
                continue;
            }
            if (Sight.hit(level, eye, point).map(hit -> hit.distanceToSqr(point) < 2.25).orElse(true)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<StructureTemplate> template(ServerLevel level, String kind) {
        String piece = switch (kind) {
            case CACHE, "cart", "herder", "pedlar", TRAVELLER, "trunk" -> kind;
            case "pack", "questioning" -> TRAVELLER;
            default -> null;
        };
        return Optional.ofNullable(piece).flatMap(name -> level.getStructureManager().get(Wayfarer.id("scene/" + name)));
    }

    private static Scene build(ServerLevel level, String kind, Spot spot, ServerPlayer leader) {
        Scene scene = new Scene();
        scene.kind = kind;
        scene.centre = spot.centre();
        scene.turn = spot.turn();
        scene.leader = leader.getUUID();
        scene.placed = level.getGameTime();
        scene.keep = kind.equals(CACHE);
        StructurePlaceSettings settings = Footing.settings(spot.turn(), BlockPos.ZERO);
        BlockPos origin = spot.centre();
        Optional<StructureTemplate> template = template(level, kind);
        if (template.isPresent()) {
            Vec3i size = template.get().getSize();
            origin = spot.centre().subtract(StructureTemplate.calculateRelativePosition(settings, new BlockPos(size.getX() / 2, 0, size.getZ() / 2)));
            Footing.build(level, template.get(), settings, origin, 0, pos -> scene.originals.putIfAbsent(pos, level.getBlockState(pos)));
        }
        RandomSource random = level.getRandom();
        Vec3 facing = leader.position();
        switch (kind) {
            case "herder" -> {
                scene.state = random.nextBoolean() ? "gift" : "warning";
                add(scene, People.role(level, "herder", pick(LIVING, random), at(settings, origin, 2.5, 1, 4.5), yaw(at(settings, origin, 2.5, 1, 4.5),
                        at(settings, origin, 4.5, 1, 4.5)), "sitting_crossed", TAG));
                for (int sheep = 0; sheep < 4; sheep++) {
                    Mob animal = EntityType.SHEEP.create(level);
                    spawn(level, scene, animal, at(settings, origin, 10.5 + sheep % 2, 1, 2.5 + sheep * 1.5), random.nextFloat() * 360);
                }
            }
            case "pedlar" -> {
                WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
                trader.setDespawnDelay(Integer.MAX_VALUE);
                spawn(level, scene, trader, at(settings, origin, 3.5, 1, 4.5), yaw(at(settings, origin, 3.5, 1, 4.5), facing));
                while (trader.getOffers().size() > 3) {
                    trader.getOffers().removeLast();
                }
            }
            case TRAVELLER -> {
                Entity body = People.spawn(level, pick(DEAD, random), at(settings, origin, 2.5, 1, 2.5), random.nextFloat() * 360, "fallen_back", TAG,
                        Steps.CLUE + clue(kind, TRAVELLER));
                add(scene, body);
            }
            case "cart" -> cart(level, scene, settings, origin, random);
            case "pack" -> Clues.marker(level, origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(3, 1, 1))), clue(kind, "pack"))
                    .forEach(entity -> add(scene, entity));
            case "questioning" -> questioning(level, scene, settings, origin, random);
            case "ridge" -> ridge(level, scene, spot.centre(), headings.get(leader.getUUID()));
            case "wolves" -> {
                for (int wolf = 0; wolf < 4; wolf++) {
                    Mob animal = EntityType.WOLF.create(level);
                    spawn(level, scene, animal, Vec3.atBottomCenterOf(spot.centre().above().offset(wolf - 2, 0, wolf % 2)), random.nextFloat() * 360);
                }
            }
            case CACHE -> cache(level, scene, settings, origin);
            case "fishers" -> fishers(level, scene, spot.centre(), random);
            case "flotsam" -> flotsam(level, scene, spot.centre(), random);
            case "grey_sails" -> greySails(level, scene, spot.centre(), headings.get(leader.getUUID()));
            case "swell" -> scene.state = level.isDay() && random.nextBoolean() ? "back" : "dolphins";
            default -> {}
        }
        if (scene.kind.equals("swell") && scene.state.equals("dolphins")) {
            for (int dolphin = 0; dolphin < 3; dolphin++) {
                spawn(level, scene, EntityType.DOLPHIN.create(level), Vec3.atBottomCenterOf(spot.centre().offset(dolphin * 2 - 2, 0, dolphin % 2)), random.nextFloat() * 360);
            }
        }
        return scene;
    }

    private static void add(Scene scene, Entity entity) {
        entity.addTag(TAG);
        scene.entities.add(entity.getUUID());
    }

    private static void spawn(ServerLevel level, Scene scene, Mob mob, Vec3 wanted, float yaw) {
        Vec3 at = Ground.stand(level, wanted);
        mob.moveTo(at.x, at.y, at.z, yaw, 0);
        mob.setYHeadRot(yaw);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        add(scene, mob);
        level.addFreshEntity(mob);
    }

    private static void cart(ServerLevel level, Scene scene, StructurePlaceSettings settings, BlockPos origin, RandomSource random) {
        float yaw = settings.getRotation().rotate(Direction.NORTH).toYRot() + (random.nextFloat() - 0.5F) * SKEW_DEGREES;
        add(scene, Fallen.lay(level, EntityType.MULE, at(settings, origin, 1.0, 1, 3.5), yaw, random.nextBoolean(), Steps.CLUE + clue("cart", "mule")));
        BlockPos water = water(level, scene.centre);
        if (water == null) {
            return;
        }
        Vec3 from = Vec3.atBottomCenterOf(scene.centre);
        Vec3 towards = Vec3.atBottomCenterOf(water).subtract(from).multiply(1, 0, 1).normalize();
        BlockPos last = scene.centre;
        for (int print = 2; print <= TRACKS; print++) {
            BlockPos ground = ground(level, (int) Math.floor(from.x + towards.x * print * 2), (int) Math.floor(from.z + towards.z * print * 2));
            if (wet(level, ground)) {
                break;
            }
            scene.originals.putIfAbsent(ground.above(), level.getBlockState(ground.above()));
            Clues.print(level, ground.above(), clue("cart", "tracks")).forEach(entity -> add(scene, entity));
            last = ground;
        }
        if (last.equals(scene.centre)) {
            Clues.print(level, last.above(), clue("cart", "tracks")).forEach(entity -> add(scene, entity));
        }
        Horse horse = EntityType.HORSE.create(level);
        spawn(level, scene, horse, Vec3.atBottomCenterOf(last.above()), random.nextFloat() * 360);
        horse.setVariant(Variant.BROWN);
    }

    private static void questioning(ServerLevel level, Scene scene, StructurePlaceSettings settings, BlockPos origin, RandomSource random) {
        Vec3 middle = at(settings, origin, 2.5, 1, 2.5);
        add(scene, People.role(level, TRAVELLER, pick(LIVING, random), middle, random.nextFloat() * 360, "sitting_hunched", TAG));
        for (int index = 0; index < 3; index++) {
            double angle = Math.PI * 2 * index / 3;
            Vec3 at = middle.add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            Mob guard = (index == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
            spawn(level, scene, guard, at, yaw(at, middle));
            guard.setNoAi(true);
            guard.addTag(TAG + "_" + GAZEBOUND);
        }
    }

    private static Boat boat(ServerLevel level, Scene scene, Vec3 at, float yaw) {
        Boat boat = EntityType.BOAT.create(level);
        boat.moveTo(at.x, at.y, at.z, yaw, 0);
        add(scene, boat);
        level.addFreshEntity(boat);
        return boat;
    }

    private static void fishers(ServerLevel level, Scene scene, BlockPos surface, RandomSource random) {
        scene.state = random.nextBoolean() ? "gift" : "warning";
        Boat boat = boat(level, scene, Vec3.atBottomCenterOf(surface.above()), random.nextFloat() * 360);
        for (int fisher = 0; fisher < 2; fisher++) {
            Entity body = People.role(level, "tide_fisher", pick(LIVING, random), boat.position(), boat.getYRot(), "sitting", TAG);
            add(scene, body);
            body.startRiding(boat, true);
        }
    }

    private static void flotsam(ServerLevel level, Scene scene, BlockPos surface, RandomSource random) {
        BlockPos barrel = surface;
        scene.originals.putIfAbsent(barrel, level.getBlockState(barrel));
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), FLAGS);
        if (level.getBlockEntity(barrel) instanceof BarrelBlockEntity kept) {
            kept.setItem(0, new ItemStack(Items.BREAD, 2));
            kept.setItem(1, new ItemStack(Items.STRING, 3));
        }
        for (int plank = 0; plank < 5; plank++) {
            BlockPos at = surface.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            if (!at.equals(barrel) && !level.getFluidState(at).isEmpty()) {
                scene.originals.putIfAbsent(at, level.getBlockState(at));
                level.setBlock(at, (plank % 2 == 0 ? Blocks.SPRUCE_SLAB : Blocks.DARK_OAK_SLAB).defaultBlockState().setValue(SlabBlock.WATERLOGGED, true), FLAGS);
            }
        }
        Clues.marker(level, barrel, clue("flotsam", "barrel")).forEach(entity -> add(scene, entity));
    }

    private static void greySails(ServerLevel level, Scene scene, BlockPos surface, Vec3 heading) {
        Vec3 across = new Vec3(-heading.z, 0, heading.x);
        Boat boat = boat(level, scene, Vec3.atBottomCenterOf(surface.above()), (float) Math.toDegrees(Math.atan2(-across.x, across.z)));
        for (int rower = 0; rower < 2; rower++) {
            Mob grey = EntityType.PILLAGER.create(level);
            grey.moveTo(boat.getX(), boat.getY(), boat.getZ(), boat.getYRot(), 0);
            grey.setNoAi(true);
            grey.setSilent(true);
            grey.setPersistenceRequired();
            add(scene, grey);
            level.addFreshEntity(grey);
            grey.startRiding(boat, true);
        }
    }

    private static void row(ServerLevel level, Scene scene) {
        scene.entities.stream().map(level::getEntity).filter(entity -> entity instanceof Boat).forEach(boat -> {
            Vec3 ahead = boat.position().add(Vec3.directionFromRotation(0, boat.getYRot()).scale(ROWING));
            if (!level.getFluidState(BlockPos.containing(ahead.x, boat.getY() - 0.5, ahead.z)).isEmpty()) {
                boat.moveTo(ahead.x, boat.getY(), ahead.z, boat.getYRot(), 0);
            }
        });
    }

    private static void ridge(ServerLevel level, Scene scene, BlockPos centre, Vec3 heading) {
        Vec3 across = new Vec3(-heading.z, 0, heading.x);
        BlockPos target = BlockPos.containing(Vec3.atBottomCenterOf(centre).add(across.scale(LEAVE_BLOCKS * 2)));
        for (int index = 0; index < 4; index++) {
            Mob guard = EntityType.PILLAGER.create(level);
            Vec3 at = Vec3.atBottomCenterOf(centre.above()).add(across.scale(-index * 2));
            spawn(level, scene, guard, at, yaw(at, Vec3.atBottomCenterOf(target)));
            if (guard instanceof PatrollingMonster patrol) {
                patrol.setPatrolTarget(target);
                patrol.setPatrolLeader(index == 0);
            }
        }
    }

    private static void cache(ServerLevel level, Scene scene, StructurePlaceSettings settings, BlockPos origin) {
        clue(level, scene, Blocks.IRON_BLOCK.defaultBlockState(), at(settings, origin, 2.5, 1, 4.5), 0.25f, 0.18f, 0.18f, "tin");
        clue(level, scene, Blocks.BROWN_WOOL.defaultBlockState(), at(settings, origin, 4.5, 1, 3.5), 0.3f, 0.1f, 0.4f, "journal");
        clue(level, scene, Blocks.WHITE_WOOL.defaultBlockState(), at(settings, origin, 0.5, 1, 1.5), 0.45f, 0.35f, 0.45f, "cloth");
    }

    private static void clue(ServerLevel level, Scene scene, BlockState state, Vec3 centre, float x, float y, float z, String clue) {
        add(scene, Clues.item(level, state, centre.subtract(0.5, 0, 0.5), x, y, z, clue));
        add(scene, Clues.box(level, centre, clue));
    }

    private static Vec3 at(StructurePlaceSettings settings, BlockPos origin, double x, double y, double z) {
        BlockPos block = StructureTemplate.calculateRelativePosition(settings, BlockPos.containing(x, y, z));
        Vec3 inside = new Vec3(x - Math.floor(x), y - Math.floor(y), z - Math.floor(z));
        Vec3 turned = switch (settings.getRotation()) {
            case CLOCKWISE_90 -> new Vec3(1 - inside.z, inside.y, inside.x);
            case CLOCKWISE_180 -> new Vec3(1 - inside.x, inside.y, 1 - inside.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(inside.z, inside.y, 1 - inside.x);
            default -> inside;
        };
        return Vec3.atLowerCornerOf(origin.offset(block)).add(turned);
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90;
    }

    private static String pick(List<String> skins, RandomSource random) {
        return skins.get(random.nextInt(skins.size()));
    }

    private static void watch(MinecraftServer server, ServerLevel level, Ledger ledger, Scene scene, List<ServerPlayer> players) {
        List<Vec3> corners = corners(scene.centre, radius(level, scene.kind));
        List<ServerPlayer> seeing = players.stream().filter(player -> sees(level, player, corners) || scene.entities.stream().map(level::getEntity)
                .anyMatch(entity -> entity != null && entity.isAlive() && Sight.sees(player, entity))).toList();
        boolean visible = !seeing.isEmpty();
        if (ROAD.contains(scene.kind) || SEA.contains(scene.kind)) {
            seeing.forEach(player -> Deeds.scene(player, scene.centre));
        }
        if (visible && !scene.seen) {
            scene.seen = true;
            seen(server, ledger, scene);
        }
        switch (scene.kind) {
            case "questioning" -> question(server, level, ledger, scene, players);
            case "wolves" -> shadow(level, scene, players);
            case "grey_sails" -> row(level, scene);
            case CACHE -> {
                if (Progress.of(server).done(CACHE_QUEST)) {
                    scene.entities.stream().map(level::getEntity).filter(entity -> entity != null).forEach(Entity::discard);
                    ledger.scenes.remove(scene);
                }
                return;
            }
            default -> {}
        }
        for (ServerPlayer player : players) {
            double distance = player.position().distanceTo(Vec3.atCenterOf(scene.centre));
            if (distance < scene.nearest.getOrDefault(player.getUUID(), Double.MAX_VALUE)) {
                scene.nearest.put(player.getUUID(), distance);
                ledger.setDirty();
            }
        }
        if (!visible && players.stream().allMatch(player -> past(player, scene)) && level.isPositionEntityTicking(scene.centre)) {
            takeAway(level, ledger, scene);
        }
    }

    private static void seen(MinecraftServer server, Ledger ledger, Scene scene) {
        JsonObject text = text(scene.kind);
        String seen = text.has("seen_" + scene.state) ? "seen_" + scene.state : "seen";
        if (text.has(seen)) {
            Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(null, text.get(seen).getAsString()));
        }
        if (scene.kind.equals("swell") && scene.state.equals("back")) {
            ServerLevel level = Engine.overworld(server);
            level.sendParticles(ParticleTypes.SPLASH, scene.centre.getX() + 0.5, scene.centre.getY() + 1, scene.centre.getZ() + 0.5, ROLL_SPLASHES, ROLL_LENGTH, 0.2, 1, 0.1);
            level.sendParticles(ParticleTypes.BUBBLE_POP, scene.centre.getX() + 0.5, scene.centre.getY() + 1, scene.centre.getZ() + 0.5, ROLL_SPLASHES, ROLL_LENGTH, 0.1, 1, 0.05);
        }
        long now = server.overworld().getGameTime();
        boolean story = scene.kind.equals(CACHE);
        long gap = (long) Balance.get().scenes().voiceGapSeconds() * TICKS_PER_SECOND;
        if (text.has("voice") && !text.has("ask") && !Journey.claimed(server).isEmpty() && (story || now - ledger.voiceAt >= gap)) {
            JsonArray voice = text.getAsJsonArray("voice");
            List<String> names = text.has("names") ? text.getAsJsonArray("names").asList().stream().map(JsonElement::getAsString).toList() : List.of();
            Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(STRANGER, voice.get(server.overworld().getRandom().nextInt(voice.size())).getAsString(), names, null, null, false, null, null, null, null, null, null));
            ledger.voiceAt = now;
        }
        if (story) {
            Progress progress = Progress.of(server);
            progress.place(CACHE, scene.centre);
            progress.setDirty();
            Talk.then(Talk.ROAD, Vec3.atCenterOf(scene.centre), () -> Engine.open(server));
        }
        ledger.setDirty();
    }

    private static void question(MinecraftServer server, ServerLevel level, Ledger ledger, Scene scene, List<ServerPlayer> players) {
        JsonObject text = text(scene.kind);
        int ask = Balance.get().scenes().askBlocks();
        switch (scene.state) {
            case "" -> {
                List<ServerPlayer> near = players.stream().filter(player -> player.position().closerThan(Vec3.atCenterOf(scene.centre), ask)).toList();
                if (scene.seen && !Talk.busy() && !near.isEmpty() && near.stream().allMatch(Talk::free) && near.stream().anyMatch(player -> facing(level, scene, player))) {
                    scene.state = "asked";
                    ledger.asked.add(scene.kind);
                    Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), lines(text.getAsJsonArray("ask")), Talk.Placeholders.NONE);
                    if (!Journey.claimed(server).isEmpty()) {
                        Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(STRANGER, text.getAsJsonArray("voice").get(0).getAsString()));
                    }
                    List<Story.Choice> choices = text.getAsJsonArray("choices").asList().stream().map(JsonElement::getAsJsonObject)
                            .filter(choice -> !choice.has("near") || near(level, scene.centre, choice.get("near").getAsString()))
                            .map(choice -> new Story.Choice(choice.get("id").getAsString(), choice.get("text").getAsString(), choice.get("reply").getAsString(),
                                    null, null, null))
                            .toList();
                    Talk.ask(new Talk.Offer(Talk.ROAD, null, choices, Vec3.atCenterOf(scene.centre), (player, choice) -> chosen(player, scene, choice), false,
                            () -> lapsed(server, scene)));
                }
            }
            case "fighting" -> {
                bring(level, scene);
                boolean standing = scene.entities.stream().map(level::getEntity)
                        .anyMatch(entity -> entity instanceof Mob guard && guard.isAlive() && !guard.isNoAi() && guard.getTags().contains(TAG + "_" + GAZEBOUND));
                if (!standing && level.isPositionEntityTicking(scene.centre)) {
                    scene.state = "won";
                    scene.entities.stream().map(level::getEntity).filter(entity -> entity instanceof Mob guard && guard.isAlive()
                            && guard.getTags().contains(TAG + "_" + GAZEBOUND)).map(entity -> (Mob) entity).forEach(watcher -> {
                                watcher.setNoAi(false);
                                Leaving.send(watcher, Vec3.atCenterOf(scene.centre));
                            });
                    Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), lines(text.getAsJsonArray("won")), Talk.Placeholders.NONE);
                    Talk.then(Talk.ROAD, Vec3.atCenterOf(scene.centre), () -> rumourAhead(server, level, scene));
                }
            }
            case "walked", "hid" -> {
                if (!Talk.busy()) {
                    scene.entities.stream().map(level::getEntity).filter(entity -> entity != null && entity.getTags().contains(TAG + "_" + GAZEBOUND)
                            && players.stream().noneMatch(player -> Sight.sees(player, entity))).forEach(Entity::discard);
                }
            }
            default -> {}
        }
    }

    private static boolean near(ServerLevel level, BlockPos centre, String land) {
        Predicate<BlockState> what = switch (land) {
            case "trees" -> state -> state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
            case "water" -> state -> !state.getFluidState().isEmpty();
            case "rocks" -> state -> state.is(BlockTags.BASE_STONE_OVERWORLD);
            default -> throw new IllegalArgumentException("a scene's answer is near " + land + ", which no rule reads");
        };
        for (int dx = -LAND_NEAR; dx <= LAND_NEAR; dx += 2) {
            for (int dz = -LAND_NEAR; dz <= LAND_NEAR; dz += 2) {
                BlockPos column = centre.offset(dx, 0, dz);
                for (int y = Ground.land(level, column.getX(), column.getZ()); y <= Ground.crown(level, column.getX(), column.getZ()) + 1; y++) {
                    if (what.test(level.getBlockState(column.atY(y)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void bring(ServerLevel level, Scene scene) {
        scene.entities.stream().map(level::getEntity).filter(entity -> entity instanceof Mob guard && guard.isAlive() && !guard.isNoAi()
                && guard.getTarget() != null && guard.distanceTo(guard.getTarget()) > BRING_BLOCKS && !People.seen(level, guard.position()))
                .map(entity -> (Mob) entity).forEach(guard -> People.offstage(level, Ground.stand(level, guard.getTarget().position()))
                        .filter(spot -> spot.distanceTo(guard.getTarget().position()) < guard.distanceTo(guard.getTarget()))
                        .ifPresent(spot -> guard.moveTo(spot.x, spot.y, spot.z, guard.getYRot(), 0)));
    }

    private static boolean facing(ServerLevel level, Scene scene, ServerPlayer player) {
        return scene.entities.stream().map(level::getEntity).anyMatch(entity -> entity != null && entity.getTags().contains(TAG + "_" + GAZEBOUND)
                && Sight.sees(player, entity));
    }

    private static void chosen(ServerPlayer player, Scene scene, Story.Choice choice) {
        MinecraftServer server = player.server;
        Record.event(server, "scene chosen", player, line -> {
            line.addProperty("scene", scene.kind);
            line.addProperty("choice", choice.id());
        });
        Ledger ledger = Ledger.of(server);
        if (ledger.scenes.contains(scene)) {
            Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), new Story.Line(null, choice.reply()));
            ServerLevel level = Engine.overworld(server);
            switch (choice.id()) {
                case "step_in" -> {
                    scene.state = "fighting";
                    scene.entities.stream().map(level::getEntity).filter(entity -> entity instanceof Mob && entity.getTags().contains(TAG + "_" + GAZEBOUND))
                            .map(entity -> (Mob) entity).min(Comparator.comparingDouble(guard -> guard.distanceToSqr(player))).ifPresent(guard -> {
                                guard.setNoAi(false);
                                guard.setTarget(player);
                            });
                }
                case "hide" -> {
                    scene.state = "hid";
                    Talk.say(Talk.ROAD, Vec3.atCenterOf(scene.centre), lines(text(scene.kind).getAsJsonArray("hid")), Talk.Placeholders.NONE);
                }
                default -> {
                    scene.state = "walked";
                    ledger.taken = Math.floorDiv(level.getDayTime(), Clock.DAY);
                }
            }
            ledger.setDirty();
        }
    }

    static void resume(MinecraftServer server) {
        Ledger ledger = Ledger.of(server);
        ledger.scenes.stream().filter(scene -> scene.state.equals("asked")).forEach(scene -> scene.state = "");
        ledger.setDirty();
    }

    private static void lapsed(MinecraftServer server, Scene scene) {
        Ledger ledger = Ledger.of(server);
        if (ledger.scenes.contains(scene) && scene.state.equals("asked")) {
            scene.state = "walked";
            ledger.taken = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
            ledger.setDirty();
            Record.event(server, "scene lapsed", null, line -> line.addProperty("scene", scene.kind));
        }
    }

    private static void rumourAhead(MinecraftServer server, ServerLevel level, Scene scene) {
        ServerPlayer leader = server.getPlayerList().getPlayer(scene.leader);
        Vec3 heading = leader == null ? null : headings.get(leader.getUUID());
        BlockPos from = heading == null ? scene.centre : BlockPos.containing(Vec3.atCenterOf(scene.centre).add(heading.scale(RUMOUR_AHEAD)));
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getTag(RUINS).orElseThrow(), from, RUIN_CHUNKS, false);
        if (found != null) {
            Ledger ledger = Ledger.of(server);
            ledger.rumour = found.getFirst();
            ledger.rumourDay = Math.floorDiv(level.getDayTime(), Clock.DAY);
            ledger.setDirty();
            LOGGER.info("the traveller's rumour points at a ruin at {}", ledger.rumour);
        }
    }

    private static void rumour(MinecraftServer server, ServerLevel level, Ledger ledger, List<ServerPlayer> players) {
        if (ledger.rumour == null) {
            return;
        }
        int arrive = Balance.get().story().arriveBlocks();
        long today = Math.floorDiv(level.getDayTime(), Clock.DAY);
        Vec3 at = Vec3.atCenterOf(ledger.rumour);
        if (today > ledger.rumourDay + 1 || players.stream().anyMatch(player -> player.position().multiply(1, 0, 1).closerThan(at.multiply(1, 0, 1), arrive))) {
            ledger.rumour = null;
            ledger.setDirty();
        }
    }

    private static void shadow(ServerLevel level, Scene scene, List<ServerPlayer> players) {
        long age = level.getGameTime() - scene.placed;
        boolean leaving = age > (long) Balance.get().scenes().wolvesSeconds() * TICKS_PER_SECOND;
        ServerPlayer leader = level.getServer().getPlayerList().getPlayer(scene.leader);
        if (leader == null) {
            return;
        }
        Vec3 heading = headings.getOrDefault(leader.getUUID(), Vec3.directionFromRotation(0, leader.getYRot()));
        for (UUID id : scene.entities) {
            if (leaving && level.getEntity(id) instanceof Mob gone && players.stream().noneMatch(player -> Sight.sees(player, gone)
                    || player.distanceTo(gone) <= LEAVE_BLOCKS)) {
                gone.discard();
                continue;
            }
            if (level.getEntity(id) instanceof Mob wolf && wolf.isAlive() && wolf.getTarget() == null) {
                Vec3 side = wolf.position().subtract(leader.position()).multiply(1, 0, 1);
                Vec3 across = new Vec3(-heading.z, 0, heading.x);
                Vec3 goal = leaving ? wolf.position().add(side.normalize().scale(LEAVE_BLOCKS))
                        : leader.position().add(across.scale(Math.signum(side.dot(across)) * FOLLOW_BLOCKS)).add(heading.scale(HEADING_BLOCKS / 2.0));
                wolf.getNavigation().moveTo(goal.x, ground(level, (int) goal.x, (int) goal.z).getY() + 1, goal.z, 1.0);
            }
        }
    }

    private static boolean past(ServerPlayer player, Scene scene) {
        Balance.Scenes balance = Balance.get().scenes();
        double distance = player.position().distanceTo(Vec3.atCenterOf(scene.centre));
        return distance >= scene.nearest.getOrDefault(player.getUUID(), distance) + balance.goneBlocks()
                || distance >= balance.farthestBlocks() + balance.goneBlocks();
    }

    private static void takeAway(ServerLevel level, Ledger ledger, Scene scene) {
        for (UUID id : scene.entities) {
            Entity entity = level.getEntity(id);
            if (entity != null && !kept(level.getServer(), entity)) {
                entity.discard();
            }
        }
        List<Map.Entry<BlockPos, BlockState>> originals = new ArrayList<>(scene.originals.entrySet());
        for (int index = originals.size() - 1; index >= 0; index--) {
            level.setBlock(originals.get(index).getKey(), originals.get(index).getValue(), FLAGS);
        }
        if (scene.kind.equals("questioning") && scene.state.equals("asked")) {
            ledger.taken = Math.floorDiv(level.getDayTime(), Clock.DAY);
        }
        ledger.scenes.remove(scene);
        ledger.setDirty();
        LOGGER.info("a road scene, {}, was taken away at {}", scene.kind, scene.centre);
    }

    private static boolean kept(MinecraftServer server, Entity entity) {
        return entity instanceof AbstractHorse horse && (horse.isTamed() || horse.isLeashed() || horse.isVehicle())
                || entity.getUUID().equals(Caravan.horse(server)) || entity instanceof LivingEntity living && living.isVehicle();
    }

    private static List<Story.Line> lines(JsonArray lines) {
        return lines.asList().stream().map(JsonElement::getAsJsonObject)
                .map(line -> new Story.Line(line.has("by") ? line.get("by").getAsString() : null, line.get("text").getAsString())).toList();
    }
}
