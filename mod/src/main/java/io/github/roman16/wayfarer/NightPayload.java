package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record NightPayload(boolean horde, boolean bloodMoon) implements CustomPacketPayload {
    public static final Type<NightPayload> TYPE = new Type<>(Wayfarer.id("night"));
    public static final StreamCodec<ByteBuf, NightPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, NightPayload::horde, ByteBufCodecs.BOOL, NightPayload::bloodMoon, NightPayload::new);
    public static final NightPayload CALM = new NightPayload(false, false);

    public boolean hard() {
        return horde || bloodMoon;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
