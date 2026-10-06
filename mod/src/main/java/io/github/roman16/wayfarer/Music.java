package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Music {
    private static final Map<UUID, String> looping = new HashMap<>();

    private Music() {}

    public record Play(String piece, boolean loop) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(Wayfarer.id("music"));
        public static final StreamCodec<ByteBuf, Play> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Play::piece, ByteBufCodecs.BOOL, Play::loop, Play::new);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    public static void play(MinecraftServer server, String piece, boolean loop) {
        server.getPlayerList().getPlayers().forEach(player -> play(player, piece, loop));
    }

    public static void play(ServerPlayer player, String piece, boolean loop) {
        if (loop) {
            looping.put(player.getUUID(), piece);
        } else {
            looping.remove(player.getUUID());
        }
        PacketDistributor.sendToPlayer(player, new Play(piece, loop));
    }

    @Nullable
    public static String looping(ServerPlayer player) {
        return looping.get(player.getUUID());
    }

    public static void stop(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(Music::stop);
    }

    public static void stop(ServerPlayer player) {
        looping.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new Play("", false));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && looping.containsKey(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, new Play(looping.get(player.getUUID()), true));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        looping.clear();
    }
}
