package io.github.roman16.wayfarer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class Gifts {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Set<String> held = Set.of();

    private Gifts() {}

    public static void remember(String view) {
        JsonObject parsed = JsonParser.parseString(view).getAsJsonObject();
        Set<String> now = parsed.has("gifts") ? parsed.getAsJsonArray("gifts").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet())
                : Set.of();
        if (!now.equals(held)) {
            LOGGER.info("gifts held: {}", now);
        }
        held = now;
    }

    public static void forget() {
        held = Set.of();
    }

    public static boolean has(String gift) {
        return held.contains(gift);
    }
}
