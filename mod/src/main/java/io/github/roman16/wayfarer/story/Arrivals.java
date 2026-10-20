package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class Arrivals {
    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final Map<UUID, Long> coming = new HashMap<>();
    private static final Map<UUID, Long> joined = new HashMap<>();
    private static final Map<UUID, Long> arrived = new HashMap<>();

    private Arrivals() {}

    public record Arrived() implements CustomPacketPayload {
        public static final Type<Arrived> TYPE = new Type<>(Wayfarer.id("arrived"));
        public static final StreamCodec<ByteBuf, Arrived> STREAM_CODEC = StreamCodec.unit(new Arrived());

        @Override
        public Type<Arrived> type() {
            return TYPE;
        }
    }

    public static boolean there(ServerPlayer player) {
        Long since = coming.get(player.getUUID());
        if (since == null) {
            return true;
        }
        Balance.Talk talk = Balance.get().talk();
        long now = player.server.getTickCount();
        Long at = arrived.get(player.getUUID());
        return at != null ? now - at >= (long) talk.arriveSeconds() * TICKS_PER_SECOND : now - since >= (long) talk.arriveMostSeconds() * TICKS_PER_SECOND;
    }

    public static void arrive(ServerPlayer player) {
        if (!coming.containsKey(player.getUUID()) || arrived.containsKey(player.getUUID())) {
            return;
        }
        arrived.put(player.getUUID(), (long) player.server.getTickCount());
        long took = System.currentTimeMillis() - joined.getOrDefault(player.getUUID(), System.currentTimeMillis());
        Record.event(player.server, "arrived", player, line -> line.addProperty("seconds", took / MILLIS_PER_SECOND));
    }

    public static void here(ServerPlayer player) {
        coming.remove(player.getUUID());
        joined.remove(player.getUUID());
        arrived.remove(player.getUUID());
    }

    private static void coming(ServerPlayer player) {
        if (player.connection.hasChannel(Arrived.TYPE)) {
            coming.put(player.getUUID(), (long) player.server.getTickCount());
            joined.put(player.getUUID(), System.currentTimeMillis());
            arrived.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            coming(player);
        }
    }

    @SubscribeEvent
    public static void onChangedWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            coming(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            coming(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        coming.remove(event.getEntity().getUUID());
        joined.remove(event.getEntity().getUUID());
        arrived.remove(event.getEntity().getUUID());
    }
}
