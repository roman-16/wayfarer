package io.github.roman16.wayfarer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompassPayload(Mode mode, ResourceLocation target, int heat, int distance, int targetX, int targetZ, int claimed)
        implements CustomPacketPayload {

    public enum Mode {
        AIR,
        FAR,
        NEAR,
        SILENT
    }

    public static final Type<CompassPayload> TYPE = new Type<>(Wayfarer.id("compass"));
    public static final StreamCodec<FriendlyByteBuf, CompassPayload> STREAM_CODEC = StreamCodec.ofMember(CompassPayload::write, CompassPayload::new);
    public static final ResourceLocation NOTHING = Wayfarer.id("nothing");

    public static CompassPayload silent(int claimed) {
        return new CompassPayload(Mode.SILENT, NOTHING, 0, 0, 0, 0, claimed);
    }

    private CompassPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Mode.class), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeResourceLocation(target);
        buf.writeVarInt(heat);
        buf.writeVarInt(distance);
        buf.writeVarInt(targetX);
        buf.writeVarInt(targetZ);
        buf.writeVarInt(claimed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
