package io.github.roman16.wayfarer.client;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

final class Afterward {
    private static final String KEY = "wayfarer.afterwards.";

    private Afterward() {}

    static Component title(JsonObject page) {
        return Component.translatable(KEY + page.get("people").getAsString());
    }

    static Component hall(JsonObject page) {
        return Component.translatable(KEY + page.get("people").getAsString() + "." + page.get("hall").getAsString());
    }

    static Component door(JsonObject page) {
        return Component.translatable(KEY + page.get("people").getAsString() + ".door." + page.get("door").getAsString());
    }
}
