package io.github.roman16.wayfarer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GazePayload(boolean watching, float fill, int veil, int capacitySeconds) implements CustomPacketPayload {
    public static final Type<GazePayload> TYPE = new Type<>(Wayfarer.id("gaze"));
    public static final StreamCodec<FriendlyByteBuf, GazePayload> STREAM_CODEC = StreamCodec.ofMember(GazePayload::write, GazePayload::new);
    public static final GazePayload CLOSED = new GazePayload(false, 0, 0, 0);

    private GazePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(watching);
        buf.writeFloat(fill);
        buf.writeVarInt(veil);
        buf.writeVarInt(capacitySeconds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
