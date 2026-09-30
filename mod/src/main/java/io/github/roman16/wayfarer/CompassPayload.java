package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompassPayload(
        Mode mode,
        ResourceLocation target,
        int heat,
        int distance,
        int targetX,
        int targetZ,
        List<ResourceLocation> claimed,
        List<Marker> markers,
        boolean sight)
        implements CustomPacketPayload {

    public enum Mode {
        AIR,
        DIM,
        FAR,
        HIDDEN,
        LOST,
        NEAR
    }

    public enum Kind {
        GRAVE,
        LEAD,
        TEAMMATE
    }

    public record Marker(Kind kind, String name, int x, int z) {}

    public static final Type<CompassPayload> TYPE = new Type<>(Wayfarer.id("compass"));
    public static final StreamCodec<FriendlyByteBuf, CompassPayload> STREAM_CODEC = StreamCodec.ofMember(CompassPayload::write, CompassPayload::new);
    public static final ResourceLocation NOTHING = Wayfarer.id("nothing");

    public static CompassPayload hidden(List<ResourceLocation> claimed) {
        return new CompassPayload(Mode.HIDDEN, NOTHING, 0, 0, 0, 0, claimed, List.of(), false);
    }

    private CompassPayload(FriendlyByteBuf buf) {
        this(
                buf.readEnum(Mode.class),
                buf.readResourceLocation(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readList(marker -> new Marker(marker.readEnum(Kind.class), marker.readUtf(), marker.readVarInt(), marker.readVarInt())),
                buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeResourceLocation(target);
        buf.writeVarInt(heat);
        buf.writeVarInt(distance);
        buf.writeVarInt(targetX);
        buf.writeVarInt(targetZ);
        buf.writeCollection(claimed, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(markers, (out, marker) -> {
            out.writeEnum(marker.kind());
            out.writeUtf(marker.name());
            out.writeVarInt(marker.x());
            out.writeVarInt(marker.z());
        });
        buf.writeBoolean(sight);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
