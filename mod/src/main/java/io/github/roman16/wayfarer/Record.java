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
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

public final class Record {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "wayfarer/record";
    private static final String EXTENSION = ".jsonl";
    private static final String PLAYER = "player";

    private Record() {}

    public static void event(MinecraftServer server, String event, @Nullable ServerPlayer player, Consumer<JsonObject> fields) {
        JsonObject line = new JsonObject();
        line.addProperty("event", event);
        if (player != null) {
            line.addProperty(PLAYER, player.getGameProfile().getName());
            line.addProperty("dimension", player.level().dimension().location().toString());
            line.addProperty("x", player.getBlockX());
            line.addProperty("y", player.getBlockY());
            line.addProperty("z", player.getBlockZ());
        }
        fields.accept(line);
        write(server.getWorldPath(LevelResource.ROOT).resolve(FOLDER), line);
    }

    public static void event(MinecraftServer server, String event, @Nullable ServerPlayer player) {
        event(server, event, player, line -> {});
    }

    public static void frames(ServerPlayer player, Frames frames) {
        event(player.server, "frames", player, line -> {
            line.addProperty("fps", frames.fps());
            line.addProperty("low", frames.low());
            frames.graphicsCardPercent().ifPresent(percent -> line.addProperty("gpu", percent));
            if (frames.onBattery()) {
                line.addProperty("battery", true);
            }
            line.addProperty("worst_ms", frames.worstMillis());
            line.addProperty("seconds", frames.seconds());
            line.addProperty("preset", frames.preset());
            line.addProperty("shaders", frames.shaders());
            line.addProperty("far_view", frames.farView());
            line.addProperty("heap_mb", frames.heapMegabytes());
            line.addProperty("heap_max_mb", frames.heapMaxMegabytes());
        });
    }

    public static void opened(ServerPlayer player, Opened opened) {
        event(player.server, "screen", player, line -> line.addProperty("screen", opened.screen()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event(player.server, "joined", player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event(player.server, "left", player);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        event(event.getPlayer().server, "chat", event.getPlayer(), line -> line.addProperty("text", event.getRawText()));
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            event(player.server, "command", player, line -> line.addProperty("text", event.getParseResults().getReader().getString()));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entity killer = event.getSource().getEntity();
            event(player.server, "death", player, line -> {
                line.addProperty("cause", event.getSource().getLocalizedDeathMessage(player).getString());
                if (killer != null) {
                    line.addProperty("by", BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString());
                }
            });
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            event(player.server, "respawn", player);
        }
    }

    @SubscribeEvent
    public static void onChangedWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event(player.server, "world", player, line -> line.addProperty("from", event.getFrom().location().toString()));
        }
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        event(player.server, "sleep", player, line -> {
            if (event.getProblem() != null) {
                line.addProperty("refused", event.getProblem().name().toLowerCase(Locale.ROOT));
            }
        });
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getPlacedBlock().getBlock() instanceof CampfireBlock
                && event.getPlacedBlock().getValue(CampfireBlock.LIT)) {
            event(player.server, "fire", player);
        }
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

    public record Opened(String screen) implements CustomPacketPayload {
        public static final Type<Opened> TYPE = new Type<>(Wayfarer.id("opened"));
        public static final StreamCodec<FriendlyByteBuf, Opened> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Opened::new, Opened::screen).cast();

        @Override
        public Type<Opened> type() {
            return TYPE;
        }
    }

    public record Frames(int fps, int low, Optional<Integer> graphicsCardPercent, boolean onBattery, int worstMillis, int seconds, String preset,
                         boolean shaders, int farView, int heapMegabytes, int heapMaxMegabytes) implements CustomPacketPayload {
        public static final Type<Frames> TYPE = new Type<>(Wayfarer.id("frames"));
        public static final StreamCodec<FriendlyByteBuf, Frames> STREAM_CODEC = StreamCodec.ofMember(Frames::write, Frames::new);

        private Frames(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), buf.readOptional(FriendlyByteBuf::readVarInt), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(fps);
            buf.writeVarInt(low);
            buf.writeOptional(graphicsCardPercent, FriendlyByteBuf::writeVarInt);
            buf.writeBoolean(onBattery);
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
