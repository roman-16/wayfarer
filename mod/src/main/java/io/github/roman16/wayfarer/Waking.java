package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Arrivals;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Waking {
    private static final String ASLEEP = "asleep";
    private static final int CHECK_TICKS = 20;
    private static final String DATA = Wayfarer.MODID + "_waking";
    private static final Set<UUID> awake = new HashSet<>();
    private static boolean held;

    private Waking() {}

    private static final class State extends SavedData {
        boolean asleep;

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.asleep = tag.getBoolean(ASLEEP);
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean(ASLEEP, asleep);
            return tag;
        }
    }

    public record Sleeper(String name, boolean awake) {
        static final StreamCodec<ByteBuf, Sleeper> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Sleeper::name, ByteBufCodecs.BOOL,
                Sleeper::awake, Sleeper::new);
    }

    public record Page(boolean asleep, List<Sleeper> sleepers) implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(Wayfarer.id("waking"));
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Page::asleep,
                Sleeper.STREAM_CODEC.apply(ByteBufCodecs.list()), Page::sleepers, Page::new);
        static final Page AWAKE = new Page(false, List.of());

        @Override
        public Type<Page> type() {
            return TYPE;
        }
    }

    public record Wake(boolean awake) implements CustomPacketPayload {
        public static final Type<Wake> TYPE = new Type<>(Wayfarer.id("wake"));
        public static final StreamCodec<ByteBuf, Wake> STREAM_CODEC = ByteBufCodecs.BOOL.map(Wake::new, Wake::awake);

        @Override
        public Type<Wake> type() {
            return TYPE;
        }
    }

    private static State state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), DATA);
    }

    static void sleep(MinecraftServer server) {
        State state = state(server);
        state.asleep = true;
        state.setDirty();
    }

    public static boolean asleep(MinecraftServer server) {
        return server.getGameRules().getBoolean(Wayfarer.STORY) && state(server).asleep;
    }

    public static boolean awake(ServerPlayer player) {
        return awake.contains(player.getUUID());
    }

    public static void press(ServerPlayer player, boolean waking) {
        MinecraftServer server = player.server;
        if (!asleep(server)) {
            PacketDistributor.sendToPlayer(player, Page.AWAKE);
            return;
        }
        if (waking ? awake.add(player.getUUID()) : awake.remove(player.getUUID())) {
            settle(server, server.getPlayerList().getPlayers());
        }
    }

    public static void wake(MinecraftServer server) {
        rouse(server, server.getPlayerList().getPlayers());
    }

    private static void rouse(MinecraftServer server, List<ServerPlayer> players) {
        State state = state(server);
        if (!state.asleep) {
            return;
        }
        state.asleep = false;
        state.setDirty();
        awake.clear();
        hold(server);
        Component woke = Component.translatable("wayfarer.waking.woke");
        server.sendSystemMessage(woke);
        for (ServerPlayer player : players) {
            Arrivals.coming(player);
            PacketDistributor.sendToPlayer(player, Page.AWAKE);
            player.sendSystemMessage(woke);
        }
    }

    private static void settle(MinecraftServer server, List<ServerPlayer> players) {
        if (!players.isEmpty() && players.stream().allMatch(player -> awake.contains(player.getUUID()))) {
            rouse(server, players);
            return;
        }
        Page page = new Page(true, players.stream().map(player -> new Sleeper(player.getGameProfile().getName(), awake.contains(player.getUUID()))).toList());
        players.forEach(player -> PacketDistributor.sendToPlayer(player, page));
    }

    private static void hold(MinecraftServer server) {
        boolean hold = asleep(server) && server.getPlayerList().getPlayerCount() > 0;
        if (hold != held) {
            held = hold;
            server.tickRateManager().setFrozen(hold);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        awake.clear();
        held = false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_TICKS == 0) {
            hold(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && asleep(player.server)) {
            hold(player.server);
            settle(player.server, player.server.getPlayerList().getPlayers());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leaving)) {
            return;
        }
        awake.remove(leaving.getUUID());
        MinecraftServer server = leaving.server;
        if (!asleep(server)) {
            return;
        }
        List<ServerPlayer> staying = server.getPlayerList().getPlayers().stream().filter(player -> player != leaving).toList();
        if (staying.isEmpty()) {
            held = false;
            server.tickRateManager().setFrozen(false);
            return;
        }
        settle(server, staying);
    }
}
