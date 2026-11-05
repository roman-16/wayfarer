package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Handover;
import io.github.roman16.wayfarer.Heat;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.NotchedLog;
import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class Memories {
    private static final int BESIDE_REACH = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> DREAM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Wayfarer.id("dream"));
    private static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> NIGHT_DREAM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Wayfarer.id("dream_night"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_memory", Comparator.comparingLong(ChunkPos::toLong));
    static final String ROLE = Wayfarer.MODID + "_memory_";
    private static final int CHUNKS = 4;
    private static final int COPY_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final String OPENS = "memory_opens";
    private static final String SHARD_TAKEN = "shard_taken";
    private static final double MEETING_BLOCKS = 2;
    private static final float CAST_VOLUME = 0.5f;
    private static final List<ResourceKey<Level>> LET_GO = new ArrayList<>();
    @Nullable
    private static final int SCENE_BLOCKS = 256;
    private static Dream dream;

    private Memories() {}

    public record Flash(boolean on, int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(Wayfarer.id("flash"));
        public static final StreamCodec<ByteBuf, Flash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Flash::on,
                ByteBufCodecs.VAR_INT, Flash::ticks,
                Flash::new);

        @Override
        public Type<Flash> type() {
            return TYPE;
        }
    }

    private record Return(ResourceKey<Level> level, Vec3 pos, float yRot, float xRot) {}

    private enum Phase { MAKING, DREAMING, WAKING }

    private static final class Dream {
        private final Story.Memory memory;
        @Nullable
        private final Engine.Active active;
        private final ResourceKey<Level> key;
        private final ServerLevel level;
        private final BlockPos stone;
        private final Map<UUID, Vec3> arrivals = new LinkedHashMap<>();
        private final Set<BlockPos> put = new HashSet<>();
        private Vec3 anchor;
        private final Map<UUID, Return> dreamers = new LinkedHashMap<>();
        private Phase phase = Phase.MAKING;
        private int step;
        private boolean entered;
        private boolean asked;
        private boolean finishing;
        private boolean taken;
        private boolean cast;
        private int fade;

        private Dream(Story.Memory memory, @Nullable Engine.Active active, ResourceKey<Level> key, ServerLevel level, BlockPos stone) {
            this.memory = memory;
            this.active = active;
            this.key = key;
            this.level = level;
            this.stone = stone;
            this.anchor = Vec3.atCenterOf(stone);
        }

        private Story.Step current() {
            return memory.steps().get(step);
        }
    }

    public static boolean touch(ServerPlayer player, BlockPos pos, ItemStack stack) {
        MinecraftServer server = player.server;
        if (dream != null && player.level() == dream.level) {
            if (dream.phase == Phase.DREAMING) {
                switch (dream.current().kind()) {
                    case "lay" -> lay(player, pos, stack);
                    case "take" -> take(player, pos);
                    default -> {}
                }
            }
            return true;
        }
        Optional<Engine.Active> active = Engine.main(server).filter(step -> step.step().kind().equals("memory"));
        if (active.isEmpty() || dream != null || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        Story.Memory memory = Story.get().memory(active.get().step().text("memory"));
        if (stone(server, memory).filter(pos::equals).isEmpty()) {
            return false;
        }
        begin(server, memory, active.get(), pos, player.blockPosition());
        return true;
    }

    static Optional<BlockPos> stone(MinecraftServer server, Story.Memory memory) {
        return switch (memory.place()) {
            case FirstCamp.NAME -> FirstCamp.built(server) ? Optional.of(FirstCamp.world(server, "hearth")) : Optional.empty();
            case Frost.STONE -> Frost.built(server) ? Progress.of(server).place(Frost.STONE) : Optional.empty();
            case FirstVillage.STONE -> FirstVillage.built(server) ? Progress.of(server).place(FirstVillage.STONE) : Optional.empty();
            case WendCamp.NAME -> WendCamp.ready(server) ? Progress.of(server).place(WendCamp.NAME) : Optional.empty();
            case SandSingers.LAIR -> SandSingers.ready(server, SandSingers.LAIR) ? Progress.of(server).place(SandSingers.LAIR) : Optional.empty();
            case Act3Halls.MANSION_CHAIR, Prologue.LAST_CAMP -> Progress.of(server).place(memory.place());
            default -> throw new IllegalStateException("memory " + memory.id() + " happens at " + memory.place() + ", where no memory can be dreamt");
        };
    }

    public static void again(ServerPlayer player, String id) {
        MinecraftServer server = player.server;
        Progress progress = Progress.of(server);
        boolean dreamt = Story.get().quests().stream().anyMatch(quest -> dreamt(progress, quest).filter(id::equals).isPresent());
        Optional<BlockPos> fire = fire(player.level(), player.blockPosition());
        if (!dreamt || dream != null || player.level().dimension() != Level.OVERWORLD || fire.isEmpty()) {
            return;
        }
        Story.Memory memory = Story.get().memory(id);
        stone(server, memory).ifPresent(stone -> begin(server, memory, null, stone, fire.get()));
    }

    static Optional<String> dreamt(Progress progress, Story.Quest quest) {
        for (int index = 0; index < quest.steps().size(); index++) {
            Story.Step step = quest.steps().get(index);
            if (step.kind().equals("memory") && (progress.done(quest.id()) || progress.step(quest.id()) > index)) {
                return Optional.of(step.text("memory"));
            }
        }
        return Optional.empty();
    }

    private static Vec3 clearOfFire(ServerLevel level, Vec3 arrival) {
        BlockPos at = BlockPos.containing(arrival);
        Optional<BlockPos> fire = BlockPos.betweenClosedStream(at.offset(-1, -1, -1), at.offset(1, 1, 1))
                .filter(pos -> level.getBlockState(pos).is(BlockTags.CAMPFIRES) || level.getBlockState(pos).is(BlockTags.FIRE)).map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(arrival)));
        if (fire.isEmpty()) {
            return arrival;
        }
        Vec3 centre = Vec3.atBottomCenterOf(fire.get());
        Vec3 away = arrival.subtract(centre).multiply(1, 0, 1);
        Vec3 out = away.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : away.normalize();
        return new Vec3(centre.x + out.x * Balance.get().memory().arrivalBlocks() / 2.0, arrival.y, centre.z + out.z * Balance.get().memory().arrivalBlocks() / 2.0);
    }

    public static Optional<BlockPos> fire(Level level, BlockPos at) {
        int reach = Balance.get().memory().fireBlocks();
        return BlockPos.betweenClosedStream(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))
                .filter(pos -> CampfireBlock.isLitCampfire(level.getBlockState(pos))).map(BlockPos::immutable).min(Comparator.comparingDouble(at::distSqr));
    }

    static boolean copy(MinecraftServer server, ServerLevel dream, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (dream.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        ServerLevel world = Engine.overworld(server);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = world.getBlockState(pos);
            if (!dream.getBlockState(pos).equals(state)) {
                dream.setBlock(pos, state, COPY_FLAGS);
            }
            BlockEntity entity = world.getBlockEntity(pos);
            BlockEntity copy = dream.getBlockEntity(pos);
            if (entity != null && copy != null) {
                copy.loadWithComponents(entity.saveWithFullMetadata(server.registryAccess()), server.registryAccess());
                copy.setChanged();
            }
        }
        return true;
    }

    public static boolean dream(Level level) {
        return level.dimensionTypeRegistration().is(DREAM_TYPE) || level.dimensionTypeRegistration().is(NIGHT_DREAM_TYPE);
    }

    static boolean scene(Vec3 at) {
        return dream != null && at.distanceToSqr(Vec3.atCenterOf(dream.stone)) <= (double) SCENE_BLOCKS * SCENE_BLOCKS;
    }

    public static boolean dreaming(Player player) {
        return dream != null && dream.dreamers.containsKey(player.getUUID());
    }

    static boolean jump(MinecraftServer server, String id) {
        if (dream == null || dream.phase != Phase.DREAMING) {
            return false;
        }
        List<String> ids = dream.memory.steps().stream().map(Story.Step::id).toList();
        if (!ids.contains(id)) {
            return false;
        }
        Talk.reset();
        dream.step = ids.indexOf(id);
        dream.entered = false;
        dream.asked = false;
        dreamers(server).forEach(Tasks::refresh);
        return true;
    }

    static Optional<Tasks.Panel> panel(ServerPlayer player) {
        if (!dreaming(player) || dream.phase != Phase.DREAMING) {
            return Optional.empty();
        }
        Story.Step step = dream.current();
        String objective = step.kind().equals("gather") && step.spec().has("count")
                ? step.objective().replace("{carried}", String.valueOf(Math.min(carried(player, step), step.number("count")))).replace("{count}", step.text("count"))
                : step.objective().replace("{away}", String.valueOf(Places.leaving(dream.memory.place())));
        return Optional.of(new Tasks.Panel(Component.translatable("wayfarer.journal.memory"), Component.literal(dream.memory.title()),
                Component.literal(objective), List.of(), List.of()));
    }

    private static void begin(MinecraftServer server, Story.Memory memory, @Nullable Engine.Active active, BlockPos stone, BlockPos gathering) {
        ServerLevel overworld = Engine.overworld(server);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Wayfarer.id("memory_" + Long.toHexString(System.currentTimeMillis())));
        LevelStem stem = new LevelStem(server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(memory.night() ? NIGHT_DREAM_TYPE : DREAM_TYPE),
                overworld.getChunkSource().getGenerator());
        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, key, () -> stem);
        dream = new Dream(memory, active, key, level, stone);
        Balance.Memory balance = Balance.get().memory();
        for (ServerPlayer player : overworld.players()) {
            if (player.blockPosition().distSqr(gathering) <= (double) balance.togetherBlocks() * balance.togetherBlocks()) {
                dream.dreamers.put(player.getUUID(), new Return(Level.OVERWORLD, player.position(), player.getYRot(), player.getXRot()));
                Vec3 offset = player.position().subtract(Vec3.atBottomCenterOf(gathering));
                if (active == null && offset.horizontalDistance() > balance.arrivalBlocks()) {
                    offset = offset.multiply(1, 0, 1).normalize().scale(balance.arrivalBlocks());
                }
                dream.arrivals.put(player.getUUID(), Vec3.atBottomCenterOf(stone.above()).add(offset.x, 0, offset.z));
                PacketDistributor.sendToPlayer(player, new Flash(true, Balance.get().memory().fadeTicks()));
                Sounds.play(player, OPENS);
            }
        }
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(stone), CHUNKS, new ChunkPos(stone));
        LOGGER.info("memory {}: {} dream it together in {}", memory.id(), dream.dreamers.size(), key.location());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        letGo(server);
        if (dream == null) {
            return;
        }
        switch (dream.phase) {
            case MAKING -> make(server);
            case DREAMING -> dream(server);
            case WAKING -> {
                if (--dream.fade <= 0) {
                    wake(server);
                }
            }
        }
    }

    private static void make(MinecraftServer server) {
        ChunkPos centre = new ChunkPos(dream.stone);
        for (int x = centre.x - CHUNKS + 1; x < centre.x + CHUNKS; x++) {
            for (int z = centre.z - CHUNKS + 1; z < centre.z + CHUNKS; z++) {
                if (dream.level.getChunkSource().getChunkNow(x, z) == null) {
                    return;
                }
            }
        }
        boolean built = switch (dream.memory.place()) {
            case FirstCamp.NAME -> {
                Camps.build(dream.level, FirstCamp.THEN, FirstCamp.origin(server), FirstCamp.turn(server));
                yield true;
            }
            case Frost.STONE -> Frost.past(server, dream.level);
            case FirstVillage.STONE -> FirstVillage.past(server, dream.level);
            case WendCamp.NAME -> WendCamp.past(server, dream.level);
            case SandSingers.LAIR -> SandSingers.past(server, dream.level);
            case Act3Halls.MANSION_CHAIR -> Act3Halls.past(server, dream.level);
            case Prologue.LAST_CAMP -> Prologue.past(server, dream.level);
            default -> throw new IllegalStateException("memory " + dream.memory.id() + " happens at " + dream.memory.place() + ", which has no past to build");
        };
        if (!built) {
            return;
        }
        for (Map.Entry<UUID, Return> entry : dream.dreamers.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Stash.of(server).keep(player, entry.getValue());
                arrive(player, dream.arrivals.get(entry.getKey()), entry.getValue());
            }
        }
        dream.phase = Phase.DREAMING;
        if (dream.memory.music() != null) {
            dreamers(server).forEach(player -> Music.play(player, dream.memory.music(), false));
        }
    }

    private static Vec3 beside(Vec3 wanted) {
        BlockPos at = BlockPos.containing(wanted);
        for (int reach = 0; reach <= BESIDE_REACH; reach++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos cell = at.offset(dx, 0, dz);
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == reach && open(cell) && open(cell.above())
                            && !dream.level.getBlockState(cell.below()).getCollisionShape(dream.level, cell.below()).isEmpty()) {
                        return new Vec3(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
                    }
                }
            }
        }
        return wanted;
    }

    private static boolean open(BlockPos cell) {
        return dream.level.getBlockState(cell).getCollisionShape(dream.level, cell).isEmpty();
    }

    private static void arrive(ServerPlayer player, Vec3 wanted, Return from) {
        Vec3 arrival = beside(clearOfFire(dream.level, wanted));
        BlockPos feet = BlockPos.containing(arrival);
        while (!open(feet) || !open(feet.above())) {
            feet = feet.above();
        }
        Vec3 toward = dream.anchor.subtract(arrival).multiply(1, 0, 1);
        float yaw = toward.lengthSqr() < 1 ? from.yRot() : (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        dream.arrivals.put(player.getUUID(), new Vec3(arrival.x, feet.getY(), arrival.z));
        player.teleportTo(dream.level, arrival.x, feet.getY(), arrival.z, yaw, 0);
        player.resetFallDistance();
        player.getInventory().clearContent();
        List<ItemStack> kit = dream.memory.kit().stream().map(item -> Engine.stack(player, dream.memory.id(), item)).toList();
        Notices.given(player, kit);
        kit.forEach(stack -> player.getInventory().add(stack.copy()));
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        if (ModList.get().isLoaded("toughasnails")) {
            Heat.rest(player);
        }
        PacketDistributor.sendToPlayer(player, new Flash(false, Balance.get().memory().fadeTicks()));
    }

    private static void dream(MinecraftServer server) {
        if (dream.finishing) {
            return;
        }
        Story.Step step = dream.current();
        if (!dream.entered) {
            dream.entered = true;
            enter(server, step);
            Talk.say(quest(), dream.anchor, step.lines(), Talk.Placeholders.NONE);
            dream.dreamers.keySet().stream().map(server.getPlayerList()::getPlayer).filter(player -> player != null).forEach(Tasks::refresh);
        }
        if (step.kind().equals("talk")) {
            if (!dream.asked && !Talk.busy()) {
                dream.asked = true;
                ask();
            }
            return;
        }
        if (done(server, step)) {
            next(server);
        }
    }

    private static void enter(MinecraftServer server, Story.Step step) {
        Optional<ServerPlayer> leader = dreamers(server).stream().max(Comparator.comparingDouble(player -> Steps.horizontal(player.blockPosition(), dream.stone)));
        leader.ifPresent(player -> dream.anchor = player.position());
        List<Entity> bodies = new ArrayList<>();
        dream.level.getAllEntities().forEach(bodies::add);
        if (step.spec().has("stands")) {
            bodies.stream().filter(body -> body.getTags().contains(ROLE + step.text("stands"))).forEach(body -> People.pose(body, "standing"));
        }
        if (step.spec().has("vanish")) {
            bodies.stream().filter(body -> body.getTags().contains(ROLE + step.text("vanish"))).forEach(Entity::discard);
        }
        if (step.spec().has("meets")) {
            leader.ifPresent(player -> meet(player, step.text("meets")));
        }
        if (step.spec().has("douses")) {
            fire(dream.level, dream.stone).ifPresent(fire -> dream.level.setBlock(fire, dream.level.getBlockState(fire).setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL));
        }
    }

    private static void meet(ServerPlayer dreamer, String person) {
        Vec3 centre = Vec3.atBottomCenterOf(dream.stone.above());
        Vec3 at = People.ring(dream.level, centre, dreamer.position(), MEETING_BLOCKS, 1).getFirst();
        Vec3 toward = centre.subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        Optional<Vec3> offstage = People.offstage(dream.level, at);
        Vec3 from = offstage.orElse(at);
        Entity body;
        if (People.skinned(person)) {
            body = People.spawn(dream.level, person, from, yaw, "standing", ROLE + person);
            if (Story.get().people().containsKey(person)) {
                body.setCustomName(Component.literal(People.label(dream.level.getServer(), person)));
            }
        } else {
            Mob grey = EntityType.VINDICATOR.create(dream.level);
            grey.moveTo(from.x, from.y, from.z, yaw, 0);
            grey.setYHeadRot(yaw);
            grey.setNoAi(true);
            grey.setInvulnerable(true);
            grey.setSilent(true);
            grey.addTag(ROLE + person);
            dream.level.addFreshEntity(grey);
            body = grey;
        }
        if (offstage.isPresent()) {
            People.approach(body, at, yaw);
        }
    }

    private static boolean done(MinecraftServer server, Story.Step step) {
        BlockPos fire = dream.stone.above();
        BlockState state = dream.level.getBlockState(fire);
        return switch (step.kind()) {
            case "gather" -> dreamers(server).stream().anyMatch(player -> carried(player, step) >= step.number("count"));
            case "lay" -> state.is(Blocks.CAMPFIRE);
            case "light" -> state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT);
            case "cook" -> dream.level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire
                    && campfire.getItems().stream().anyMatch(stack -> !stack.isEmpty());
            case "notch" -> FirstCamp.pine(server).stream().anyMatch(pos -> dream.level.getBlockState(pos).is(Wayfarer.NOTCHED_SPRUCE_LOG));
            case "take" -> dream.taken;
            case "cast" -> dream.cast;
            case "climb" -> dreamers(server).stream().anyMatch(player -> player.getY() >= Engine.at(server, step.text("at")).getY() - 1
                    && dream.level.canSeeSky(player.blockPosition()));
            case "away" -> dreamers(server).stream().anyMatch(player -> Steps.horizontal(player.blockPosition(), dream.stone)
                    >= (double) Places.leaving(dream.memory.place()) * Places.leaving(dream.memory.place()));
            case "say" -> !Talk.busy();
            default -> throw new IllegalStateException("memory " + dream.memory.id() + "/" + step.id() + " is of the kind " + step.kind()
                    + ", which a memory does not know");
        };
    }

    private static int carried(ServerPlayer player, Story.Step step) {
        TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(step.text("tag")));
        return player.getInventory().items.stream().filter(stack -> stack.is(tag)).mapToInt(ItemStack::getCount).sum();
    }

    private static void lay(ServerPlayer player, BlockPos pos, ItemStack stack) {
        Story.Step step = dream.current();
        if (!pos.equals(dream.stone) || !stack.is(ItemTags.LOGS) || stack.getCount() < step.number("count") || !dream.level.getBlockState(pos.above()).isAir()) {
            return;
        }
        stack.shrink(step.number("count"));
        dream.level.setBlock(pos.above(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
    }

    private static void take(ServerPlayer player, BlockPos pos) {
        if (!pos.equals(dream.stone) || dream.taken || !(dream.level.getBlockEntity(pos) instanceof CallingStoneBlockEntity stone)) {
            return;
        }
        stone.hold(Optional.empty());
        dream.taken = true;
        Sounds.play(player, SHARD_TAKEN);
    }

    private static void ask() {
        Story.Step step = dream.current();
        Talk.ask(new Talk.Offer(quest(), null, step.choices(), dream.anchor, (player, choice) -> {
            if (choice.refused() != null) {
                Talk.say(quest(), dream.anchor, new Story.Line(null, choice.refused()));
                Talk.then(quest(), Memories::ask);
                return;
            }
            if (!choice.reply().isEmpty()) {
                Talk.say(quest(), dream.anchor, new Story.Line(null, choice.reply()));
            }
            next(player.server);
        }));
    }

    private static void next(MinecraftServer server) {
        Talk.say(quest(), dream.anchor, dream.current().done(), Talk.Placeholders.NONE);
        if (dream.step + 1 < dream.memory.steps().size()) {
            dream.step++;
            dream.entered = false;
            dream.asked = false;
            return;
        }
        dream.finishing = true;
        Talk.then(quest(), () -> {
            if (dream != null) {
                dream.phase = Phase.WAKING;
                dream.fade = Balance.get().memory().fadeTicks();
                dreamers(server).forEach(player -> PacketDistributor.sendToPlayer(player, new Flash(true, dream.fade)));
            }
        });
    }

    private static void wake(MinecraftServer server) {
        Dream ended = dream;
        for (ServerPlayer player : dreamers(server)) {
            Stash.of(server).restore(player);
            if (ended.memory.music() != null) {
                Music.stop(player);
            }
            PacketDistributor.sendToPlayer(player, new Flash(false, Balance.get().memory().fadeTicks()));
        }
        end(server);
        if (ended.active != null && !ended.dreamers.isEmpty()) {
            Engine.complete(server, ended.active);
        }
    }

    private static void end(MinecraftServer server) {
        dream.level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(dream.stone), CHUNKS, new ChunkPos(dream.stone));
        InfiniverseAPI.get().markDimensionForUnregistration(server, dream.key);
        LET_GO.add(dream.key);
        LOGGER.info("memory {}: woke, {} let go", dream.memory.id(), dream.key.location());
        dream = null;
    }

    private static List<ServerPlayer> dreamers(MinecraftServer server) {
        return dream.dreamers.keySet().stream().map(server.getPlayerList()::getPlayer).filter(player -> player != null).toList();
    }

    @Nullable
    private static String quest() {
        return dream.active != null ? dream.active.quest().id() : null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (dream == null || !(event.getEntity() instanceof ServerPlayer player) || player.level() != dream.level) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.removeAllEffects();
        Vec3 arrival = dream.arrivals.get(player.getUUID());
        player.teleportTo(dream.level, arrival.x, arrival.y, arrival.z, player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || !dream(level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (dream != null && level == dream.level && dream.put.remove(event.getPos())) {
            return;
        }
        if (dream == null || level != dream.level || dream.phase != Phase.DREAMING || !gathered(player.server, event.getPos(), event.getState())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("wayfarer.memory.kept").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (dream != null && event.getLevel() == dream.level && event.getBreaker() instanceof ServerPlayer player) {
            event.getDrops().forEach(drop -> Handover.give(player, drop.getItem()));
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (dream != null && event.getLevel() == dream.level && event.getEntity() instanceof ServerPlayer) {
            dream.put.add(event.getPos().immutable());
        }
    }

    private static boolean gathered(MinecraftServer server, BlockPos pos, BlockState state) {
        Story.Step step = dream.current();
        if (!step.kind().equals("gather") || !step.spec().has("tag")
                || !new ItemStack(state.getBlock()).is(TagKey.create(Registries.ITEM, ResourceLocation.parse(step.text("tag"))))) {
            return false;
        }
        return !dream.memory.place().equals(FirstCamp.NAME) || FirstCamp.fallen(server).contains(pos);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (dream == null || event.getLevel() != dream.level || dream.phase != Phase.DREAMING || !dream.current().kind().equals("cast")
                || !(event.getEntity() instanceof ServerPlayer player) || !event.getPos().equals(Engine.at(player.server, dream.current().text("at")))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!dream.cast) {
            dream.cast = true;
            dream.level.playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1, 1);
            dream.level.playSound(null, event.getPos(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, CAST_VOLUME, 1);
        }
    }

    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (dream == null || event.getItemAbility() != ItemAbilities.AXE_STRIP || event.getLevel() != dream.level
                || !dream.current().kind().equals("notch") || !FirstCamp.pine(dream.level.getServer()).contains(event.getPos())) {
            return;
        }
        Direction face = event.getContext().getClickedFace();
        event.setFinalState(Wayfarer.NOTCHED_SPRUCE_LOG.get().defaultBlockState()
                .setValue(NotchedLog.FACING, face.getAxis().isHorizontal() ? face : event.getContext().getHorizontalDirection().getOpposite()));
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getLevel() instanceof ServerLevel level && dream(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && dreaming(player)) {
            Stash.of(player.server).restore(player);
            dream.dreamers.remove(player.getUUID());
            if (dream.dreamers.isEmpty()) {
                end(player.server);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !dreaming(player)) {
            Stash.of(player.server).restore(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (dream != null) {
            dreamers(event.getServer()).forEach(Stash.of(event.getServer())::restore);
            end(event.getServer());
        }
    }

    private static void letGo(MinecraftServer server) {
        for (ResourceKey<Level> key : List.copyOf(LET_GO)) {
            if (server.getLevel(key) == null) {
                LET_GO.remove(key);
                delete(server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(key.location().getNamespace()).resolve(key.location().getPath()));
            }
        }
    }

    private static void delete(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static final class Stash extends net.minecraft.world.level.saveddata.SavedData {
        private static final String NAME = "wayfarer_dreamers";
        private final Map<UUID, CompoundTag> kept = new LinkedHashMap<>();

        static Stash of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Stash::new, Stash::load, null), NAME);
        }

        private static Stash load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            Stash stash = new Stash();
            tag.getAllKeys().forEach(key -> stash.kept.put(UUID.fromString(key), tag.getCompound(key)));
            return stash;
        }

        @Override
        public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            kept.forEach((player, entry) -> tag.put(player.toString(), entry));
            return tag;
        }

        void keep(ServerPlayer player, Return from) {
            CompoundTag entry = new CompoundTag();
            entry.putString("level", from.level().location().toString());
            entry.putDouble("x", from.pos().x);
            entry.putDouble("y", from.pos().y);
            entry.putDouble("z", from.pos().z);
            entry.putFloat("yRot", from.yRot());
            entry.putFloat("xRot", from.xRot());
            entry.putFloat("health", player.getHealth());
            entry.putInt("food", player.getFoodData().getFoodLevel());
            entry.putFloat("saturation", player.getFoodData().getSaturationLevel());
            entry.put("inventory", player.getInventory().save(new ListTag()));
            if (ModList.get().isLoaded("toughasnails")) {
                entry.put("heat", Heat.keep(player));
            }
            kept.put(player.getUUID(), entry);
            setDirty();
        }

        void restore(ServerPlayer player) {
            CompoundTag entry = kept.remove(player.getUUID());
            if (entry == null) {
                return;
            }
            setDirty();
            ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("level"))));
            player.getInventory().clearContent();
            player.getInventory().load(entry.getList("inventory", Tag.TAG_COMPOUND));
            player.setHealth(entry.getFloat("health"));
            player.getFoodData().setFoodLevel(entry.getInt("food"));
            player.getFoodData().setSaturation(entry.getFloat("saturation"));
            if (ModList.get().isLoaded("toughasnails")) {
                Heat.restore(player, entry.getCompound("heat"));
            }
            player.teleportTo(level == null ? player.server.overworld() : level, entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                    entry.getFloat("yRot"), entry.getFloat("xRot"));
            player.resetFallDistance();
        }
    }
}
