package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.story.Progress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

public final class Release {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA = "data";
    private static final String EXTENSION = ".dat";
    private static final String STAMP = "wayfarer_release";
    private static final String BEGAN = "began";
    private static final String PLAYED = "played";
    private static final String MIGRATED = "migrated";
    private static final String LANGUAGE = "/assets/" + Wayfarer.MODID + "/lang/en_us.json";
    private static final List<Consumer<Map<String, CompoundTag>>> MIGRATIONS = List.of();

    private Release() {}

    public static String version() {
        return ModList.get().getModContainerById(Wayfarer.MODID).orElseThrow().getModInfo().getVersion().toString();
    }

    public static int major() {
        return major(version());
    }

    public static int major(String version) {
        return parts(version)[0];
    }

    public static int compare(String one, String other) {
        int[] left = parts(one);
        int[] right = parts(other);
        for (int index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                return Integer.compare(left[index], right[index]);
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        String[] split = version.split("\\.");
        int[] parts = new int[3];
        for (int index = 0; index < parts.length && index < split.length; index++) {
            parts[index] = Integer.parseInt(split[index].replaceAll("\\D.*", ""));
        }
        return parts;
    }

    public static Optional<Component> refusal(Path world) {
        Path stamp = file(world, STAMP);
        if (!Files.exists(stamp)) {
            return Files.exists(file(world, Progress.NAME)) ? Optional.of(Component.translatable("wayfarer.release.before", major())) : Optional.empty();
        }
        CompoundTag tag = read(stamp);
        if (tag.getInt(BEGAN) < major()) {
            return Optional.of(Component.translatable("wayfarer.release.older", tag.getInt(BEGAN)));
        }
        if (compare(tag.getString(PLAYED), version()) > 0) {
            return Optional.of(Component.translatable("wayfarer.release.newer", tag.getString(PLAYED)));
        }
        return Optional.empty();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        Path world = event.getServer().getWorldPath(LevelResource.ROOT);
        Optional<Component> refused = refusal(world);
        if (refused.isPresent()) {
            String said = english(refused.get());
            LOGGER.error(said);
            throw new IllegalStateException(said);
        }
        Path stamp = file(world, STAMP);
        CompoundTag tag = Files.exists(stamp) ? read(stamp) : fresh(world);
        int migrated = tag.getInt(MIGRATED);
        if (migrated < MIGRATIONS.size()) {
            migrate(world, migrated);
        }
        tag.putString(PLAYED, version());
        tag.putInt(MIGRATED, MIGRATIONS.size());
        write(stamp, tag);
    }

    private static String english(Component said) {
        if (!(said.getContents() instanceof TranslatableContents translatable)) {
            return said.getString();
        }
        JsonObject language = Bundled.read(LANGUAGE, reader -> JsonParser.parseReader(reader).getAsJsonObject());
        return String.format(language.get(translatable.getKey()).getAsString(), translatable.getArgs());
    }

    private static CompoundTag fresh(Path world) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(BEGAN, major());
        tag.putInt(MIGRATED, MIGRATIONS.size());
        return tag;
    }

    private static void migrate(Path world, int migrated) {
        Map<String, CompoundTag> data = new HashMap<>();
        try (Stream<Path> files = Files.list(world.resolve(DATA))) {
            files.filter(path -> path.getFileName().toString().startsWith(Wayfarer.MODID + "_") && path.getFileName().toString().endsWith(EXTENSION))
                    .forEach(path -> data.put(name(path), read(path)));
        } catch (IOException failure) {
            throw new IllegalStateException("migrating the story failed: cannot read " + world.resolve(DATA), failure);
        }
        for (int index = migrated; index < MIGRATIONS.size(); index++) {
            try {
                MIGRATIONS.get(index).accept(data);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("migrating the story failed: migration " + (index + 1) + " of " + MIGRATIONS.size(), failure);
            }
            LOGGER.info("migrated the story with migration {} of {}", index + 1, MIGRATIONS.size());
        }
        data.forEach((name, tag) -> write(file(world, name), tag));
    }

    private static String name(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - EXTENSION.length());
    }

    private static Path file(Path world, String name) {
        return world.resolve(DATA).resolve(name + EXTENSION);
    }

    private static CompoundTag read(Path file) {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read " + file, failure);
        }
    }

    private static void write(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write " + file, failure);
        }
    }

    public record Handshake() implements CustomPacketPayload {
        public static final Type<Handshake> TYPE = new Type<>(Wayfarer.id("release"));
        public static final StreamCodec<FriendlyByteBuf, Handshake> STREAM_CODEC = StreamCodec.unit(new Handshake());

        @Override
        public Type<Handshake> type() {
            return TYPE;
        }
    }
}
