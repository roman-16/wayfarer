package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Joining;
import io.github.roman16.wayfarer.TentPitch;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

public final class Caravan {
    static final String NAME = "caravan";
    private static final String SAVE = "wayfarer_caravan";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TENTS = "nomadictents";
    private static final Bundled<JsonArray> HOUSEHOLDS = Bundled.json("/wayfarer/story.json", story -> story.getAsJsonArray("caravan"));
    private static final int ANGLE_STEP = 15;
    private static final int AROUND = 32;
    private static final int BUSH = 4;
    private static final int CHUNKS = 2;
    private static final int CROWDED = 2;
    private static final Set<Block> DANGERS = Set.of(Blocks.BEE_NEST, Blocks.BEEHIVE, Blocks.FIRE, Blocks.MAGMA_BLOCK, Blocks.SOUL_FIRE);
    private static final Set<Block> UNDERFOOT = Set.of(Blocks.CACTUS, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH);
    private static final int EMBERS = 2;
    private static final int EXPOSED = 8;
    private static final int FARTHER = 16;
    private static final int FIRE_SHIFT = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int FOUNDATION = 4;
    private static final int GAP = 2;
    private static final int APART_BLOCKS = 8;
    private static final int STANDBY = 4;
    private static final String APART = "apart";
    private static final String JOINS = "joins";
    private static final int HOLLOW = 8;
    private static final int LOW = 4;
    private static final int NEAR_WATER = 8;
    private static final int[] NUDGES = {0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180};
    private static final int REACH = 8;
    private static final int REACH_OUT = 16;
    private static final int RING = 12;
    private static final int GRAZE = 8;
    private static final int SAFE = 16;
    private static final int SAFE_ABOVE = 8;
    private static final int SAFE_BELOW = 4;
    private static final int FARTHEST = 1024;
    private static final int HALF_TURN = 180;
    private static final int SEARCH_SPAN = 128;
    private static final int WIDER = 30;
    private static final int WIDEST_AHEAD = 60;
    private static final int SEARCH_STEP = 32;
    private static final int SEEN_PASSES = 4;
    private static final int STEEP = 4;
    private static final int SURVEY = 16;
    private static final int TALLEST = 8;
    private static final int TRIES = 4;
    private static final int AWAY = 32;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_caravan", Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<Block> TRODDEN = Set.of(Blocks.ANDESITE, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.DIRT_PATH, Blocks.GRAVEL,
            Blocks.MOSSY_COBBLESTONE, Blocks.PODZOL, Blocks.ROOTED_DIRT);
    @Nullable
    private static Search search;
    @Nullable
    private static Packing packing;
    private static long armed = -1;
    private static final Set<UUID> ARRIVED = new HashSet<>();

    private Caravan() {}

    private enum Stage {
        LOADING,
        SEARCHING
    }

    enum Phase {
        GONE,
        SEARCHING,
        STANDING,
        PACKING
    }

    public record HeadIn() implements CustomPacketPayload {
        public static final Type<HeadIn> TYPE = new Type<>(Wayfarer.id("head_in"));
        public static final StreamCodec<ByteBuf, HeadIn> STREAM_CODEC = StreamCodec.unit(new HeadIn());

        @Override
        public Type<HeadIn> type() {
            return TYPE;
        }
    }

    private record Placed(BoundingBox box, BlockPos centre, BlockPos front) {}

    private record Spot(BlockPos centre, int distance, int score) {}

    private record Survey(@Nullable String hard, List<String> wishes) {}

    private record Fallback(BlockPos centre, List<String> wishes) {}

    private record Packing(ChunkPos ticket, Unseen.Heights heights, boolean loading) {}

    private static final class Search {
        private final BlockPos from;
        private final List<BlockPos> rejected = new ArrayList<>();
        private final List<Fallback> fallbacks = new ArrayList<>();
        private final Unseen.Heights heights;
        private int seenPasses;
        private boolean settling;
        private int tries;
        @Nullable
        private CompletableFuture<BlockPos> pending;
        private Stage stage = Stage.SEARCHING;
        private ChunkPos ticket = ChunkPos.ZERO;

        private Search(ServerLevel level, BlockPos from) {
            this.from = from;
            this.heights = new Unseen.Heights(level);
        }
    }

    static final class Camp extends SavedData {
        private Phase phase = Phase.GONE;
        private boolean ever;
        private boolean heading;
        private long day;
        private BlockPos centre = BlockPos.ZERO;
        private float facing;
        private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        private final Map<BlockPos, BlockState> laid = new LinkedHashMap<>();
        private final List<BoundingBox> boxes = new ArrayList<>();
        private long welcomed = -1;
        private long seenOff = -1;
        @Nullable
        private UUID horse;
        private String keeper = "";
        private CompoundTag carried = new CompoundTag();

        static Camp of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Camp::new, Camp::load, null), SAVE);
        }

        private static Camp load(CompoundTag tag, HolderLookup.Provider registries) {
            Camp camp = new Camp();
            camp.phase = Phase.valueOf(tag.getString("phase"));
            camp.ever = tag.getBoolean("ever");
            camp.heading = tag.getBoolean("heading");
            camp.day = tag.getLong("day");
            camp.centre = BlockPos.of(tag.getLong("centre"));
            camp.facing = tag.getFloat("facing");
            if (tag.hasUUID("horse")) {
                camp.horse = tag.getUUID("horse");
            }
            camp.keeper = tag.getString("keeper");
            camp.carried = tag.getCompound("carried");
            camp.welcomed = tag.getLong("welcomed");
            camp.seenOff = tag.getLong("seenOff");
            HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
            for (Tag entry : tag.getList("originals", Tag.TAG_COMPOUND)) {
                CompoundTag original = (CompoundTag) entry;
                camp.originals.put(BlockPos.of(original.getLong("pos")), NbtUtils.readBlockState(blocks, original.getCompound("state")));
            }
            for (Tag entry : tag.getList("laid", Tag.TAG_COMPOUND)) {
                CompoundTag laid = (CompoundTag) entry;
                camp.laid.put(BlockPos.of(laid.getLong("pos")), NbtUtils.readBlockState(blocks, laid.getCompound("state")));
            }
            for (Tag entry : tag.getList("boxes", Tag.TAG_INT_ARRAY)) {
                int[] box = ((IntArrayTag) entry).getAsIntArray();
                camp.boxes.add(new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
            }
            return camp;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("phase", phase.name());
            tag.putBoolean("ever", ever);
            tag.putBoolean("heading", heading);
            tag.putLong("day", day);
            tag.putLong("centre", centre.asLong());
            tag.putFloat("facing", facing);
            if (horse != null) {
                tag.putUUID("horse", horse);
            }
            tag.putString("keeper", keeper);
            tag.put("carried", carried);
            tag.putLong("welcomed", welcomed);
            tag.putLong("seenOff", seenOff);
            tag.put("originals", states(originals));
            tag.put("laid", states(laid));
            ListTag boxes = new ListTag();
            this.boxes.forEach(box -> boxes.add(new IntArrayTag(new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})));
            tag.put("boxes", boxes);
            return tag;
        }

        private static ListTag states(Map<BlockPos, BlockState> states) {
            ListTag list = new ListTag();
            states.forEach((pos, state) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", pos.asLong());
                entry.put("state", NbtUtils.writeBlockState(state));
                list.add(entry);
            });
            return list;
        }

        private boolean untouched(ServerLevel level, BlockPos pos) {
            BlockState left = laid.get(pos);
            return left == null || level.getBlockState(pos).equals(left);
        }
    }

    @Nullable
    static UUID horse(MinecraftServer server) {
        return Camp.of(server).horse;
    }

    static void adopt(MinecraftServer server, AbstractHorse horse, String name, String keeper) {
        Camp camp = Camp.of(server);
        camp.horse = horse.getUUID();
        camp.keeper = keeper;
        camp.setDirty();
        horse.setCustomName(Component.literal(name));
        horse.setPersistenceRequired();
        horse.restrictTo(pasture(server, camp), GRAZE);
    }

    private static BlockPos pasture(MinecraftServer server, Camp camp) {
        return People.find(server, camp.keeper).map(Entity::blockPosition).orElse(camp.centre);
    }

    private static void carry(ServerLevel level, Camp camp) {
        if (camp.horse != null && level.getEntity(camp.horse) instanceof AbstractHorse horse && !horse.isVehicle() && !horse.isLeashed()
                && horse.position().closerThan(Vec3.atCenterOf(camp.centre), RING + GRAZE)) {
            camp.carried = horse.saveWithoutId(new CompoundTag());
            camp.carried.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()).toString());
            horse.discard();
            camp.setDirty();
        }
    }

    private static void release(ServerLevel level, Camp camp) {
        if (camp.carried.isEmpty()) {
            return;
        }
        BlockPos pasture = pasture(level.getServer(), camp);
        Entity horse = EntityType.loadEntityRecursive(camp.carried, level, loaded -> {
            loaded.moveTo(pasture.getX() + GRAZE / 2.0, pasture.getY(), pasture.getZ() + 0.5, 0, 0);
            return loaded;
        });
        if (horse instanceof AbstractHorse grazing) {
            grazing.restrictTo(pasture, GRAZE);
            level.addFreshEntity(grazing);
        }
        camp.carried = new CompoundTag();
        camp.setDirty();
    }

    static void headIn(MinecraftServer server) {
        Camp camp = Camp.of(server);
        if (!camp.heading) {
            camp.heading = true;
            camp.setDirty();
            Journal.syncAll(server);
        }
    }

    public static void headIn(ServerPlayer player) {
        if (Engine.running(player.server) && canHeadIn(player.server)) {
            Talk.notice(Component.translatable("wayfarer.caravan.chosen", player.getDisplayName()));
            headIn(player.server);
        }
    }

    static boolean canHeadIn(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.ever && !camp.heading && camp.phase == Phase.GONE;
    }

    static List<BoundingBox> boxes(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.phase == Phase.STANDING ? List.copyOf(camp.boxes) : List.of();
    }

    static boolean campedToday(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.phase == Phase.STANDING && camp.day == Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
    }

    static Vec3 way(MinecraftServer server) {
        return Vec3.directionFromRotation(0, Camp.of(server).facing);
    }

    static boolean moving(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.ever && !camp.heading && camp.phase != Phase.STANDING && camp.phase != Phase.SEARCHING;
    }

    public static Optional<BlockPos> standing(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.phase == Phase.STANDING || camp.phase == Phase.PACKING ? Optional.of(camp.centre) : Optional.empty();
    }

    static boolean heading(MinecraftServer server) {
        Camp camp = Camp.of(server);
        boolean night = camp.phase == Phase.STANDING
                && Math.floorMod(server.overworld().getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().evening());
        return camp.heading && camp.phase != Phase.PACKING && !night;
    }

    static boolean heading(ServerPlayer player) {
        Camp camp = Camp.of(player.server);
        return heading(player.server) || camp.heading && camp.phase == Phase.STANDING && !ARRIVED.contains(player.getUUID())
                && !Tents.isTent(player.level().dimension());
    }

    static void reset() {
        search = null;
        packing = null;
        armed = -1;
        ARRIVED.clear();
    }

    static void tick(MinecraftServer server) {
        Camp camp = Camp.of(server);
        ServerLevel level = Engine.overworld(server);
        switch (camp.phase) {
            case GONE -> {
                if (!camp.heading && camp.ever && Math.floorMod(level.getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().runFrom())) {
                    Talk.notice(Component.translatable("wayfarer.caravan.evening"));
                    headIn(server);
                }
                if (camp.heading) {
                    start(server, level, camp);
                }
            }
            case SEARCHING -> {
                if (search == null) {
                    restore(level, camp);
                    start(server, level, camp);
                } else if (search.stage == Stage.SEARCHING) {
                    search(server, level, camp);
                } else if (loaded(level, search.ticket)) {
                    load(server, level, camp);
                }
            }
            case STANDING -> {
                guard(level, camp);
                long today = Math.floorDiv(level.getDayTime(), Clock.DAY);
                if (today > camp.day) {
                    camp.phase = Phase.PACKING;
                    camp.heading = false;
                    camp.setDirty();
                    ARRIVED.clear();
                } else {
                    if (camp.heading && armed != today) {
                        armed = today;
                        Clock.caravan(level, camp.centre);
                    }
                    if (arrived(server, level, camp) && camp.welcomed != camp.day && !Talk.busy()) {
                        camp.welcomed = camp.day;
                        camp.setDirty();
                        greet(server, camp.day, Story.Person::welcomes);
                    }
                }
            }
            case PACKING -> {
                long today = Math.floorDiv(level.getDayTime(), Clock.DAY);
                if (camp.seenOff != today && !Talk.busy() && arrived(server, level, camp)) {
                    camp.seenOff = today;
                    camp.setDirty();
                    greet(server, today, Story.Person::mornings);
                }
                pack(server, level, camp);
            }
        }
    }

    private static boolean arrived(MinecraftServer server, ServerLevel level, Camp camp) {
        int circle = Balance.get().fire().circleBlocks();
        boolean any = false;
        for (ServerPlayer player : Engine.players(server)) {
            if (player.level() == level && player.distanceToSqr(camp.centre.getX() + 0.5, player.getY(), camp.centre.getZ() + 0.5) <= (double) circle * circle) {
                ARRIVED.add(player.getUUID());
                any = true;
            }
        }
        return any;
    }

    private static void greet(MinecraftServer server, long day, Function<Story.Person, List<String>> said) {
        List<String> awake = new ArrayList<>();
        for (JsonElement household : households(server)) {
            if (household.getAsJsonObject().has("people")) {
                for (JsonElement person : household.getAsJsonObject().getAsJsonArray("people")) {
                    String id = person.getAsJsonObject().get("person").getAsString();
                    if (Progress.of(server).met(id) && !said.apply(Story.get().person(id)).isEmpty() && People.find(server, id).isPresent()) {
                        awake.add(id);
                    }
                }
            }
        }
        if (awake.isEmpty()) {
            return;
        }
        RandomSource random = RandomSource.create(day);
        String by = awake.get(random.nextInt(awake.size()));
        List<String> lines = said.apply(Story.get().person(by));
        Talk.say(Talk.ROAD, People.find(server, by).map(Entity::position).orElse(null), new Story.Line(by, lines.get(random.nextInt(lines.size()))));
    }

    private static void start(MinecraftServer server, ServerLevel level, Camp camp) {
        List<ServerPlayer> team = Engine.players(server).stream().filter(player -> player.level() == level).toList();
        Progress progress = Progress.of(server);
        BlockPos before = camp.ever ? camp.centre : progress.place(Steps.OWN_CAMP).orElseGet(() -> Engine.at(server, Prologue.LAST_CAMP));
        BlockPos from = team.isEmpty() ? before : BlockPos.containing(team.stream().mapToDouble(ServerPlayer::getX).average().orElseThrow(), 0,
                team.stream().mapToDouble(ServerPlayer::getZ).average().orElseThrow());
        if (from.distSqr(before.atY(from.getY())) > (double) AWAY * AWAY) {
            camp.facing = (float) Math.toDegrees(Math.atan2(from.getZ() - before.getZ(), from.getX() - before.getX())) - 90;
        }
        camp.phase = Phase.SEARCHING;
        camp.centre = from;
        camp.setDirty();
        search = new Search(level, from);
    }

    private static void search(MinecraftServer server, ServerLevel level, Camp camp) {
        if (search.pending == null) {
            int view = server.getPlayerList().getViewDistance();
            List<Unseen.Viewer> viewers = Unseen.viewers(level);
            Unseen.Heights heights = search.heights;
            List<BlockPos> rejected = List.copyOf(search.rejected);
            BlockPos from = search.from;
            float facing = camp.facing;
            boolean anywhereBeyond = search.seenPasses >= SEEN_PASSES;
            int widest = Math.min(HALF_TURN, WIDEST_AHEAD + WIDER * search.seenPasses);
            int span = Math.min(FARTHEST, SEARCH_SPAN * (1 + search.seenPasses));
            search.pending = CompletableFuture.supplyAsync(() -> {
                int nearest = (view + CHUNKS + 2) * 16;
                List<Spot> spots = new ArrayList<>();
                for (int distance = nearest; distance <= nearest + span; distance += SEARCH_STEP) {
                    for (int angle : angles(widest)) {
                        Vec3 way = Vec3.directionFromRotation(0, facing + angle);
                        BlockPos centre = BlockPos.containing(from.getX() + way.x * distance, 0, from.getZ() + way.z * distance);
                        if (rejected.stream().anyMatch(spot -> spot.distSqr(centre.atY(spot.getY())) < AWAY * AWAY * 4) || misfit(heights, centre) != null) {
                            continue;
                        }
                        spots.add(new Spot(centre.atY(heights.surface(centre.getX(), centre.getZ())), distance, score(heights, centre) - (distance - nearest) / FARTHER));
                    }
                }
                spots.sort(Comparator.comparingInt(Spot::score).reversed());
                for (Spot spot : spots) {
                    Vec3 top = new Vec3(spot.centre().getX() + 0.5, spot.centre().getY() + TALLEST, spot.centre().getZ() + 0.5);
                    if (anywhereBeyond ? Unseen.beyond(viewers, top, view) : Unseen.hidden(heights, viewers, List.of(top), view, true)) {
                        return spot.centre();
                    }
                }
                return null;
            }, Util.backgroundExecutor());
            return;
        }
        if (!search.pending.isDone()) {
            return;
        }
        BlockPos found = search.pending.join();
        search.pending = null;
        if (found != null) {
            camp.centre = found;
            hold(level, found);
        } else if (!search.fallbacks.isEmpty()) {
            settle(level, camp);
        } else {
            widen();
        }
    }

    private static List<Integer> angles(int widest) {
        List<Integer> angles = new ArrayList<>(List.of(0));
        for (int angle = ANGLE_STEP; angle <= widest; angle += ANGLE_STEP) {
            angles.add(angle);
            if (angle < HALF_TURN) {
                angles.add(-angle);
            }
        }
        return angles;
    }

    private static void hold(ServerLevel level, BlockPos centre) {
        search.ticket = new ChunkPos(centre);
        level.getChunkSource().addRegionTicket(TICKET, search.ticket, CHUNKS, search.ticket);
        search.stage = Stage.LOADING;
    }

    private static void settle(ServerLevel level, Camp camp) {
        Fallback best = search.fallbacks.stream().min(Comparator.comparingInt(fallback -> fallback.wishes().size())).orElseThrow();
        camp.centre = best.centre();
        search.settling = true;
        hold(level, best.centre());
    }

    private static boolean loaded(ServerLevel level, ChunkPos ticket) {
        for (int x = ticket.x - CHUNKS; x <= ticket.x + CHUNKS; x++) {
            for (int z = ticket.z - CHUNKS; z <= ticket.z + CHUNKS; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void load(MinecraftServer server, ServerLevel level, Camp camp) {
        Survey survey = survey(level, camp.centre);
        String hard = survey.hard() == null ? build(level, camp) : survey.hard();
        level.getChunkSource().removeRegionTicket(TICKET, search.ticket, CHUNKS, search.ticket);
        if (hard == null && (search.settling || survey.wishes().isEmpty())) {
            camp.originals.keySet().forEach(pos -> camp.laid.put(pos, level.getBlockState(pos)));
            camp.phase = Phase.STANDING;
            camp.ever = true;
            camp.day = Math.floorDiv(level.getDayTime(), Clock.DAY);
            camp.setDirty();
            search = null;
            Progress.of(server).place(NAME, camp.centre);
            Journal.syncAll(server);
            if (ModList.get().isLoaded(TENTS)) {
                TentPitch.bringBack(server, new Joining.Place(Level.OVERWORLD, camp.centre));
            }
            release(level, camp);
            LOGGER.info("the caravan made camp at {}", camp.centre);
            return;
        }
        restore(level, camp);
        LOGGER.info("the caravan will not camp at {}: {}", camp.centre, hard != null ? hard : String.join(", ", survey.wishes()));
        if (hard == null) {
            search.fallbacks.add(new Fallback(camp.centre, survey.wishes()));
        }
        search.rejected.add(camp.centre);
        search.tries++;
        search.stage = Stage.SEARCHING;
        if (search.tries >= TRIES && !search.fallbacks.isEmpty()) {
            settle(level, camp);
        } else if (search.tries >= TRIES) {
            search.tries = 0;
            widen();
        }
    }

    private static void widen() {
        search.seenPasses++;
        LOGGER.info("the caravan found no ground to camp on ahead; it looks wider ({} degrees either way, {} blocks on)",
                Math.min(HALF_TURN, WIDEST_AHEAD + WIDER * search.seenPasses), Math.min(FARTHEST, SEARCH_SPAN * (1 + search.seenPasses)));
    }

    private static void restore(ServerLevel level, Camp camp) {
        List<Map.Entry<BlockPos, BlockState>> originals = new ArrayList<>(camp.originals.entrySet());
        for (int index = originals.size() - 1; index >= 0; index--) {
            level.setBlock(originals.get(index).getKey(), originals.get(index).getValue(), FLAGS);
        }
        camp.originals.clear();
        camp.laid.clear();
        camp.boxes.clear();
        camp.setDirty();
    }

    private static void pack(MinecraftServer server, ServerLevel level, Camp camp) {
        if (packing == null) {
            packing = new Packing(new ChunkPos(camp.centre), new Unseen.Heights(level), false);
        }
        if (!packing.loading()) {
            List<Vec3> targets = ring(camp.centre, RING).stream()
                    .map(point -> new Vec3(point.getX() + 0.5, packing.heights().surface(point.getX(), point.getZ()) + TALLEST, point.getZ() + 0.5)).toList();
            if (!Unseen.hidden(packing.heights(), Unseen.viewers(level), targets, server.getPlayerList().getViewDistance(), true)) {
                return;
            }
            level.getChunkSource().addRegionTicket(TICKET, packing.ticket(), CHUNKS, packing.ticket());
            packing = new Packing(packing.ticket(), packing.heights(), true);
            return;
        }
        if (!loaded(level, packing.ticket())) {
            return;
        }
        for (JsonElement household : households(server)) {
            if (household.getAsJsonObject().has("people")) {
                for (JsonElement person : household.getAsJsonObject().getAsJsonArray("people")) {
                    People.find(server, person.getAsJsonObject().get("person").getAsString()).ifPresent(Entity::discard);
                }
            }
        }
        carry(level, camp);
        Map<BlockPos, BlockState> traces = traces(level, camp);
        List<Map.Entry<BlockPos, BlockState>> originals = new ArrayList<>(camp.originals.entrySet());
        for (int index = originals.size() - 1; index >= 0; index--) {
            BlockPos pos = originals.get(index).getKey();
            if (!traces.containsKey(pos) && camp.untouched(level, pos)) {
                level.setBlock(pos, originals.get(index).getValue(), FLAGS);
            }
        }
        traces.forEach((pos, state) -> level.setBlock(pos, state, FLAGS));
        level.getChunkSource().removeRegionTicket(TICKET, packing.ticket(), CHUNKS, packing.ticket());
        packing = null;
        camp.originals.clear();
        camp.laid.clear();
        camp.boxes.clear();
        camp.phase = Phase.GONE;
        camp.setDirty();
        Progress progress = Progress.of(server);
        progress.places.remove(NAME);
        progress.setDirty();
        Journal.syncAll(server);
        LOGGER.info("the caravan packed up at {}, leaving {} blocks of its camp behind", camp.centre, traces.size());
    }

    private static Map<BlockPos, BlockState> traces(ServerLevel level, Camp camp) {
        Map<BlockPos, BlockState> traces = new HashMap<>();
        camp.originals.forEach((pos, was) -> {
            BlockState now = level.getBlockState(pos);
            if (TRODDEN.contains(now.getBlock()) && !was.canBeReplaced() && camp.untouched(level, pos)) {
                traces.put(pos, now);
            }
        });
        boolean leftBehind = RandomSource.create(camp.centre.asLong()).nextBoolean();
        camp.originals.keySet().forEach(pos -> {
            BlockState now = level.getBlockState(pos);
            if (!traces.containsKey(pos.below()) || !camp.untouched(level, pos)) {
                return;
            }
            if (now.is(Blocks.CAMPFIRE)) {
                traces.put(pos, now.setValue(CampfireBlock.LIT, false));
            } else if (now.is(BlockTags.SLABS) || leftBehind && now.is(Blocks.SPRUCE_LOG)) {
                traces.put(pos, now);
            }
        });
        return traces;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt bolt && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Camp camp = Camp.of(level.getServer());
            if (!camp.boxes.isEmpty() && bolt.blockPosition().distSqr(camp.centre.atY(bolt.getBlockY())) < (REACH_OUT + SAFE) * (REACH_OUT + SAFE)) {
                event.setCanceled(true);
            }
        }
    }

    @Nullable
    private static String misfit(Unseen.Heights heights, BlockPos centre) {
        return heights.wet(centre.getX(), centre.getZ()) ? "in water" : null;
    }

    private static int score(Unseen.Heights heights, BlockPos centre) {
        int ground = heights.surface(centre.getX(), centre.getZ());
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int total = 0;
        boolean water = false;
        List<BlockPos> around = ring(centre, AROUND);
        around.removeFirst();
        for (BlockPos point : around) {
            int y = heights.surface(point.getX(), point.getZ());
            low = Math.min(low, y);
            high = Math.max(high, y);
            total += y;
            water |= heights.wet(point.getX(), point.getZ());
        }
        int score = water ? NEAR_WATER : 0;
        if (ground * around.size() < total - LOW * around.size()) {
            score -= HOLLOW;
        }
        if (ground > high + LOW) {
            score -= EXPOSED;
        }
        List<BlockPos> camp = ring(centre, RING);
        int flatLow = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).min().orElse(ground);
        int flatHigh = camp.stream().mapToInt(point -> heights.surface(point.getX(), point.getZ())).max().orElse(ground);
        long soaked = camp.stream().filter(point -> heights.wet(point.getX(), point.getZ())).count();
        return score - (flatHigh - flatLow) - (int) soaked * NEAR_WATER;
    }

    private static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> points = new ArrayList<>(List.of(centre));
        for (int step = 0; step < 8; step++) {
            double angle = Math.toRadians(step * 45);
            points.add(centre.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius)));
        }
        return points;
    }

    private static Survey survey(ServerLevel level, BlockPos centre) {
        String danger = danger(level, centre);
        if (danger != null) {
            return new Survey(danger, List.of());
        }
        String inside = neighbour(level, centre, REACH_OUT);
        if (inside != null) {
            return new Survey("%s in the way".formatted(inside), List.of());
        }
        List<String> wishes = new ArrayList<>();
        String neighbour = neighbour(level, centre, REACH_OUT + AWAY);
        if (neighbour != null) {
            wishes.add("%s within %d blocks".formatted(neighbour, AWAY));
        }
        int columns = 0;
        int trees = 0;
        int water = 0;
        for (int x = -SURVEY; x <= SURVEY; x++) {
            for (int z = -SURVEY; z <= SURVEY; z++) {
                if (x * x + z * z > SURVEY * SURVEY) {
                    continue;
                }
                columns++;
                int top = ground(level, centre.getX() + x, centre.getZ() + z);
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1 - top > BUSH) {
                    trees++;
                } else if (!level.getBlockState(new BlockPos(centre.getX() + x, top, centre.getZ() + z)).getFluidState().isEmpty()) {
                    water++;
                }
            }
        }
        if (trees * CROWDED > columns) {
            wishes.add("%d%% under trees".formatted(100 * trees / columns));
        }
        if (water * CROWDED > columns) {
            wishes.add("%d%% under water".formatted(100 * water / columns));
        }
        return new Survey(null, wishes);
    }

    @Nullable
    private static String danger(ServerLevel level, BlockPos centre) {
        int reach = REACH_OUT + SAFE;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > reach * reach) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + x, centre.getZ() + z) - 1;
                for (int y = top - SAFE_BELOW; y <= top + SAFE_ABOVE; y++) {
                    BlockPos pos = new BlockPos(centre.getX() + x, y, centre.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    boolean underfoot = x * x + z * z <= REACH_OUT * REACH_OUT && UNDERFOOT.contains(state.getBlock());
                    if (state.getFluidState().is(FluidTags.LAVA) || DANGERS.contains(state.getBlock()) || underfoot) {
                        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String neighbour(ServerLevel level, BlockPos centre, int reach) {
        BoundingBox around = new BoundingBox(centre.getX() - reach, level.getMinBuildHeight(), centre.getZ() - reach, centre.getX() + reach,
                level.getMaxBuildHeight(), centre.getZ() + reach);
        for (int x = (centre.getX() - reach) >> 4; x <= (centre.getX() + reach) >> 4; x++) {
            for (int z = (centre.getZ() - reach) >> 4; z <= (centre.getZ() + reach) >> 4; z++) {
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(x, z), structure -> true)) {
                    BoundingBox box = start.getBoundingBox();
                    if (box.intersects(around) && box.maxY() >= centre.getY() - SAFE_BELOW) {
                        return String.valueOf(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure()));
                    }
                }
            }
        }
        return null;
    }

    private static void guard(ServerLevel level, Camp camp) {
        if (!level.isLoaded(camp.centre)) {
            return;
        }
        for (BoundingBox box : camp.boxes) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - EMBERS, box.minY() - EMBERS, box.minZ() - EMBERS, box.maxX() + EMBERS,
                    box.maxY() + EMBERS, box.maxZ() + EMBERS)) {
                if (level.getBlockState(pos).is(BlockTags.FIRE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    @Nullable
    private static String build(ServerLevel level, Camp camp) {
        List<BoundingBox> taken = camp.boxes;
        taken.clear();
        List<BlockPos> shifts = new ArrayList<>();
        for (int dx = -FIRE_SHIFT; dx <= FIRE_SHIFT; dx += FIRE_SHIFT / 2) {
            for (int dz = -FIRE_SHIFT; dz <= FIRE_SHIFT; dz += FIRE_SHIFT / 2) {
                shifts.add(camp.centre.offset(dx, 0, dz));
            }
        }
        shifts.sort(Comparator.comparingDouble(spot -> spot.distSqr(camp.centre)));
        Placed fire = null;
        for (BlockPos spot : shifts) {
            fire = place(level, camp, "fire", spot, Rotation.NONE, taken);
            if (fire != null) {
                break;
            }
        }
        if (fire == null) {
            return "no room for the fire";
        }
        camp.centre = fire.centre;
        List<BlockPos> seats = new ArrayList<>();
        BlockPos.betweenClosedStream(fire.box).filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG))
                .forEach(pos -> seats.add(pos.immutable()));
        List<JsonElement> households = households(level.getServer());
        long together = households.size();
        int slot = 0;
        for (JsonElement entry : households) {
            JsonObject household = entry.getAsJsonObject();
            String piece = household.get("piece").getAsString();
            StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
            double angle = camp.facing + 180 + 360.0 * slot++ / together;
            int radius = 4 + Math.max(template.getSize().getX(), template.getSize().getZ()) / 2 + GAP
                    + (household.has(APART) && household.get(APART).getAsBoolean() ? APART_BLOCKS : 0);
            Placed placed = null;
            for (int nudge : NUDGES) {
                for (int extra = 0; extra <= REACH && placed == null; extra += 2) {
                    Vec3 way = Vec3.directionFromRotation(0, (float) (angle + nudge));
                    BlockPos at = camp.centre.offset((int) Math.round(way.x * (radius + extra)), 0, (int) Math.round(way.z * (radius + extra)));
                    placed = place(level, camp, piece, at, facing(camp.centre, at), taken);
                }
                if (placed != null) {
                    break;
                }
            }
            if (placed == null) {
                if (household.has("people")) {
                    for (JsonElement person : household.getAsJsonArray("people")) {
                        BlockPos spot = seats.isEmpty() ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                camp.centre.offset((int) Math.round(Math.cos(Math.toRadians(angle)) * STANDBY), 0, (int) Math.round(Math.sin(Math.toRadians(angle)) * STANDBY)))
                                : seats.removeFirst().above();
                        double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
                        People.person(level, person.getAsJsonObject().get("person").getAsString(), Vec3.atBottomCenterOf(spot), (float) yaw, "standing");
                    }
                }
                LOGGER.info("the caravan found no room for its {}, so its people stay by the fire", piece);
                continue;
            }
            path(level, camp, placed, taken);
            if (household.has("people")) {
                for (JsonElement person : household.getAsJsonArray("people")) {
                    JsonObject who = person.getAsJsonObject();
                    boolean sits = who.get("at").getAsString().equals("fire") && !seats.isEmpty();
                    BlockPos spot = sits ? seats.removeFirst().above() : placed.front.above();
                    double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
                    String pose = who.has("pose") ? who.get("pose").getAsString() : sits ? "sitting" : "standing";
                    People.person(level, who.get("person").getAsString(), Vec3.atBottomCenterOf(spot), (float) yaw, pose);
                }
            }
        }
        return null;
    }

    private static List<JsonElement> households(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return HOUSEHOLDS.get().asList().stream()
                .filter(entry -> !entry.getAsJsonObject().has(JOINS) || progress.joined.contains(entry.getAsJsonObject().get(JOINS).getAsString())).toList();
    }

    private static Rotation facing(BlockPos centre, BlockPos at) {
        int dx = centre.getX() - at.getX();
        int dz = centre.getZ() - at.getZ();
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        }
        return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    @Nullable
    private static Placed place(ServerLevel level, Camp camp, String piece, BlockPos at, Rotation rotation, List<BoundingBox> taken) {
        StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
        BlockPos pivot = new BlockPos(template.getSize().getX() / 2, 0, template.getSize().getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).setIgnoreEntities(true)
                .setKnownShape(true);
        BlockPos origin = at.subtract(pivot);
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        if (taken.stream().anyMatch(box -> box.inflatedBy(1).intersects(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ()))) {
            return null;
        }
        List<Integer> tops = new ArrayList<>();
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
            for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                int top = ground(level, x, z);
                BlockState ground = level.getBlockState(new BlockPos(x, top, z));
                if (!ground.getFluidState().isEmpty() || level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 - top > BUSH) {
                    return null;
                }
                tops.add(top);
            }
        }
        tops.sort(Integer::compare);
        int y = tops.get(tops.size() / 2);
        if (tops.getLast() - y > STEEP || y - tops.getFirst() > STEEP) {
            return null;
        }
        BlockPos placedOrigin = origin.atY(y);
        BoundingBox box = template.getBoundingBox(settings, placedOrigin);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            remember(level, camp, pos);
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), y - FOUNDATION, box.minZ(), box.maxX(), Math.max(box.maxY(), y + BUSH), box.maxZ())) {
            if (growth(level.getBlockState(pos))) {
                remember(level, camp, pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                terrace(level, camp, x, z, y);
            }
        }
        template.placeInWorld(level, placedOrigin, placedOrigin, settings, level.getRandom(), FLAGS);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                foundation(level, camp, x, z, y, box.maxY());
            }
        }
        taken.add(box);
        Direction front = rotation.rotate(Direction.NORTH);
        BlockPos centre = at.atY(y);
        BlockPos door = centre.relative(front, template.getSize().getZ() / 2 + 1);
        int doorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door.getX(), door.getZ()) - 1;
        return new Placed(box, centre, door.atY(doorY));
    }

    private static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        while (growth(level.getBlockState(new BlockPos(x, top, z)))) {
            top--;
        }
        return top;
    }

    private static boolean growth(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static void terrace(ServerLevel level, Camp camp, int x, int z, int y) {
        int top = ground(level, x, z);
        if (top <= y) {
            return;
        }
        BlockState surface = level.getBlockState(new BlockPos(x, top, z));
        for (int height = y; height <= top; height++) {
            remember(level, camp, new BlockPos(x, height, z));
            level.setBlock(new BlockPos(x, height, z), height == y ? surface : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void foundation(ServerLevel level, Camp camp, int x, int z, int y, int top) {
        int lowest = Integer.MIN_VALUE;
        for (int height = y; height <= top; height++) {
            BlockPos pos = new BlockPos(x, height, z);
            if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).equals(camp.originals.get(pos))) {
                lowest = height;
                break;
            }
        }
        if (lowest == Integer.MIN_VALUE) {
            return;
        }
        for (int depth = 1; depth <= FOUNDATION; depth++) {
            BlockPos below = new BlockPos(x, lowest - depth, z);
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return;
            }
            remember(level, camp, below);
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), FLAGS);
        }
    }

    private static void path(ServerLevel level, Camp camp, Placed placed, List<BoundingBox> taken) {
        Vec3 from = Vec3.atCenterOf(placed.front);
        Vec3 to = Vec3.atCenterOf(camp.centre);
        double length = Math.sqrt(from.distanceToSqr(to.x, from.y, to.z));
        for (double travelled = 0; travelled < length - 3; travelled += 0.5) {
            int x = Mth.floor(from.x + (to.x - from.x) * travelled / length);
            int z = Mth.floor(from.z + (to.z - from.z) * travelled / length);
            if (taken.stream().anyMatch(box -> box.isInside(x, box.minY(), z))) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            BlockState ground = level.getBlockState(top);
            if (!ground.is(BlockTags.DIRT) || ground.is(Blocks.DIRT_PATH) || camp.originals.containsKey(top)) {
                continue;
            }
            remember(level, camp, top);
            remember(level, camp, top.above());
            if (level.getBlockState(top.above()).canBeReplaced()) {
                level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
        }
    }

    private static void remember(ServerLevel level, Camp camp, BlockPos pos) {
        camp.originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

}
