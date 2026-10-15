package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class Record {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "wayfarer/record";
    private static final String EXTENSION = ".jsonl";

    private Record() {}

    public static void frames(ServerPlayer player, Frames frames) {
        if (!player.server.isDedicatedServer()) {
            return;
        }
        JsonObject line = new JsonObject();
        line.addProperty("event", "frames");
        line.addProperty("player", player.getGameProfile().getName());
        line.addProperty("fps", frames.fps());
        line.addProperty("low", frames.low());
        line.addProperty("worst_ms", frames.worstMillis());
        line.addProperty("seconds", frames.seconds());
        line.addProperty("preset", frames.preset());
        line.addProperty("shaders", frames.shaders());
        line.addProperty("far_view", frames.farView());
        line.addProperty("heap_mb", frames.heapMegabytes());
        line.addProperty("heap_max_mb", frames.heapMaxMegabytes());
        write(player.server.getServerDirectory().resolve(FOLDER), line);
    }

    private static void write(Path folder, JsonObject line) {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        JsonObject timed = new JsonObject();
        timed.addProperty("time", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        line.entrySet().forEach(entry -> timed.add(entry.getKey(), entry.getValue()));
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(now.format(DateTimeFormatter.ISO_LOCAL_DATE) + EXTENSION), timed + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.error("record: cannot write to {}", folder, exception);
        }
    }

    public record Frames(int fps, int low, int worstMillis, int seconds, String preset, boolean shaders, int farView, int heapMegabytes,
                         int heapMaxMegabytes) implements CustomPacketPayload {
        public static final Type<Frames> TYPE = new Type<>(Wayfarer.id("frames"));
        public static final StreamCodec<FriendlyByteBuf, Frames> STREAM_CODEC = StreamCodec.ofMember(Frames::write, Frames::new);

        private Frames(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(fps);
            buf.writeVarInt(low);
            buf.writeVarInt(worstMillis);
            buf.writeVarInt(seconds);
            buf.writeUtf(preset);
            buf.writeBoolean(shaders);
            buf.writeVarInt(farView);
            buf.writeVarInt(heapMegabytes);
            buf.writeVarInt(heapMaxMegabytes);
        }

        @Override
        public Type<Frames> type() {
            return TYPE;
        }
    }
}
