package io.github.roman16.wayfarer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Cues {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, List<Layer>> CUES = load();
    private static final List<Pending> pending = new ArrayList<>();

    private Cues() {}

    private record Layer(ResourceLocation event, float pitch, float volume, int delay) {}

    private record Pending(UUID player, Layer layer, long at) {}

    public static void play(MinecraftServer server, String cue) {
        server.getPlayerList().getPlayers().forEach(player -> play(player, cue));
    }

    public static void play(ServerPlayer player, String cue) {
        List<Layer> layers = CUES.get(cue);
        if (layers == null) {
            throw new IllegalArgumentException("story/sounds.toml has no cue " + cue);
        }
        long now = player.server.getTickCount();
        layers.forEach(layer -> pending.add(new Pending(player.getUUID(), layer, now + layer.delay())));
        LOGGER.info("cue {} for {}", cue, player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        pending.removeIf(due -> {
            if (due.at() > now) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(due.player());
            if (player != null) {
                Layer layer = due.layer();
                player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(layer.event())), SoundSource.PLAYERS,
                        player.getX(), player.getEyeY(), player.getZ(), layer.volume(), layer.pitch(), player.getRandom().nextLong()));
            }
            return true;
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        pending.clear();
    }

    private static Map<String, List<Layer>> load() {
        try (var stream = Cues.class.getResourceAsStream("/wayfarer/cues.json")) {
            if (stream == null) {
                throw new IllegalStateException("cues.json is missing from the mod jar; run just generate");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                    entry -> entry.getValue().getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject)
                            .map(layer -> new Layer(ResourceLocation.parse(layer.get("event").getAsString()), layer.get("pitch").getAsFloat(),
                                    layer.get("volume").getAsFloat(), layer.get("delay").getAsInt()))
                            .toList()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
