package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record JournalView(String json) implements CustomPacketPayload {
    public static final Type<JournalView> TYPE = new Type<>(Wayfarer.id("spike_journal"));
    public static final StreamCodec<ByteBuf, JournalView> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(JournalView::new, JournalView::json);

    @Override
    public Type<JournalView> type() {
        return TYPE;
    }
}
