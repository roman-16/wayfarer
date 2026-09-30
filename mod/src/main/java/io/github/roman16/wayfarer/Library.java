package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record Library(
        Map<String, Book> books,
        Map<String, Lead> leads,
        List<String> memories,
        Map<String, List<String>> notes,
        Map<ResourceLocation, String> people,
        Map<ResourceLocation, String> places,
        Map<String, Scene> scenes,
        Map<String, Speaker> speakers,
        List<Step> steps) {

    public record Book(String author, List<String> pages, String quest, String title) {}

    public record Item(ResourceLocation item, int count, String name) {
        public ItemStack stack() {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item), count);
            if (!name.isEmpty()) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
            }
            return stack;
        }
    }

    public record Lead(
            String camp,
            List<String> find,
            String foundTask,
            String givenTask,
            List<Item> items,
            String line,
            String mark,
            String page,
            String structures) {}

    public record Scene(String book, List<String> lines, String speaker) {}

    public record Name(String after, String name) {}

    public record Speaker(ChatFormatting color, List<Name> names) {}

    public record Step(
            List<String> after,
            List<String> found,
            String id,
            String lead,
            List<String> lines,
            String quest,
            int shards,
            boolean slain,
            String speaker,
            Optional<List<Item>> visit) {}

    public static final Library INSTANCE = load();

    public Optional<Book> book(String id) {
        return Optional.ofNullable(books.get(id));
    }

    public String memory(int index) {
        return memories.get(Math.clamp(index, 0, memories.size() - 1));
    }

    public Optional<Book> people(ResourceLocation shrine) {
        return Optional.ofNullable(people.get(shrine)).map(books::get);
    }

    public List<String> notes(String trigger) {
        return notes.getOrDefault(trigger, List.of());
    }

    private static Library load() {
        try (var stream = Library.class.getResourceAsStream("/wayfarer/library.json")) {
            if (stream == null) {
                throw new IllegalStateException("library.json is missing from the mod jar; run just generate");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Book> books = new HashMap<>();
            json.getAsJsonObject("books").entrySet().forEach(entry -> {
                JsonObject book = entry.getValue().getAsJsonObject();
                books.put(entry.getKey(), new Book(
                        book.get("author").getAsString(), strings(book.getAsJsonArray("pages")), book.get("quest").getAsString(), book.get("title").getAsString()));
            });
            Map<String, Lead> leads = new HashMap<>();
            json.getAsJsonObject("leads").entrySet().forEach(entry -> {
                JsonObject lead = entry.getValue().getAsJsonObject();
                leads.put(entry.getKey(), new Lead(
                        text(lead, "camp"),
                        strings(lead.getAsJsonArray("find")),
                        text(lead, "found_task"),
                        text(lead, "given_task"),
                        items(lead.getAsJsonArray("items")),
                        text(lead, "line"),
                        text(lead, "mark"),
                        text(lead, "page"),
                        text(lead, "structures")));
            });
            Map<String, List<String>> notes = new HashMap<>();
            json.getAsJsonObject("notes").entrySet().forEach(entry -> notes.put(entry.getKey(), strings(entry.getValue().getAsJsonArray())));
            Map<ResourceLocation, String> people = new HashMap<>();
            json.getAsJsonObject("people").entrySet()
                    .forEach(entry -> people.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsString()));
            Map<ResourceLocation, String> places = new HashMap<>();
            json.getAsJsonObject("places").entrySet()
                    .forEach(entry -> places.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsString()));
            Map<String, Scene> scenes = new HashMap<>();
            json.getAsJsonObject("scenes").entrySet().forEach(entry -> {
                JsonObject scene = entry.getValue().getAsJsonObject();
                scenes.put(entry.getKey(), new Scene(text(scene, "book"), strings(scene.getAsJsonArray("lines")), text(scene, "speaker")));
            });
            Map<String, Speaker> speakers = new HashMap<>();
            json.getAsJsonObject("speakers").entrySet().forEach(entry -> {
                JsonObject speaker = entry.getValue().getAsJsonObject();
                List<Name> names = speaker.getAsJsonArray("names").asList().stream()
                        .map(name -> new Name(text(name.getAsJsonObject(), "after"), text(name.getAsJsonObject(), "name")))
                        .toList();
                speakers.put(entry.getKey(), new Speaker(ChatFormatting.getByName(text(speaker, "color")), names));
            });
            List<Step> steps = json.getAsJsonArray("steps").asList().stream().map(element -> {
                JsonObject step = element.getAsJsonObject();
                JsonElement visit = step.get("visit");
                return new Step(
                        strings(step.getAsJsonArray("after")),
                        strings(step.getAsJsonArray("found")),
                        text(step, "id"),
                        text(step, "lead"),
                        strings(step.getAsJsonArray("lines")),
                        text(step, "quest"),
                        step.get("shards").getAsInt(),
                        step.get("slain").getAsBoolean(),
                        text(step, "speaker"),
                        visit.isJsonNull() ? Optional.empty() : Optional.of(items(visit.getAsJsonArray())));
            }).toList();
            return new Library(
                    Map.copyOf(books),
                    Map.copyOf(leads),
                    strings(json.getAsJsonArray("memories")),
                    Map.copyOf(notes),
                    Map.copyOf(people),
                    Map.copyOf(places),
                    Map.copyOf(scenes),
                    Map.copyOf(speakers),
                    steps);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static List<Item> items(JsonArray array) {
        return array.asList().stream().map(element -> {
            JsonObject item = element.getAsJsonObject();
            return new Item(ResourceLocation.parse(text(item, "item")), item.get("count").getAsInt(), text(item, "name"));
        }).toList();
    }
}
