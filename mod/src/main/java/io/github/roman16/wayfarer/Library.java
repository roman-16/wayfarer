package io.github.roman16.wayfarer;

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
import net.minecraft.resources.ResourceLocation;

public record Library(Map<String, Book> books, List<String> memories, Map<ResourceLocation, String> people) {
    public record Book(String author, List<String> pages, String quest, String title) {}

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

    private static Library load() {
        try (var stream = Library.class.getResourceAsStream("/wayfarer/books.json")) {
            if (stream == null) {
                throw new IllegalStateException("books.json is missing from the mod jar; run just generate");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Book> books = new HashMap<>();
            json.getAsJsonObject("books").entrySet().forEach(entry -> {
                JsonObject book = entry.getValue().getAsJsonObject();
                books.put(entry.getKey(), new Book(
                        book.get("author").getAsString(),
                        book.getAsJsonArray("pages").asList().stream().map(page -> page.getAsString()).toList(),
                        book.get("quest").getAsString(),
                        book.get("title").getAsString()));
            });
            List<String> memories = json.getAsJsonArray("memories").asList().stream().map(memory -> memory.getAsString()).toList();
            Map<ResourceLocation, String> people = new HashMap<>();
            json.getAsJsonObject("people").entrySet()
                    .forEach(entry -> people.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsString()));
            return new Library(Map.copyOf(books), memories, Map.copyOf(people));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
