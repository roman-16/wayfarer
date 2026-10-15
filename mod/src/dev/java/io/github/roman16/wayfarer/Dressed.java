package io.github.roman16.wayfarer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class Dressed {
    private static final Bundled<JsonObject> FACES = Bundled.json("/wayfarer/faces.json");
    private static final String TEXTURES = "https://textures.minecraft.net/texture/";
    private static final Map<String, PlayerSkin> WORN = new HashMap<>();

    private Dressed() {}

    @Nullable
    public static PlayerSkin worn(AbstractClientPlayer player) {
        return WORN.get(player.getGameProfile().getName());
    }

    static String wear(Minecraft minecraft, String argument) {
        String[] words = argument.split(" ");
        String name = words.length > 1 ? words[1] : minecraft.player.getGameProfile().getName();
        JsonObject face = face(words[0]).orElseThrow(() -> new IllegalArgumentException("generator/faces.toml has no face " + words[0]));
        WORN.put(name, new PlayerSkin(texture(minecraft, face), null, null, null, PlayerSkin.Model.WIDE, true));
        return "skin: " + name + " wears " + words[0];
    }

    private static Optional<JsonObject> face(String name) {
        return FACES.get().entrySet().stream().map(Map.Entry::getValue).map(JsonElement::getAsJsonObject).filter(group -> group.has(name))
                .map(group -> group.getAsJsonObject(name)).findFirst();
    }

    private static ResourceLocation texture(Minecraft minecraft, JsonObject face) {
        if (face.has("default")) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + face.get("default").getAsString() + ".png");
        }
        if (face.has("person")) {
            return Wayfarer.id("textures/entity/people/" + face.get("person").getAsString() + ".png");
        }
        String hash = face.get("texture").getAsString();
        ResourceLocation location = Wayfarer.id("dev/faces/" + hash);
        Path kept = Path.of(System.getenv().getOrDefault("XDG_CACHE_HOME", System.getProperty("user.home") + "/.cache"), "wayfarer", "faces", hash + ".png");
        minecraft.getTextureManager().register(location, new HttpTexture(kept.toFile(), TEXTURES + hash, DefaultPlayerSkin.getDefaultTexture(), true, null));
        return location;
    }
}
