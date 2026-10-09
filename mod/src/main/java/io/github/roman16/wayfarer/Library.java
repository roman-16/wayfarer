package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record Library(Map<String, Book> books, Map<ResourceLocation, String> people) {

    public record Book(String author, List<String> pages, String title) {}

    private static final Bundled<Library> LIBRARY = Bundled.json("/wayfarer/library.json", Library::load);

    public static Library get() {
        return LIBRARY.get();
    }

    public Optional<Book> book(String id) {
        return Optional.ofNullable(books.get(id));
    }

    public Optional<String> find(String title, String author) {
        return books.entrySet().stream().filter(entry -> entry.getValue().title().equals(title) && entry.getValue().author().equals(author))
                .map(Map.Entry::getKey).findFirst();
    }

    public Optional<Book> people(ResourceLocation shrine) {
        return Optional.ofNullable(people.get(shrine)).map(books::get);
    }

    private static Library load(JsonObject json) {
        Map<String, Book> books = new HashMap<>();
        json.getAsJsonObject("books").entrySet().forEach(entry -> {
            JsonObject book = entry.getValue().getAsJsonObject();
            books.put(entry.getKey(), new Book(book.get("author").getAsString(), strings(book.getAsJsonArray("pages")), book.get("title").getAsString()));
        });
        Map<ResourceLocation, String> people = new HashMap<>();
        json.getAsJsonObject("people").entrySet()
                .forEach(entry -> people.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsString()));
        return new Library(Map.copyOf(books), Map.copyOf(people));
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }
}
