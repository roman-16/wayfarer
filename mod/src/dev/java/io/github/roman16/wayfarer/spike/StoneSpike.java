package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.corelib.death.PlayerDeathEvent;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import io.github.roman16.wayfarer.CompassEvent;
import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class StoneSpike {
    private static final int CHECK_TICKS = 20;
    private static final String DATA = Wayfarer.MODID + "_stone_spike";
    private static final String GRAVESTONE = "gravestone";

    private StoneSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithGravestone {
        private WithGravestone() {}

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            if (ModList.get().isLoaded(GRAVESTONE)) {
                NeoForge.EVENT_BUS.register(StoneSpike.class);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGrave(PlayerDeathEvent event) {
        Death death = event.getDeath();
        ServerPlayer player = event.getPlayer();
        ServerLevel level = player.serverLevel();
        GraveStoneTileEntity grave = find(level, death.getBlockPos(), death.getId());
        if (grave == null || things(death) == 0) {
            return;
        }
        Stones stones = Stones.of(player.server);
        stones.all.put(death.getId(), new Stone(player.getUUID(), level.dimension(), grave.getBlockPos(), things(death),
                level.getGameTime(), false));
        stones.setDirty();
        QuestSpike.sync(player.server);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Stones stones = Stones.of(server);
        boolean changed = false;
        for (Map.Entry<UUID, Stone> entry : List.copyOf(stones.all.entrySet())) {
            Stone stone = entry.getValue();
            ServerLevel level = server.getLevel(stone.dimension());
            if (level == null || !level.isLoaded(stone.pos())) {
                continue;
            }
            if (!(level.getBlockEntity(stone.pos()) instanceof GraveStoneTileEntity grave) || !grave.getDeath().getId().equals(entry.getKey())) {
                stones.all.remove(entry.getKey());
                changed = true;
            } else if (things(grave.getDeath()) != stone.things()) {
                stones.all.put(entry.getKey(), stone.withThings(things(grave.getDeath())));
                changed = true;
            }
        }
        if (changed) {
            stones.setDirty();
            QuestSpike.sync(server);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("stone")
                        .then(Commands.literal("letgo")
                                .then(Commands.argument("stone", StringArgumentType.word())
                                        .executes(context -> letGo(context.getSource(), StringArgumentType.getString(context, "stone")))))));
    }

    private static int letGo(CommandSourceStack source, String id) {
        Stones stones = Stones.of(source.getServer());
        ServerPlayer player = source.getPlayer();
        UUID key = UUID.fromString(id);
        Stone stone = stones.all.get(key);
        if (player == null || stone == null || !stone.player().equals(player.getUUID())) {
            source.sendFailure(Component.literal("stone failed: you have no stone " + id));
            return 0;
        }
        stones.all.put(key, stone.lettingGo());
        stones.setDirty();
        source.sendSuccess(() -> Component.literal("You let the stone with %d things go.".formatted(stone.things())), false);
        QuestSpike.sync(source.getServer());
        return 1;
    }

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        ServerPlayer player = event.player();
        event.markers().removeIf(marker -> marker.kind() == CompassPayload.Kind.GRAVE);
        for (Stone stone : mine(player)) {
            if (!stone.letGo() && stone.dimension() == player.level().dimension()) {
                event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.GRAVE, stone.things() + " things", stone.pos().getX(), stone.pos().getZ()));
            }
        }
    }

    static JsonArray view(ServerPlayer player) {
        JsonArray list = new JsonArray();
        for (Map.Entry<UUID, Stone> entry : Stones.of(player.server).all.entrySet()) {
            Stone stone = entry.getValue();
            if (stone.player().equals(player.getUUID())) {
                JsonObject json = new JsonObject();
                json.addProperty("id", entry.getKey().toString());
                json.addProperty("things", stone.things());
                json.addProperty("x", stone.pos().getX());
                json.addProperty("z", stone.pos().getZ());
                json.addProperty("let_go", stone.letGo());
                list.add(json);
            }
        }
        return list;
    }

    private static List<Stone> mine(ServerPlayer player) {
        List<Stone> stones = new ArrayList<>();
        Stones.of(player.server).all.values().stream().filter(stone -> stone.player().equals(player.getUUID()))
                .sorted(Comparator.comparingInt(Stone::things).reversed())
                .forEach(stones::add);
        return stones;
    }

    private static GraveStoneTileEntity find(ServerLevel level, BlockPos near, UUID id) {
        ChunkPos centre = new ChunkPos(near);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (BlockEntity entity : level.getChunk(centre.x + dx, centre.z + dz).getBlockEntities().values()) {
                    if (entity instanceof GraveStoneTileEntity grave && grave.getDeath().getId().equals(id)) {
                        return grave;
                    }
                }
            }
        }
        return null;
    }

    private static int things(Death death) {
        return (int) death.getAllItems().stream().filter(stack -> !stack.isEmpty()).count();
    }

    record Stone(UUID player, ResourceKey<Level> dimension, BlockPos pos, int things, long time, boolean letGo) {
        Stone withThings(int count) {
            return new Stone(player, dimension, pos, count, time, letGo);
        }

        Stone lettingGo() {
            return new Stone(player, dimension, pos, things, time, true);
        }
    }

    public static final class Stones extends SavedData {
        private final Map<UUID, Stone> all = new TreeMap<>();

        static Stones of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Stones::new, Stones::load, null), DATA);
        }

        private static Stones load(CompoundTag tag, HolderLookup.Provider registries) {
            Stones stones = new Stones();
            for (String key : tag.getAllKeys()) {
                CompoundTag entry = tag.getCompound(key);
                stones.all.put(UUID.fromString(key), new Stone(entry.getUUID("player"),
                        ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("dimension"))),
                        BlockPos.of(entry.getLong("pos")), entry.getInt("things"), entry.getLong("time"), entry.getBoolean("let_go")));
            }
            return stones;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            all.forEach((id, stone) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("player", stone.player());
                entry.putString("dimension", stone.dimension().location().toString());
                entry.putLong("pos", stone.pos().asLong());
                entry.putInt("things", stone.things());
                entry.putLong("time", stone.time());
                entry.putBoolean("let_go", stone.letGo());
                tag.put(id.toString(), entry);
            });
            return tag;
        }
    }
}
