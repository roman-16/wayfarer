package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class Narration {
    public static final String NARRATOR = "narrator";
    public static final String SILENT = "silent";
    private static final String BOOK = "book:";
    private static final String GAZE = ".gaze";
    private static final int TICKS_PER_SECOND = 20;
    private static final Bundled<Index> INDEX = Bundled.json("/wayfarer/voices.json", Narration::load);
    private static final Map<UUID, String> chosen = new ConcurrentHashMap<>();

    private Narration() {}

    public record Clip(String path, float seconds) {
        public int ticks() {
            return Mth.ceil(seconds * TICKS_PER_SECOND);
        }

        Clip gazing() {
            return new Clip(path + GAZE, seconds);
        }
    }

    private record Index(Map<String, String> speakers, Map<String, String> chosen, Map<String, Map<String, Clip>> clips, Set<String> gaze,
            Map<String, Clip> pages) {}

    public record Chosen(String voice) implements CustomPacketPayload {
        public static final Type<Chosen> TYPE = new Type<>(Wayfarer.id("chosen_voice"));
        public static final StreamCodec<ByteBuf, Chosen> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Chosen::new, Chosen::voice);

        @Override
        public Type<Chosen> type() {
            return TYPE;
        }
    }

    public static Optional<Clip> said(@Nullable String speaker, String text, boolean gazing) {
        Index index = INDEX.get();
        String voice = index.speakers().get(speaker == null ? NARRATOR : speaker);
        Optional<Clip> clip = voice == null ? Optional.empty() : find(voice, text);
        return gazing && speaker != null && index.gaze().contains(speaker) ? clip.map(Clip::gazing) : clip;
    }

    public static Optional<Clip> chosen(String voice, String text) {
        String reader = INDEX.get().chosen().get(voice);
        return reader == null ? Optional.empty() : find(reader, text);
    }

    public static Optional<Clip> page(String text) {
        return Optional.ofNullable(INDEX.get().pages().get(text.strip()));
    }

    public static String voice(ServerPlayer player) {
        return chosen.getOrDefault(player.getUUID(), SILENT);
    }

    public static void choose(ServerPlayer player, String voice) {
        chosen.put(player.getUUID(), voice);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        chosen.remove(event.getEntity().getUUID());
    }

    private static Optional<Clip> find(String voice, String text) {
        Map<String, Clip> clips = INDEX.get().clips().get(voice);
        return clips == null ? Optional.empty() : Optional.ofNullable(clips.get(text.strip()));
    }

    private static Index load(JsonObject json) {
        Map<String, String> speakers = new HashMap<>();
        json.getAsJsonObject("speakers").entrySet().forEach(entry -> speakers.put(entry.getKey(), entry.getValue().getAsString()));
        Map<String, String> voices = new HashMap<>();
        json.getAsJsonObject("chosen").entrySet().forEach(entry -> voices.put(entry.getKey(), entry.getValue().getAsString()));
        Map<String, Map<String, Clip>> clips = new HashMap<>();
        json.getAsJsonObject("clips").entrySet().forEach(voice -> {
            Map<String, Clip> said = new HashMap<>();
            voice.getValue().getAsJsonObject().entrySet().forEach(line -> {
                JsonArray clip = line.getValue().getAsJsonArray();
                said.put(line.getKey(), new Clip(clip.get(0).getAsString(), clip.get(1).getAsFloat()));
            });
            clips.put(voice.getKey(), said);
        });
        Set<String> gaze = new HashSet<>();
        json.getAsJsonArray("gaze").forEach((JsonElement speaker) -> gaze.add(speaker.getAsString()));
        Map<String, Clip> pages = new HashMap<>();
        speakers.forEach((speaker, voice) -> {
            if (speaker.startsWith(BOOK) && clips.containsKey(voice)) {
                pages.putAll(clips.get(voice));
            }
        });
        return new Index(Map.copyOf(speakers), Map.copyOf(voices), Map.copyOf(clips), Set.copyOf(gaze), Map.copyOf(pages));
    }
}
