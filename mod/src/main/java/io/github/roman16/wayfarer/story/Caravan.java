package io.github.roman16.wayfarer.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Handover;
import io.github.roman16.wayfarer.Joining;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.TentPitch;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class Caravan {
    static final String NAME = "caravan";
    static final String KIND = "caravan";
    private static final int SEAT_REACH = 4;
    static final String SAVE = "wayfarer_caravan";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TENTS = "nomadictents";
    private static final String HORSEMAN = "horseman";
    private static final Map<UUID, Optional<BlockPos>> SMOKE_SENT = new HashMap<>();
    private static final int SMOKE_TICKS = 20;
    private static final ResourceLocation HORN = ResourceLocation.fromNamespaceAndPath(HORSEMAN, "copper_horn");
    private static final Bundled<JsonArray> HOUSEHOLDS = Bundled.json("/wayfarer/story.json", story -> story.getAsJsonArray("caravan"));
    private static final int ANGLE_STEP = 15;
    private static final int AROUND = 32;
    private static final int AWAY = 32;
    private static final int BUSH = 4;
    private static final int CLEAR = 64;
    private static final int CHUNKS = 2;
    private static final String FAR_CAMP = "far_camp";
    private static final String LENT = "wayfarer_lent";
    private static final String NEAR = "near";
    private static final int CROWDED = 2;
    private static final int CAMP_SAMPLE = 4;
    private static final int RISE = 4;
    private static final int WET_SHARE = 8;
    private static final Set<Block> DANGERS = Set.of(Blocks.BEE_NEST, Blocks.BEEHIVE, Blocks.FIRE, Blocks.MAGMA_BLOCK, Blocks.SOUL_FIRE);
    private static final Set<Block> UNDERFOOT = Set.of(Blocks.CACTUS, Blocks.POWDER_SNOW, Blocks.SWEET_BERRY_BUSH);
    private static final int EMBERS = 2;
    private static final int EXPOSED = 8;
    private static final int FARTHER = 16;
    private static final int FIRE_SHIFT = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int FOUNDATION = 2;
    private static final int GAP = 2;
    private static final int APART_BLOCKS = 8;
    private static final int STANDBY = 4;
    private static final String APART = "apart";
    private static final String JOINS = "joins";
    private static final String PLACE = "place";
    private static final String SITTING = "sitting";
    private static final String SITTING_ON_THE_GROUND = "sitting_crossed";
    private static final int BENCH_ASIDE = 2;
    private static final String UNLESS = "unless";
    private static final int HOLLOW = 8;
    private static final int LOW = 4;
    private static final int NEAR_WATER = 8;
    private static final int SNOWY = 64;
    private static final int BEYOND = 32;
    private static final int[] NUDGES = {0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180};
    private static final int REACH = 8;
    private static final int REACH_OUT = 16;
    private static final int WAY_OUT = 32;
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
    private static final int STEEP = 2;
    private static final int SAFE_DROP = 3;
    private static final int SURVEY = 16;
    private static final int TENT_NEAREST = 6;
    private static final int TENT_SIZE = 5;
    private static final int TENT_STEP = 2;
    private static final int TALLEST = 8;
    private static final int TRIES = 4;
    private static final int APPROACH_BLOCKS = 64;
    private static final int CLIFF_BLOCKS = 8;
    private static final int CLIFF_STEP = 4;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_caravan", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKS_PER_SECOND = 20;
    private static final double FREE_SIDE = 0.7;
    private static final String FED = "fed:";
    private static final Set<Block> TRODDEN = Set.of(Blocks.ANDESITE, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.DIRT_PATH, Blocks.GRAVEL,
            Blocks.MOSSY_COBBLESTONE, Blocks.PODZOL, Blocks.ROOTED_DIRT);
    @Nullable
    private static Search search;
    @Nullable
    private static Packing packing;
    private static long armed = -1;
    private static long quietUntil;
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

    public record Smoke(Optional<BlockPos> fire) implements CustomPacketPayload {
        public static final Type<Smoke> TYPE = new Type<>(Wayfarer.id("smoke"));
        public static final StreamCodec<ByteBuf, Smoke> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Smoke::fire,
                Smoke::new);

        @Override
        public Type<Smoke> type() {
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
        private final boolean around;
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

        private Search(ServerLevel level, BlockPos from, boolean around) {
            this.from = from;
            this.around = around;
            this.heights = new Unseen.Heights(level);
        }
    }

    record Camped(BlockPos centre, long day) {}

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
        private final List<Camped> camps = new ArrayList<>();
        private long evened = -1;
        private int evenings;
        private long welcomed = -1;
        private long seenOff = -1;
        @Nullable
        private UUID horse;
        private String keeper = "";
        private String turnedBack = "";
        private String lastGreeter = "";
        private final Map<String, Integer> greeted = new LinkedHashMap<>();
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
            camp.turnedBack = tag.getString("turnedBack");
            camp.lastGreeter = tag.getString("lastGreeter");
            CompoundTag greeted = tag.getCompound("greeted");
            greeted.getAllKeys().forEach(key -> camp.greeted.put(key, greeted.getInt(key)));
            camp.carried = tag.getCompound("carried");
            camp.evened = tag.getLong("evened");
            camp.evenings = tag.getInt("evenings");
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
            for (Tag entry : tag.getList("camps", Tag.TAG_COMPOUND)) {
                CompoundTag camped = (CompoundTag) entry;
                camp.camps.add(new Camped(BlockPos.of(camped.getLong("centre")), camped.getLong("day")));
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
            tag.putString("turnedBack", turnedBack);
            tag.putString("lastGreeter", lastGreeter);
            CompoundTag greeted = new CompoundTag();
            this.greeted.forEach(greeted::putInt);
            tag.put("greeted", greeted);
            tag.put("carried", carried);
            tag.putLong("evened", evened);
            tag.putInt("evenings", evenings);
            tag.putLong("welcomed", welcomed);
            tag.putLong("seenOff", seenOff);
            tag.put("originals", states(originals));
            tag.put("laid", states(laid));
            ListTag boxes = new ListTag();
            this.boxes.forEach(box -> boxes.add(new IntArrayTag(new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})));
            tag.put("boxes", boxes);
            ListTag camps = new ListTag();
            this.camps.forEach(camped -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("centre", camped.centre().asLong());
                entry.putLong("day", camped.day());
                camps.add(entry);
            });
            tag.put("camps", camps);
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
            return left == null || level.getBlockState(pos).is(left.getBlock());
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
        unbind(horse);
    }

    private static void unbind(AbstractHorse horse) {
        if (!ModList.get().isLoaded(HORSEMAN)) {
            return;
        }
        try {
            Arrays.stream(horse.getClass().getMethods()).filter(method -> method.getName().equals("setHorsemanBoundData")).findFirst()
                    .orElseThrow().invoke(horse, (Object) null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error("unbinding failed: Horseman kept {} bound to a horn", horse.getUUID(), exception);
        }
    }

    @SubscribeEvent
    public static void onHorn(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSecondaryUseActive() && HORN.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()))
                && event.getTarget() instanceof AbstractHorse horse && horse.getUUID().equals(horse(player.server))) {
            player.displayClientMessage(Component.translatable("wayfarer.horn.caravan", horse.getDisplayName()), true);
            event.setCanceled(true);
        }
    }

    private static BlockPos pasture(MinecraftServer server, Camp camp) {
        return People.find(server, camp.keeper).map(Entity::blockPosition).orElse(camp.centre);
    }

    static Optional<BlockPos> grazing(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.horse != null && camp.carried.isEmpty() ? Optional.of(camp.centre) : Optional.empty();
    }

    static boolean takeAlong(ServerLevel level) {
        Camp camp = Camp.of(level.getServer());
        if (camp.horse != null && camp.carried.isEmpty() && level.getEntity(camp.horse) instanceof AbstractHorse horse) {
            stow(camp, horse);
        }
        return !camp.carried.isEmpty();
    }

    static void adoptFrom(MinecraftServer server, CompoundTag saved) {
        Camp from = Camp.load(saved, server.registryAccess());
        Camp camp = Camp.of(server);
        camp.horse = from.horse;
        camp.keeper = from.keeper;
        camp.carried = from.carried;
        camp.setDirty();
    }

    private static void carry(ServerLevel level, Camp camp) {
        if (camp.horse != null && level.getEntity(camp.horse) instanceof AbstractHorse horse && !horse.isVehicle() && !horse.isLeashed()
                && horse.position().closerThan(Vec3.atCenterOf(camp.centre), RING + GRAZE)) {
            stow(camp, horse);
        }
    }

    private static void stow(Camp camp, AbstractHorse horse) {
        camp.carried = horse.saveWithoutId(new CompoundTag());
        camp.carried.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(horse.getType()).toString());
        horse.discard();
        camp.setDirty();
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
            Camp camp = Camp.of(player.server);
            camp.turnedBack = player.getGameProfile().getName();
            camp.setDirty();
            player.server.getPlayerList().getPlayers().forEach(member -> member.sendSystemMessage(Component.translatable("wayfarer.caravan.chosen", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY)));
            headIn(player.server);
        }
    }

    static boolean homeward(ServerPlayer player) {
        return heading(player) && !Clock.camped(player) && !Wards.within(player) && !Memories.dreaming(player) && !beside(player)
                && Engine.tracked(player).map(active -> active.step().kind().equals(KIND) || !Steps.entered(active) && !Steps.ownNight(active.step())).orElse(true);
    }

    private static boolean beside(ServerPlayer player) {
        int beside = Balance.get().story().besideBlocks();
        return Engine.tracked(player).filter(active -> !active.step().kind().equals(KIND)).flatMap(Steps::goal)
                .flatMap(goal -> Places.aim(Progress.of(player.server), player, goal))
                .filter(at -> Steps.horizontal(player.blockPosition(), at) <= (double) beside * beside).isPresent();
    }

    static void walkedOn(MinecraftServer server) {
        Camp camp = Camp.of(server);
        if (!camp.turnedBack.isEmpty()) {
            camp.turnedBack = "";
            camp.setDirty();
            Journal.syncAll(server);
        }
    }

    static Optional<String> turnedBack(MinecraftServer server) {
        String name = Camp.of(server).turnedBack;
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    static boolean canHeadIn(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.ever && !camp.heading && camp.phase == Phase.GONE;
    }

    static List<BoundingBox> boxes(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.phase == Phase.STANDING || camp.phase == Phase.PACKING ? List.copyOf(camp.boxes) : List.of();
    }

    static boolean campedToday(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.phase == Phase.STANDING && camp.day == Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY);
    }

    public static boolean bench(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD && level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG)
                && fire(level.getServer()).filter(fire -> Math.abs(fire.getX() - pos.getX()) <= SEAT_REACH && Math.abs(fire.getZ() - pos.getZ()) <= SEAT_REACH
                        && Math.abs(fire.getY() - pos.getY()) <= 1).isPresent()
                && level.getEntities((Entity) null, new AABB(pos.above()), entity -> !(entity instanceof Player)).isEmpty();
    }

    static Optional<Places.Seat> fireSeat(MinecraftServer server, int fromEnd) {
        ServerLevel level = Engine.overworld(server);
        return Places.place(server, NAME).filter(level::isLoaded).flatMap(fire -> {
            List<BlockPos> logs = BlockPos.betweenClosedStream(fire.offset(-SEAT_REACH, -1, -SEAT_REACH), fire.offset(SEAT_REACH, 1, SEAT_REACH))
                    .filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG) && level.getBlockState(pos.above()).isAir()).map(BlockPos::immutable).toList();
            if (logs.size() < fromEnd) {
                return Optional.empty();
            }
            Vec3 at = Vec3.atBottomCenterOf(logs.get(logs.size() - fromEnd).above());
            return Optional.of(new Places.Seat(at, Frost.yaw(Vec3.atBottomCenterOf(fire).subtract(at).multiply(1, 0, 1))));
        });
    }

    static List<Camped> camps(MinecraftServer server) {
        return List.copyOf(Camp.of(server).camps);
    }

    static BlockPos last(MinecraftServer server) {
        Camp camp = Camp.of(server);
        return camp.ever ? camp.centre : Engine.overworld(server).getSharedSpawnPos();
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

    public static boolean lends(ServerPlayer player, BlockPos bed) {
        return player.level().dimension() == Level.OVERWORLD && boxes(player.server).stream().anyMatch(box -> box.isInside(bed)) && !TentPitch.owns(player);
    }

    static boolean near(MinecraftServer server) {
        int reach = Balance.get().stillness().radiusBlocks();
        return fire(server).map(fire -> Engine.players(server).stream().anyMatch(player -> player.blockPosition().closerThan(fire, reach))).orElse(false);
    }

    static boolean spareBed(MinecraftServer server) {
        return !spareBeds(server).isEmpty();
    }

    public static List<BlockPos> spareBeds(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        return boxes(server).stream().flatMap(box -> BlockPos.betweenClosedStream(box).filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.BEDS)
                && level.getBlockState(pos).getValue(BedBlock.PART) == BedPart.HEAD && !level.getBlockState(pos).getValue(BedBlock.OCCUPIED)).map(BlockPos::immutable)).toList();
    }

    public static Optional<BlockPos> lentBed(ServerPlayer player) {
        return TentPitch.owns(player) || player.level().dimension() != Level.OVERWORLD ? Optional.empty()
                : standing(player.server).flatMap(camp -> spareBeds(player.server).stream().min(Comparator.comparingDouble(bed -> bed.distSqr(player.blockPosition()))));
    }

    public static boolean met(MinecraftServer server) {
        return Progress.of(server).place(NAME).isPresent();
    }

    public static Optional<BlockPos> fire(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        return standing(server).flatMap(centre -> Stream.of(centre, centre.above())
                .filter(pos -> level.isLoaded(pos) && CampfireBlock.isLitCampfire(level.getBlockState(pos))).findFirst());
    }

    public static boolean within(MinecraftServer server, Level level, Vec3 at) {
        int circle = Balance.get().fire().circleBlocks();
        return level.dimension() == Level.OVERWORLD && fire(server).filter(camp -> camp.getCenter().distanceToSqr(at) <= (double) circle * circle).isPresent();
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
        if (server.getTickCount() % SMOKE_TICKS == 0) {
            smoke(server, met(server) ? standing(server) : Optional.empty());
        }
        switch (camp.phase) {
            case GONE -> {
                if (!camp.heading && camp.ever && !Journey.over(server) && !Steps.holdsNight(server)
                        && Math.floorMod(level.getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().runFrom())) {
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
                    camp.turnedBack = "";
                    camp.setDirty();
                    ARRIVED.clear();
                } else {
                    if (camp.heading && armed != today) {
                        armed = today;
                        Clock.caravan(level, camp.centre);
                    }
                    if (arrived(server, level, camp) && camp.welcomed != camp.day && !Talk.busy() && (Clock.nightfall()
                            || Math.floorMod(level.getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().runFrom()))) {
                        camp.welcomed = camp.day;
                        camp.setDirty();
                        Record.event(server, "camp", null, line -> line.addProperty("day", camp.day));
                        Engine.retell(server);
                        greet(server, camp.day, "welcomes", returned(server) ? Story.Person::returns : Story.Person::welcomes);
                        quietUntil = level.getGameTime() + (long) Balance.get().talk().eveningGapSeconds() * TICKS_PER_SECOND;
                    }
                    evening(server, level, camp, today);
                }
            }
            case PACKING -> {
                long today = Math.floorDiv(level.getDayTime(), Clock.DAY);
                if (camp.seenOff != today && !Talk.busy() && arrived(server, level, camp)) {
                    camp.seenOff = today;
                    camp.setDirty();
                    greet(server, today, "mornings", Story.Person::mornings);
                }
                if (!Journey.over(server) && (packing == null || !packing.loading())
                        && Math.floorMod(level.getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().runFrom())) {
                    stay(server, camp, today);
                } else {
                    pack(server, level, camp);
                }
            }
        }
    }

    private static void evening(MinecraftServer server, ServerLevel level, Camp camp, long today) {
        Balance.Talk talk = Balance.get().talk();
        if (!Clock.nightfall() || camp.welcomed != camp.day || Nights.hard(server) || Hold.active(server)) {
            return;
        }
        feed(server, camp, today);
        if (Talk.busy() || level.getGameTime() < quietUntil || Engine.players(server).stream().noneMatch(Fire::resting)) {
            return;
        }
        if (camp.evened != today) {
            camp.evened = today;
            camp.evenings = 0;
        }
        if (camp.evenings >= talk.eveningLines()) {
            return;
        }
        camp.evenings++;
        camp.setDirty();
        quietUntil = level.getGameTime() + (long) talk.eveningGapSeconds() * TICKS_PER_SECOND;
        greet(server, today, "evenings", Story.Person::evenings);
    }

    private static void feed(MinecraftServer server, Camp camp, long today) {
        for (ServerPlayer player : Engine.players(server)) {
            String key = FED + player.getStringUUID();
            if (Fire.resting(player) && camp.greeted.getOrDefault(key, -1) != (int) today) {
                camp.greeted.put(key, (int) today);
                camp.setDirty();
                ItemStack bowl = new ItemStack(Items.MUSHROOM_STEW);
                CustomData.update(DataComponents.CUSTOM_DATA, bowl, tag -> tag.putBoolean(LENT, true));
                Notices.given(player, List.of(bowl));
                Handover.give(player, bowl.copy());
                Talk.tell(player, Component.translatable("wayfarer.caravan.bowl"));
            }
        }
    }

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish event) {
        if (event.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(LENT) && event.getResultStack().is(Items.BOWL)) {
            event.setResultStack(ItemStack.EMPTY);
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

    private static boolean returned(MinecraftServer server) {
        return Engine.players(server).stream().anyMatch(player -> player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH)) < Clock.DAY);
    }

    private static void greet(MinecraftServer server, long day, String kind, Function<Story.Person, List<String>> said) {
        List<String> awake = new ArrayList<>();
        for (JsonElement household : households(server)) {
            for (JsonObject person : people(server, household.getAsJsonObject())) {
                String id = person.get("person").getAsString();
                if (Progress.of(server).met(id) && !said.apply(Story.get().person(id)).isEmpty() && People.find(server, id).filter(body -> seen(server, body)).isPresent()) {
                    awake.add(id);
                }
            }
        }
        if (awake.isEmpty()) {
            return;
        }
        Camp camp = Camp.of(server);
        List<String> others = awake.size() > 1 ? awake.stream().filter(id -> !id.equals(camp.lastGreeter)).toList() : awake;
        String by = others.get(Math.floorMod(day, others.size()));
        List<String> lines = said.apply(Story.get().person(by));
        int told = camp.greeted.merge(kind + ":" + by, 1, Integer::sum) - 1;
        camp.lastGreeter = by;
        camp.setDirty();
        Talk.say(Talk.ROAD, People.find(server, by).map(Entity::position).orElse(null), new Story.Line(by, lines.get(told % lines.size())));
    }

    private static boolean seen(MinecraftServer server, Entity body) {
        int reach = Balance.get().talk().reachBlocks();
        return Engine.players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) reach * reach
                && Sight.sees(player, body));
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
        int nearest = nearest(server);
        Optional<BlockPos> near = placed(server);
        BlockPos origin = near.orElse(from);
        double walked = Math.sqrt(from.distSqr(before.atY(from.getY())));
        int day = Balance.get().caravan().dayBlocks();
        if (near.isEmpty() && camp.ever && walked - day > nearest) {
            Vec3 way = Vec3.atLowerCornerOf(from.subtract(before.atY(from.getY()))).multiply(1, 0, 1).normalize();
            origin = BlockPos.containing(Vec3.atLowerCornerOf(from).subtract(way.scale(Math.min(walked - day, day))));
        }
        camp.phase = Phase.SEARCHING;
        camp.centre = origin;
        camp.setDirty();
        search = new Search(level, origin, !origin.equals(from));
    }

    private static int nearest(MinecraftServer server) {
        return (server.getPlayerList().getViewDistance() + CHUNKS + 2) * 16;
    }

    private static Optional<BlockPos> placed(MinecraftServer server) {
        return Engine.active(server).stream().filter(active -> active.step().kind().equals(KIND) && active.step().spec().has(NEAR))
                .map(active -> Engine.at(server, active.step().text(NEAR))).findFirst();
    }

    private static void search(MinecraftServer server, ServerLevel level, Camp camp) {
        if (search.pending == null) {
            int view = server.getPlayerList().getViewDistance();
            boolean around = search.around;
            List<Unseen.Viewer> viewers = Unseen.viewers(level);
            Unseen.Heights heights = search.heights;
            List<BlockPos> rejected = List.copyOf(search.rejected);
            BlockPos from = search.from;
            float facing = camp.facing;
            boolean anywhereBeyond = search.seenPasses >= SEEN_PASSES;
            int widest = Math.min(HALF_TURN, WIDEST_AHEAD + WIDER * search.seenPasses);
            int span = Math.min(FARTHEST, SEARCH_SPAN * (1 + search.seenPasses));
            search.pending = CompletableFuture.supplyAsync(() -> {
                int nearest = around ? 0 : (view + CHUNKS + 2) * 16;
                List<Spot> spots = new ArrayList<>();
                for (int distance = nearest; distance <= nearest + span; distance += SEARCH_STEP) {
                    for (int angle : angles(HALF_TURN)) {
                        Vec3 way = Vec3.directionFromRotation(0, facing + angle);
                        BlockPos centre = BlockPos.containing(from.getX() + way.x * distance, 0, from.getZ() + way.z * distance);
                        boolean beyond = Math.abs(angle) > widest;
                        if (beyond && snowy(heights, centre) || rejected.stream().anyMatch(spot -> spot.distSqr(centre.atY(spot.getY())) < AWAY * AWAY * 4)
                                || misfit(heights, centre) != null) {
                            continue;
                        }
                        spots.add(new Spot(centre.atY(heights.surface(centre.getX(), centre.getZ())), distance,
                                score(heights, centre) - (distance - nearest) / FARTHER - (beyond ? BEYOND : 0)));
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

    private static void far(MinecraftServer server, BlockPos centre) {
        int far = Balance.get().caravan().farBlocks();
        for (ServerPlayer player : Engine.players(server)) {
            if (player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(centre.atY(player.getBlockY())) > (double) far * far
                    && Firsts.first(player, FAR_CAMP)) {
                Talk.tell(player, Component.translatable("wayfarer.first." + FAR_CAMP));
            }
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
        List<String> wishes = new ArrayList<>(survey.wishes());
        List<Runnable> seating = new ArrayList<>();
        String hard = survey.hard() == null ? build(level, camp, wishes, seating) : survey.hard();
        level.getChunkSource().removeRegionTicket(TICKET, search.ticket, CHUNKS, search.ticket);
        if (hard == null && (search.settling || wishes.isEmpty())) {
            camp.originals.keySet().forEach(pos -> camp.laid.put(pos, level.getBlockState(pos)));
            camp.phase = Phase.STANDING;
            camp.ever = true;
            camp.day = Math.floorDiv(level.getDayTime(), Clock.DAY);
            camp.camps.add(new Camped(camp.centre, camp.day));
            camp.setDirty();
            search = null;
            Progress.of(server).place(NAME, camp.centre);
            Journal.syncAll(server);
            if (camp.heading) {
                Talk.notice(Component.translatable("wayfarer.caravan.evening"));
                far(server, camp.centre);
            }
            if (ModList.get().isLoaded(TENTS)) {
                TentPitch.bringBack(server, new Joining.Place(Level.OVERWORLD, camp.centre));
            }
            release(level, camp);
            seating.forEach(Runnable::run);
            LOGGER.info("the caravan made camp at {}{}", camp.centre, wishes.isEmpty() ? "" : ", though " + String.join(", ", wishes));
            return;
        }
        restore(level, camp);
        LOGGER.info("the caravan will not camp at {}: {}", camp.centre, hard != null ? hard : String.join(", ", wishes));
        if (hard == null && !search.settling) {
            search.fallbacks.add(new Fallback(camp.centre, List.copyOf(wishes)));
        } else {
            search.fallbacks.removeIf(fallback -> fallback.centre().equals(camp.centre));
        }
        search.settling = false;
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

    private static void stay(MinecraftServer server, Camp camp, long today) {
        packing = null;
        camp.phase = Phase.STANDING;
        camp.day = today;
        camp.welcomed = today;
        camp.heading = true;
        camp.setDirty();
        Talk.notice(Component.translatable("wayfarer.caravan.stayed"));
        Journal.syncAll(server);
        LOGGER.info("the caravan stays a second night at {}", camp.centre);
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
            for (JsonObject person : people(server, household.getAsJsonObject())) {
                People.find(server, person.get("person").getAsString()).ifPresent(Entity::discard);
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

    private static void smoke(MinecraftServer server, Optional<BlockPos> fire) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<BlockPos> shown = player.level().dimension() == Level.OVERWORLD ? fire : Optional.empty();
            if (!shown.equals(SMOKE_SENT.get(player.getUUID()))) {
                SMOKE_SENT.put(player.getUUID(), shown);
                PacketDistributor.sendToPlayer(player, new Smoke(shown));
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SMOKE_SENT.remove(player.getUUID());
        }
        if (event.getEntity() instanceof LightningBolt bolt && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Camp camp = Camp.of(level.getServer());
            if (!camp.boxes.isEmpty() && bolt.blockPosition().distSqr(camp.centre.atY(bolt.getBlockY())) < (REACH_OUT + SAFE) * (REACH_OUT + SAFE)) {
                event.setCanceled(true);
            }
        }
    }

    @Nullable
    private static String misfit(Unseen.Heights heights, BlockPos centre) {
        if (heights.wet(centre.getX(), centre.getZ())) {
            return "in water";
        }
        return Sites.open(heights.biome(centre.getX(), heights.surface(centre.getX(), centre.getZ()), centre.getZ())) ? null : "under trees";
    }

    private static boolean snowy(Unseen.Heights heights, BlockPos centre) {
        int ground = heights.surface(centre.getX(), centre.getZ());
        return Unseen.snowy(heights.biome(centre.getX(), ground, centre.getZ()), centre.atY(ground));
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
        int score = (water ? NEAR_WATER : 0) - (snowy(heights, centre) ? SNOWY : 0);
        if (ground * around.size() < total - LOW * around.size()) {
            score -= HOLLOW;
        }
        if (ground > high + LOW) {
            score -= EXPOSED;
        }
        int flatLow = Integer.MAX_VALUE;
        int flatHigh = Integer.MIN_VALUE;
        int soaked = 0;
        for (int dx = -REACH_OUT; dx <= REACH_OUT; dx += CAMP_SAMPLE) {
            for (int dz = -REACH_OUT; dz <= REACH_OUT; dz += CAMP_SAMPLE) {
                if (dx * dx + dz * dz > REACH_OUT * REACH_OUT) {
                    continue;
                }
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                if (heights.wet(x, z)) {
                    soaked++;
                } else if (dx * dx + dz * dz <= RING * RING) {
                    int y = heights.surface(x, z);
                    flatLow = Math.min(flatLow, y);
                    flatHigh = Math.max(flatHigh, y);
                }
            }
        }
        int rise = flatHigh < flatLow ? 0 : flatHigh - flatLow;
        return score - rise * Math.max(1, rise - RISE) - soaked * NEAR_WATER;
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
        int floor = ground(level, centre.getX(), centre.getZ());
        if (roofed(level, centre.atY(floor))) {
            return new Survey("rock over the fire", List.of());
        }
        String neighbour = neighbour(level, centre, CLEAR);
        if (neighbour != null) {
            return new Survey("%s within %d blocks".formatted(neighbour, CLEAR), List.of());
        }
        long players = Math.max(1, level.getServer().getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator()).count());
        int room = tentRoom(level, centre);
        if (room < players) {
            return new Survey("room for %d of %d tents".formatted(room, players), List.of());
        }
        if (!walkable(level, centre)) {
            return new Survey("no way to walk %d blocks out from the fire".formatted(WAY_OUT), List.of());
        }
        List<String> wishes = new ArrayList<>();
        cliff(level, centre.atY(floor)).ifPresent(wishes::add);
        int columns = 0;
        int trees = 0;
        int water = 0;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = -SURVEY; x <= SURVEY; x++) {
            for (int z = -SURVEY; z <= SURVEY; z++) {
                if (x * x + z * z > SURVEY * SURVEY) {
                    continue;
                }
                columns++;
                int top = ground(level, centre.getX() + x, centre.getZ() + z);
                if (Ground.crown(level, centre.getX() + x, centre.getZ() + z) - top > BUSH) {
                    trees++;
                } else if (!level.getBlockState(new BlockPos(centre.getX() + x, top, centre.getZ() + z)).getFluidState().isEmpty()) {
                    water++;
                } else if (x * x + z * z <= RING * RING) {
                    low = Math.min(low, top);
                    high = Math.max(high, top);
                }
            }
        }
        if (trees * CROWDED > columns) {
            wishes.add("%d%% under trees".formatted(100 * trees / columns));
        }
        if (water * WET_SHARE > columns) {
            wishes.add("%d%% under water".formatted(100 * water / columns));
        }
        if (high - low > RISE) {
            wishes.add("a rise of %d blocks across the fire's circle".formatted(high - low));
        }
        return new Survey(null, wishes);
    }

    private static int tentRoom(ServerLevel level, BlockPos centre) {
        List<BlockPos> taken = new ArrayList<>();
        for (int x = -SURVEY; x <= SURVEY; x += TENT_STEP) {
            for (int z = -SURVEY; z <= SURVEY; z += TENT_STEP) {
                int far = x * x + z * z;
                BlockPos spot = centre.offset(x, 0, z);
                if (far < TENT_NEAREST * TENT_NEAREST || far > SURVEY * SURVEY
                        || taken.stream().anyMatch(other -> Math.abs(other.getX() - spot.getX()) < TENT_SIZE && Math.abs(other.getZ() - spot.getZ()) < TENT_SIZE)) {
                    continue;
                }
                if (flat(level, spot)) {
                    taken.add(spot);
                }
            }
        }
        return taken.size();
    }

    private static boolean flat(ServerLevel level, BlockPos spot) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = -TENT_SIZE / 2; dx <= TENT_SIZE / 2; dx++) {
            for (int dz = -TENT_SIZE / 2; dz <= TENT_SIZE / 2; dz++) {
                int x = spot.getX() + dx;
                int z = spot.getZ() + dz;
                int top = ground(level, x, z);
                if (!level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty() || Ground.crown(level, x, z) - top > BUSH) {
                    return false;
                }
                low = Math.min(low, top);
                high = Math.max(high, top);
            }
        }
        return high - low <= 1;
    }

    private static boolean walkable(ServerLevel level, BlockPos centre) {
        Map<Long, Integer> tops = new HashMap<>();
        ToIntFunction<BlockPos> top = column -> tops.computeIfAbsent(column.asLong(), key -> ground(level, column.getX(), column.getZ()));
        Set<BlockPos> seen = new HashSet<>(Set.of(centre.atY(0)));
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>(seen);
        while (!frontier.isEmpty()) {
            BlockPos at = frontier.poll();
            if (at.distSqr(centre.atY(0)) >= (double) WAY_OUT * WAY_OUT) {
                return true;
            }
            int here = top.applyAsInt(at);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = at.relative(side);
                if (seen.contains(next)) {
                    continue;
                }
                if (!level.isLoaded(next.atY(here))) {
                    return true;
                }
                int there = top.applyAsInt(next);
                if (there - here <= 1 && here - there <= SAFE_DROP
                        && level.getBlockState(new BlockPos(next.getX(), there, next.getZ())).getFluidState().isEmpty()) {
                    seen.add(next);
                    frontier.add(next);
                }
            }
        }
        return false;
    }

    private static boolean roofed(ServerLevel level, BlockPos ground) {
        int sky = Ground.sky(level, ground.getX(), ground.getZ()) + 1;
        for (int y = ground.getY() + 1; y < sky; y++) {
            BlockState state = level.getBlockState(ground.atY(y));
            if (!state.isAir() && !growth(state) && state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> cliff(ServerLevel level, BlockPos ground) {
        Optional<ServerPlayer> team = Engine.players(level.getServer()).stream().filter(player -> player.level() == level)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(Vec3.atCenterOf(ground))));
        if (team.isEmpty()) {
            return Optional.empty();
        }
        Vec3 way = Vec3.atCenterOf(ground).subtract(team.get().position()).multiply(1, 0, 1);
        double length = Math.min(way.length(), APPROACH_BLOCKS);
        if (length < CLIFF_STEP) {
            return Optional.empty();
        }
        Vec3 back = way.normalize().scale(-1);
        int before = ground.getY();
        for (double out = CLIFF_STEP; out <= length; out += CLIFF_STEP) {
            int x = ground.getX() + (int) Math.round(back.x * out);
            int z = ground.getZ() + (int) Math.round(back.z * out);
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                break;
            }
            int at = ground(level, x, z);
            if (Math.abs(at - before) > CLIFF_BLOCKS) {
                return Optional.of("a cliff of %d blocks on the way in".formatted(Math.abs(at - before)));
            }
            before = at;
        }
        return Optional.empty();
    }

    @Nullable
    private static String danger(ServerLevel level, BlockPos centre) {
        int reach = REACH_OUT + SAFE;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > reach * reach) {
                    continue;
                }
                int top = Ground.crown(level, centre.getX() + x, centre.getZ() + z);
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
        camp.laid.forEach((pos, laid) -> {
            if (laid.is(BlockTags.CAMPFIRES) && level.isLoaded(pos) && !CampfireBlock.isLitCampfire(level.getBlockState(pos))) {
                level.setBlock(pos, laid.setValue(CampfireBlock.LIT, true), FLAGS);
                LOGGER.info("the caravan's fire at {} was out; it burns again", pos);
            }
        });
    }

    @Nullable
    private static String build(ServerLevel level, Camp camp, List<String> wishes, List<Runnable> seating) {
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
        List<BlockPos> seats = seats(BlockPos.betweenClosedStream(fire.box).filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG))
                .map(BlockPos::immutable).toList(), fire.centre, camp.facing);
        List<JsonElement> households = households(level.getServer());
        long together = households.size();
        int slot = 0;
        for (JsonElement entry : households) {
            pitch(level, camp, entry.getAsJsonObject(), camp.facing + 180 + 360.0 * slot++ / together, seats, taken, wishes, seating);
        }
        return null;
    }

    private static List<BlockPos> seats(List<BlockPos> logs, BlockPos fire, float facing) {
        Vec3 front = Vec3.directionFromRotation(0, facing);
        List<BlockPos> seats = new ArrayList<>(logs);
        seats.stream().max(Comparator.comparingDouble(seat -> away(seat, fire).dot(front))).map(free -> away(free, fire))
                .ifPresent(side -> seats.removeIf(seat -> away(seat, fire).dot(side) > FREE_SIDE));
        return seats;
    }

    private static Vec3 away(BlockPos seat, BlockPos fire) {
        return Vec3.atLowerCornerOf(seat.subtract(fire)).multiply(1, 0, 1).normalize();
    }

    private static void pitch(ServerLevel level, Camp camp, JsonObject household, double angle, List<BlockPos> seats, List<BoundingBox> taken, List<String> wishes,
            List<Runnable> seating) {
        String piece = household.get("piece").getAsString();
        StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "caravan/" + piece)).orElseThrow();
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
            for (JsonObject person : people(level.getServer(), household)) {
                boolean sits = !seats.isEmpty();
                BlockPos spot = sits ? seats.removeFirst().above() : Ground.above(level, camp.centre.offset((int) Math.round(Math.cos(Math.toRadians(angle)) * STANDBY), 0, (int) Math.round(Math.sin(Math.toRadians(angle)) * STANDBY)));
                double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
                String pose = sits ? SITTING : "standing";
                seating.add(() -> People.place(level, person.get("person").getAsString(), Vec3.atBottomCenterOf(spot), (float) yaw, pose));
            }
            landmark(level, household, camp.centre, camp.centre.above());
            wishes.add("no room for its " + piece);
            return;
        }
        path(level, camp, placed, taken);
        landmark(level, household, placed.centre, placed.front.above());
        for (JsonObject who : people(level.getServer(), household)) {
            boolean sits = who.get("at").getAsString().equals("fire") && !seats.isEmpty();
            BlockPos spot = sits ? seats.removeFirst().above() : placed.front.above();
            String pose = who.has("pose") ? who.get("pose").getAsString() : sits ? SITTING : "standing";
            if (!sits && pose.equals(SITTING)) {
                Optional<BlockPos> bench = bench(level, camp, placed, taken);
                spot = bench.map(BlockPos::above).orElse(spot);
                pose = bench.isPresent() ? SITTING : SITTING_ON_THE_GROUND;
            }
            double yaw = Math.toDegrees(Math.atan2(camp.centre.getZ() - spot.getZ(), camp.centre.getX() - spot.getX())) - 90;
            BlockPos at = spot;
            String posed = pose;
            seating.add(() -> People.place(level, who.get("person").getAsString(), Vec3.atBottomCenterOf(at), (float) yaw, posed));
        }
    }

    static void join(MinecraftServer server, String person) {
        Camp camp = Camp.of(server);
        ServerLevel level = Engine.overworld(server);
        if (camp.phase != Phase.STANDING || !level.isLoaded(camp.centre)) {
            return;
        }
        households(server).stream().map(JsonElement::getAsJsonObject).filter(household -> household.has(JOINS) && household.get(JOINS).getAsString().equals(person))
                .findFirst().ifPresent(household -> {
                    Set<BlockPos> before = new HashSet<>(camp.originals.keySet());
                    List<BlockPos> seats = seats(BlockPos.betweenClosedStream(camp.centre.offset(-SEAT_REACH, -1, -SEAT_REACH),
                            camp.centre.offset(SEAT_REACH, 1, SEAT_REACH)).filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG)
                                    && level.getEntities((Entity) null, new AABB(pos.above()), entity -> !(entity instanceof ServerPlayer)).isEmpty())
                            .map(BlockPos::immutable).toList(), camp.centre, camp.facing);
                    List<Runnable> seating = new ArrayList<>();
                    pitch(level, camp, household, camp.facing + 180, seats, camp.boxes, new ArrayList<>(), seating);
                    seating.forEach(Runnable::run);
                    camp.originals.keySet().stream().filter(pos -> !before.contains(pos)).forEach(pos -> camp.laid.put(pos, level.getBlockState(pos)));
                    camp.setDirty();
                    LOGGER.info("{}'s household joins the standing camp at {}", person, camp.centre);
                });
    }

    private static void landmark(ServerLevel level, JsonObject household, BlockPos at, BlockPos seat) {
        if (!household.has(PLACE)) {
            return;
        }
        Progress progress = Progress.of(level.getServer());
        String place = household.get(PLACE).getAsString();
        progress.place(place, at);
        progress.place("seat_" + place, seat);
        progress.yaws.put("seat_" + place, Frost.yaw(Vec3.atLowerCornerOf(at.subtract(seat)).multiply(1, 0, 1)));
        progress.setDirty();
    }

    private static List<JsonObject> people(MinecraftServer server, JsonObject household) {
        if (!household.has("people")) {
            return List.of();
        }
        Progress progress = Progress.of(server);
        return household.getAsJsonArray("people").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(who -> !who.has(UNLESS) || !Engine.chose(progress, who.get(UNLESS).getAsString())).toList();
    }

    static boolean with(MinecraftServer server, String person) {
        return households(server).stream().anyMatch(household -> people(server, household.getAsJsonObject()).stream()
                .anyMatch(who -> who.get("person").getAsString().equals(person)));
    }

    static JsonArray households() {
        return HOUSEHOLDS.get();
    }

    private static List<JsonElement> households(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return HOUSEHOLDS.get().asList().stream()
                .filter(entry -> !entry.getAsJsonObject().has(JOINS) || progress.joined.contains(entry.getAsJsonObject().get(JOINS).getAsString()))
                .filter(entry -> !entry.getAsJsonObject().has(UNLESS) || !Engine.chose(progress, entry.getAsJsonObject().get(UNLESS).getAsString())).toList();
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
                if (!ground.getFluidState().isEmpty() || Ground.crown(level, x, z) - top > BUSH) {
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
        int doorY = Ground.land(level, door.getX(), door.getZ());
        return new Placed(box, centre, door.atY(doorY));
    }

    private static int ground(ServerLevel level, int x, int z) {
        int top = Ground.land(level, x, z);
        while (growth(level.getBlockState(new BlockPos(x, top, z)))) {
            top--;
        }
        return top;
    }

    private static boolean growth(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM) || !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty();
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

    private static BlockState soil(ServerLevel level, int x, int z) {
        BlockPos ground = new BlockPos(x, ground(level, x, z), z);
        BlockState state = level.getBlockState(ground);
        return state.isCollisionShapeFullBlock(level, ground) && state.getFluidState().isEmpty() && !state.hasBlockEntity() ? state : Blocks.DIRT.defaultBlockState();
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
            level.setBlock(below, soil(level, x, z), FLAGS);
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
            BlockPos top = new BlockPos(x, Ground.land(level, x, z), z);
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

    private static Optional<BlockPos> bench(ServerLevel level, Camp camp, Placed placed, List<BoundingBox> taken) {
        Direction out = Direction.getNearest(placed.front.getX() - placed.centre.getX(), 0, placed.front.getZ() - placed.centre.getZ());
        for (Direction aside : List.of(out.getClockWise(), out.getCounterClockWise())) {
            BlockPos column = placed.front.relative(aside, BENCH_ASIDE);
            BlockPos seat = new BlockPos(column.getX(), Ground.land(level, column.getX(), column.getZ()) + 1, column.getZ());
            BlockPos ground = seat.below();
            if (taken.stream().noneMatch(box -> box.isInside(seat)) && level.getBlockState(seat).canBeReplaced() && level.getBlockState(seat.above()).canBeReplaced()
                    && level.getBlockState(seat).getFluidState().isEmpty() && !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
                remember(level, camp, seat);
                remember(level, camp, seat.above());
                if (!level.getBlockState(seat.above()).isAir()) {
                    level.setBlock(seat.above(), Blocks.AIR.defaultBlockState(), FLAGS);
                }
                level.setBlock(seat, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, aside.getAxis()), FLAGS);
                return Optional.of(seat);
            }
        }
        return Optional.empty();
    }

    private static void remember(ServerLevel level, Camp camp, BlockPos pos) {
        camp.originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

}
