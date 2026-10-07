package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Banners {
    private Banners() {}

    public record Payload(Component text) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Wayfarer.id("banner"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                ComponentSerialization.TRUSTED_STREAM_CODEC.map(Payload::new, Payload::text);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    static void show(Component text) {
        PacketDistributor.sendToAllPlayers(new Payload(text));
    }
}
