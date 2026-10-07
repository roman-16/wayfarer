package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class Sounds {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Sound> SOUNDS = load();
    private static final Map<String, Function<MinecraftServer, Optional<BlockPos>>> loops = new HashMap<>();
    private static final Map<String, Long> due = new HashMap<>();
    private static final int CHECK_TICKS = 20;
    private static final int VANILLA_REACH = 16;

    private Sounds() {}

    private record Sound(Holder<SoundEvent> event, SoundSource slider, int reach, int loopTicks) {}

    public static void play(MinecraftServer server, String name) {
        server.getPlayerList().getPlayers().forEach(player -> play(player, name));
    }

    public static void play(ServerPlayer player, String name) {
        Sound sound = sound(name);
        player.connection.send(new ClientboundSoundPacket(sound.event(), sound.slider(), player.getX(), player.getEyeY(), player.getZ(), 1, 1,
                player.getRandom().nextLong()));
        LOGGER.info("sound {} for {}", name, player.getGameProfile().getName());
    }

    public static void play(ServerLevel level, Vec3 at, String name) {
        Sound sound = sound(name);
        if (!level.hasNearbyAlivePlayer(at.x, at.y, at.z, sound.reach() > 0 ? sound.reach() : VANILLA_REACH)) {
            return;
        }
        level.playSeededSound(null, at.x, at.y, at.z, sound.event(), sound.slider(), 1, 1, level.getRandom().nextLong());
        LOGGER.info("sound {} at {} {} {}", name, (int) at.x, (int) at.y, (int) at.z);
    }

    public static void loop(String name, Function<MinecraftServer, Optional<BlockPos>> where) {
        sound(name);
        loops.put(name, where);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % CHECK_TICKS != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        loops.forEach((name, where) -> {
            Sound sound = sound(name);
            Optional<BlockPos> at = where.apply(server);
            boolean heard = at.isPresent() && level.players().stream().anyMatch(player -> player.blockPosition().closerThan(at.get(), sound.reach()));
            if (!heard) {
                due.remove(name);
            } else if (due.getOrDefault(name, now) <= now) {
                play(level, at.get().getCenter(), name);
                due.put(name, now + sound.loopTicks());
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        due.clear();
    }

    private static Sound sound(String name) {
        Sound sound = SOUNDS.get(name);
        if (sound == null) {
            throw new IllegalArgumentException("story/sounds.toml has no sound " + name);
        }
        return sound;
    }

    private static Map<String, Sound> load() {
        try (var stream = Sounds.class.getResourceAsStream("/wayfarer/sounds.json")) {
            if (stream == null) {
                throw new IllegalStateException("sounds.json is missing from the mod jar; run just generate");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> {
                JsonObject spec = entry.getValue().getAsJsonObject();
                int reach = spec.has("reach") ? spec.get("reach").getAsInt() : 0;
                SoundEvent event = reach > 0 ? SoundEvent.createFixedRangeEvent(Wayfarer.id(entry.getKey()), reach)
                        : SoundEvent.createVariableRangeEvent(Wayfarer.id(entry.getKey()));
                SoundSource slider = SoundSource.valueOf(spec.get("slider").getAsString().toUpperCase(Locale.ROOT));
                return new Sound(Holder.direct(event), slider, reach, spec.has("loop_ticks") ? spec.get("loop_ticks").getAsInt() : 0);
            }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
