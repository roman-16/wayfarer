package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Banners {
    private Banners() {}

    public record Payload(Component label, Component title) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Wayfarer.id("banner"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Payload::label,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Payload::title,
                Payload::new);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    static void show(Component label, Component title) {
        PacketDistributor.sendToAllPlayers(new Payload(label, title));
    }

    static void show(ServerPlayer player, Component label, Component title) {
        PacketDistributor.sendToPlayer(player, new Payload(label, title));
    }
}
