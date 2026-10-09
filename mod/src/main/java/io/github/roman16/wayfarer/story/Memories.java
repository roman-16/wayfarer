package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Camps;
import io.github.roman16.wayfarer.Heat;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.NotchedLog;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.commoble.infiniverse.api.InfiniverseAPI;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class Memories {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> DREAM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Wayfarer.id("dream"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_memory", Comparator.comparingLong(ChunkPos::toLong));
    private static final int CHUNKS = 3;
    private static final String OPENS = "memory_opens";
    private static final List<ResourceKey<Level>> LET_GO = new ArrayList<>();
    @Nullable
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
        private final BlockPos hearth;
        private final Map<UUID, Vec3> arrivals = new LinkedHashMap<>();
        private final Map<UUID, Return> dreamers = new LinkedHashMap<>();
        private Phase phase = Phase.MAKING;
        private int step;
        private boolean entered;
        private boolean asked;
        private boolean finishing;
        private int fade;

        private Dream(Story.Memory memory, @Nullable Engine.Active active, ResourceKey<Level> key, ServerLevel level, BlockPos hearth) {
            this.memory = memory;
            this.active = active;
            this.key = key;
            this.level = level;
            this.hearth = hearth;
        }

        private Story.Step current() {
            return memory.steps().get(step);
        }
    }

    public static boolean touch(ServerPlayer player, BlockPos pos, ItemStack stack) {
        MinecraftServer server = player.server;
        if (dream != null && player.level() == dream.level && dream.phase == Phase.DREAMING) {
            return lay(player, pos, stack);
        }
        Optional<Engine.Active> active = Engine.main(server).filter(step -> step.step().kind().equals("memory"));
        if (active.isEmpty() || dream != null || player.level().dimension() != Level.OVERWORLD) {
            return false;
        }
        Story.Memory memory = Story.get().memory(active.get().step().text("memory"));
        if (!FirstCamp.NAME.equals(memory.place()) || !FirstCamp.built(server) || !FirstCamp.world(server, "hearth").equals(pos)) {
            return false;
        }
        begin(server, memory, active.get(), pos, pos);
        return true;
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
        if (!FirstCamp.NAME.equals(memory.place()) || !FirstCamp.built(server)) {
            return;
        }
        begin(server, memory, null, FirstCamp.world(server, "hearth"), fire.get());
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

    public static Optional<BlockPos> fire(Level level, BlockPos at) {
        int reach = Balance.get().memory().fireBlocks();
        return BlockPos.betweenClosedStream(at.offset(-reach, -reach, -reach), at.offset(reach, reach, reach))
                .filter(pos -> CampfireBlock.isLitCampfire(level.getBlockState(pos))).map(BlockPos::immutable).min(Comparator.comparingDouble(at::distSqr));
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
        return Optional.of(new Tasks.Panel(Component.translatable("wayfarer.journal.memory"), Component.literal(dream.memory.title()),
                Component.literal(dream.current().objective()), List.of(), List.of()));
    }

    private static void begin(MinecraftServer server, Story.Memory memory, @Nullable Engine.Active active, BlockPos hearth, BlockPos gathering) {
        ServerLevel overworld = Engine.overworld(server);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Wayfarer.id("memory_" + Long.toHexString(System.currentTimeMillis())));
        LevelStem stem = new LevelStem(server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(DREAM_TYPE),
                overworld.getChunkSource().getGenerator());
        ServerLevel level = InfiniverseAPI.get().getOrCreateLevel(server, key, () -> stem);
        dream = new Dream(memory, active, key, level, hearth);
        Balance.Memory balance = Balance.get().memory();
        for (ServerPlayer player : overworld.players()) {
            if (player.blockPosition().distSqr(gathering) <= (double) balance.togetherBlocks() * balance.togetherBlocks()) {
                dream.dreamers.put(player.getUUID(), new Return(Level.OVERWORLD, player.position(), player.getYRot(), player.getXRot()));
                Vec3 offset = player.position().subtract(Vec3.atBottomCenterOf(gathering));
                if (active == null && offset.horizontalDistance() > balance.arrivalBlocks()) {
                    offset = offset.multiply(1, 0, 1).normalize().scale(balance.arrivalBlocks());
                }
                dream.arrivals.put(player.getUUID(), Vec3.atBottomCenterOf(hearth.above()).add(offset.x, 0, offset.z));
                PacketDistributor.sendToPlayer(player, new Flash(true, Balance.get().memory().fadeTicks()));
                Sounds.play(player, OPENS);
            }
        }
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(hearth), CHUNKS, new ChunkPos(hearth));
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
        ChunkPos centre = new ChunkPos(dream.hearth);
        for (int x = centre.x - CHUNKS; x <= centre.x + CHUNKS; x++) {
            for (int z = centre.z - CHUNKS; z <= centre.z + CHUNKS; z++) {
                if (dream.level.getChunkSource().getChunkNow(x, z) == null) {
                    return;
                }
            }
        }
        Camps.build(dream.level, FirstCamp.THEN, FirstCamp.origin(server), FirstCamp.turn(server));
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

    private static void arrive(ServerPlayer player, Vec3 arrival, Return from) {
        BlockPos feet = BlockPos.containing(arrival);
        while (!dream.level.getBlockState(feet).getCollisionShape(dream.level, feet).isEmpty()
                || !dream.level.getBlockState(feet.above()).getCollisionShape(dream.level, feet.above()).isEmpty()) {
            feet = feet.above();
        }
        dream.arrivals.put(player.getUUID(), new Vec3(arrival.x, feet.getY(), arrival.z));
        player.teleportTo(dream.level, arrival.x, feet.getY(), arrival.z, from.yRot(), from.xRot());
        player.getInventory().clearContent();
        dream.memory.kit().forEach(item -> player.getInventory().add(Engine.stack(player, dream.memory.id(), item)));
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
            Talk.say(quest(), Vec3.atCenterOf(dream.hearth), step.lines(), Talk.Placeholders.NONE);
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

    private static boolean done(MinecraftServer server, Story.Step step) {
        BlockPos fire = dream.hearth.above();
        BlockState state = dream.level.getBlockState(fire);
        return switch (step.kind()) {
            case "gather" -> dreamers(server).stream().anyMatch(player -> carried(player, step) >= step.number("count"));
            case "lay" -> state.is(Blocks.CAMPFIRE);
            case "light" -> state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT);
            case "cook" -> dream.level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire
                    && campfire.getItems().stream().anyMatch(stack -> !stack.isEmpty());
            case "notch" -> FirstCamp.pine(server).stream().anyMatch(pos -> dream.level.getBlockState(pos).is(Wayfarer.NOTCHED_SPRUCE_LOG));
            default -> throw new IllegalStateException("memory " + dream.memory.id() + "/" + step.id() + " is of the kind " + step.kind()
                    + ", which a memory does not know");
        };
    }

    private static int carried(ServerPlayer player, Story.Step step) {
        TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(step.text("tag")));
        return player.getInventory().items.stream().filter(stack -> stack.is(tag)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean lay(ServerPlayer player, BlockPos pos, ItemStack stack) {
        Story.Step step = dream.current();
        if (!step.kind().equals("lay") || !pos.equals(dream.hearth) || !stack.is(ItemTags.LOGS) || stack.getCount() < step.number("count")
                || !dream.level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        stack.shrink(step.number("count"));
        dream.level.setBlock(pos.above(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
        return true;
    }

    private static void ask() {
        Story.Step step = dream.current();
        Talk.ask(new Talk.Offer(quest(), null, step.choices(), Vec3.atCenterOf(dream.hearth), (player, choice) -> {
            if (choice.refused() != null) {
                Talk.say(quest(), Vec3.atCenterOf(dream.hearth), new Story.Line(null, choice.refused()));
                Talk.then(Memories::ask);
                return;
            }
            Talk.say(quest(), Vec3.atCenterOf(dream.hearth), new Story.Line(null, choice.reply()));
            next(player.server);
        }));
    }

    private static void next(MinecraftServer server) {
        Talk.say(quest(), Vec3.atCenterOf(dream.hearth), dream.current().done(), Talk.Placeholders.NONE);
        if (dream.step + 1 < dream.memory.steps().size()) {
            dream.step++;
            dream.entered = false;
            dream.asked = false;
            return;
        }
        dream.finishing = true;
        Talk.then(() -> {
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
        dream.level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(dream.hearth), CHUNKS, new ChunkPos(dream.hearth));
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
        if (event.getLevel() instanceof ServerLevel level && level.dimensionTypeRegistration().is(DREAM_TYPE)) {
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
        }
    }
}
