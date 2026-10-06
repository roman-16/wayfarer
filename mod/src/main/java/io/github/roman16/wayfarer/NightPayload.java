package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NightPayload(boolean sleepless) implements CustomPacketPayload {
    public static final Type<NightPayload> TYPE = new Type<>(Wayfarer.id("night"));
    public static final StreamCodec<ByteBuf, NightPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, NightPayload::sleepless, NightPayload::new);
    public static final NightPayload CALM = new NightPayload(false);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
