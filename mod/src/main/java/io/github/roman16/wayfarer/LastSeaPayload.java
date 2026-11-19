package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LastSeaPayload(int past) implements CustomPacketPayload {
    public static final Type<LastSeaPayload> TYPE = new Type<>(Wayfarer.id("last_sea"));
    public static final StreamCodec<ByteBuf, LastSeaPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LastSeaPayload::past, LastSeaPayload::new);
    public static final LastSeaPayload ASHORE = new LastSeaPayload(0);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
