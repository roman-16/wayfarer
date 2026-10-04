package io.github.roman16.wayfarer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Reports {
    private static final int OWNER = 4;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private Reports() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("registries")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.argument("folder", StringArgumentType.greedyString())
                                .executes(context -> report(context.getSource(),
                                        Path.of(StringArgumentType.getString(context, "folder")))))));
    }

    private static int report(CommandSourceStack source, Path folder) {
        try {
            Counts counts = write(source.getServer(), folder);
            source.sendSuccess(() -> Component.literal("wrote %d registries (%d with their content), %d tags and %d recipes to %s; %d entries could not be encoded"
                    .formatted(counts.registries, counts.contents, counts.tags, counts.recipes, folder, counts.failures)), false);
            return 1;
        } catch (IOException | UncheckedIOException exception) {
            source.sendFailure(Component.literal("registries failed: could not write the registries to " + folder + ": " + exception.getMessage()));
            return 0;
        }
    }

    private static Counts write(MinecraftServer server, Path folder) throws IOException {
        Path partial = folder.resolveSibling(folder.getFileName() + ".partial");
        delete(partial);
        RegistryAccess access = server.reloadableRegistries().get();
        RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
        Map<ResourceKey<?>, Codec<?>> codecs = new HashMap<>();
        DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().forEach(data -> codecs.put(data.key(), data.elementCodec()));
        LootDataType.values().forEach(type -> codecs.put(type.registryKey(), type.codec()));
        Counts counts = new Counts();
        for (RegistryAccess.RegistryEntry<?> entry : access.registries().toList()) {
            registry(partial, entry, codecs.get(entry.key()), ops, counts);
        }
        JsonObject recipes = new JsonObject();
        server.getRecipeManager().getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .forEach(holder -> recipes.add(holder.id().toString(), encode(Recipe.CODEC, holder.value(), ops, counts)));
        counts.recipes = server.getRecipeManager().getRecipes().size();
        save(partial.resolve("recipes.json"), recipes);
        delete(folder);
        Files.move(partial, folder);
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static <T> void registry(Path root, RegistryAccess.RegistryEntry<T> entry, Codec<?> codec, RegistryOps<JsonElement> ops, Counts counts) throws IOException {
        ResourceLocation name = entry.key().location();
        Registry<T> registry = entry.value();
        Stream<ResourceLocation> ids = registry.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString));
        JsonElement content;
        if (codec == null) {
            JsonArray list = new JsonArray();
            ids.forEach(id -> list.add(id.toString()));
            content = list;
        } else {
            JsonObject entries = new JsonObject();
            ids.forEach(id -> entries.add(id.toString(), encode((Codec<T>) codec, registry.get(id), ops, counts)));
            content = entries;
            counts.contents++;
        }
        save(file(root.resolve("registries"), name), content);
        counts.registries++;
        JsonObject tags = new JsonObject();
        registry.getTags()
                .sorted(Comparator.comparing(pair -> pair.getFirst().location().toString()))
                .forEach(pair -> {
                    JsonArray members = new JsonArray();
                    pair.getSecond().stream()
                            .map(Reports::id)
                            .sorted()
                            .forEach(members::add);
                    tags.add("#" + pair.getFirst().location(), members);
                    counts.tags++;
                });
        if (!tags.isEmpty()) {
            save(file(root.resolve("tags"), name), tags);
        }
    }

    private static <T> String id(Holder<T> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("<inline>");
    }

    private static <T> JsonElement encode(Codec<T> codec, T value, RegistryOps<JsonElement> ops, Counts counts) {
        DataResult<JsonElement> result;
        try {
            result = codec.encodeStart(ops, value);
        } catch (RuntimeException exception) {
            return failure(String.valueOf(exception), counts);
        }
        return result.result().orElseGet(() -> failure(result.error().map(error -> error.message()).orElse("unknown"), counts));
    }

    private static JsonElement failure(String message, Counts counts) {
        counts.failures++;
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }

    private static Path file(Path root, ResourceLocation name) {
        return root.resolve(name.getNamespace()).resolve(name.getPath() + ".json");
    }

    private static void save(Path path, JsonElement content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(content));
    }

    private static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static final class Counts {
        private int registries;
        private int contents;
        private int tags;
        private int recipes;
        private int failures;
    }
}
