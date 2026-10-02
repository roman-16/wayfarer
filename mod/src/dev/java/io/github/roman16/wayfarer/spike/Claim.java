package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record Claim(String reward) implements CustomPacketPayload {
    public static final Type<Claim> TYPE = new Type<>(Wayfarer.id("spike_claim"));
    public static final StreamCodec<ByteBuf, Claim> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Claim::new, Claim::reward);

    @Override
    public Type<Claim> type() {
        return TYPE;
    }
}
