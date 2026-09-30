package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BookPayload(List<String> pages) implements CustomPacketPayload {
    public static final Type<BookPayload> TYPE = new Type<>(Wayfarer.id("book"));
    public static final StreamCodec<FriendlyByteBuf, BookPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(BookPayload::new, BookPayload::pages).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
