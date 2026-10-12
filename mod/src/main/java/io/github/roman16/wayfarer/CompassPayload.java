package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompassPayload(
        Mode mode,
        int heat,
        List<ResourceLocation> claimed,
        List<Marker> markers,
        Shelter shelter,
        boolean sight)
        implements CustomPacketPayload {

    public enum Mode {
        AIR,
        BARE,
        COLD,
        DIM,
        FAR,
        HIDDEN,
        LOST
    }

    public enum Kind {
        GOAL,
        GRAVE,
        LEAD,
        TEAMMATE,
        TENT
    }

    public enum Shelter {
        FIRE,
        NONE,
        HALL
    }

    public record Marker(Kind kind, String name, int x, int z) {}

    public static final Type<CompassPayload> TYPE = new Type<>(Wayfarer.id("compass"));
    public static final StreamCodec<FriendlyByteBuf, CompassPayload> STREAM_CODEC = StreamCodec.ofMember(CompassPayload::write, CompassPayload::new);
    public static CompassPayload hidden(List<ResourceLocation> claimed) {
        return new CompassPayload(Mode.HIDDEN, 0, claimed, List.of(), Shelter.NONE, false);
    }

    private CompassPayload(FriendlyByteBuf buf) {
        this(
                buf.readEnum(Mode.class),
                buf.readVarInt(),
                buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readList(marker -> new Marker(marker.readEnum(Kind.class), marker.readUtf(), marker.readVarInt(), marker.readVarInt())),
                buf.readEnum(Shelter.class),
                buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeVarInt(heat);
        buf.writeCollection(claimed, FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(markers, (out, marker) -> {
            out.writeEnum(marker.kind());
            out.writeUtf(marker.name());
            out.writeVarInt(marker.x());
            out.writeVarInt(marker.z());
        });
        buf.writeEnum(shelter);
        buf.writeBoolean(sight);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
