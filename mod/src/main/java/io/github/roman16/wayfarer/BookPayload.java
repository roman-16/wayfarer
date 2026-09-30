package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BookPayload(List<Component> pages) implements CustomPacketPayload {
    public static final Type<BookPayload> TYPE = new Type<>(Wayfarer.id("book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BookPayload> STREAM_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()).map(BookPayload::new, BookPayload::pages);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
