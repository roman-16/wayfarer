package io.github.roman16.wayfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class Bundled<T> implements Supplier<T> {
    private static final List<Bundled<?>> ALL = new CopyOnWriteArrayList<>();
    private final Supplier<T> reader;
    @Nullable
    private volatile T value;

    private Bundled(Supplier<T> reader) {
        this.reader = reader;
    }

    public static <T> Bundled<T> of(Supplier<T> reader) {
        Bundled<T> bundled = new Bundled<>(reader);
        ALL.add(bundled);
        return bundled;
    }

    public static Bundled<JsonObject> json(String name) {
        return json(name, Function.identity());
    }

    public static <T> Bundled<T> json(String name, Function<JsonObject, T> parse) {
        return of(() -> parse.apply(read(name, reader -> JsonParser.parseReader(reader).getAsJsonObject())));
    }

    public static <T> T read(String name, Function<Reader, T> parse) {
        try (InputStream stream = Bundled.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException(name + " is missing from the mod jar; run just generate");
            }
            return parse.apply(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static void forget() {
        ALL.forEach(bundled -> bundled.value = null);
    }

    @Override
    public T get() {
        T current = value;
        if (current == null) {
            current = reader.get();
            value = current;
        }
        return current;
    }
}
