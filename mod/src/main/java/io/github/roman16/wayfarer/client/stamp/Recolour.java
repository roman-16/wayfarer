package io.github.roman16.wayfarer.client.stamp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.resources.ResourceLocation;

public final class Recolour {
    private static final String GUI = "textures/gui/";
    private static final String ATLAS = "textures/atlas/gui.png";
    private static final List<String> KEPT = List.of("textures/gui/hanging_signs/", "textures/gui/presets/", "textures/gui/realms/",
            "textures/gui/title/");
    private static final int LABEL = 0x404040;
    private static final int RGB = 0xFFFFFF;
    private static final String GRAINED = "ash";
    private static final int GRAIN_SHARE = 7;
    private static final int GRAIN_RANGE = 100;
    private static final Map<Integer, Shade> SHADES = shades();

    private Recolour() {
    }

    public static boolean guiAtlas(ResourceLocation atlas) {
        return atlas.getPath().equals(ATLAS);
    }

    public static void texture(ResourceLocation location, NativeImage image) {
        String path = location.getPath();
        if (location.getNamespace().equals(Wayfarer.MODID) || !path.startsWith(GUI) || KEPT.stream().anyMatch(path::startsWith)) {
            return;
        }
        paint(image);
    }

    public static void sprite(ResourceLocation name, NativeImage image) {
        if (!name.getNamespace().equals(Wayfarer.MODID)) {
            paint(image);
        }
    }

    public static int label(int color) {
        if ((color & RGB) != LABEL || !restyled(Minecraft.getInstance().screen)) {
            return color;
        }
        return (color & ~RGB) | (Stamp.LABEL & RGB);
    }

    private static boolean restyled(Screen screen) {
        return screen != null && !(screen instanceof BookViewScreen) && !(screen instanceof BookEditScreen) && !(screen instanceof AbstractSignEditScreen);
    }

    private static void paint(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = pixel >>> 24;
                if (alpha == 0) {
                    continue;
                }
                Shade shade = SHADES.get(rgb(pixel));
                if (shade != null) {
                    image.setPixelRGBA(x, y, abgr(alpha, shade.at(x, y)));
                }
            }
        }
    }

    private static int rgb(int abgr) {
        return (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF);
    }

    private static int abgr(int alpha, int rgb) {
        return alpha << 24 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }

    private static Map<Integer, Shade> shades() {
        JsonObject spec = Stamp.tokens();
        Map<Integer, Shade> found = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : spec.getAsJsonObject("recolour").entrySet()) {
            String target = entry.getValue().getAsString();
            int from = Integer.parseInt(entry.getKey().substring(1), 16);
            if (target.startsWith("#")) {
                int color = Integer.parseInt(target.substring(1), 16);
                found.put(from, new Shade(color, color, color));
            } else {
                String[] parts = target.split("\\.");
                int shade = Integer.parseInt(parts[1]);
                int base = Stamp.ramp(parts[0], shade) & RGB;
                if (parts[0].equals(GRAINED)) {
                    int last = spec.getAsJsonObject("ramps").getAsJsonArray(parts[0]).size() - 1;
                    found.put(from, new Shade(base, Stamp.ramp(parts[0], Math.max(0, shade - 1)) & RGB, Stamp.ramp(parts[0], Math.min(last, shade + 1)) & RGB));
                } else {
                    found.put(from, new Shade(base, base, base));
                }
            }
        }
        return found;
    }

    private record Shade(int base, int darker, int lighter) {
        int at(int x, int y) {
            int roll = Math.floorMod((x * 73856093) ^ (y * 19349663), GRAIN_RANGE);
            return roll < GRAIN_SHARE ? darker : roll >= GRAIN_RANGE - GRAIN_SHARE ? lighter : base;
        }
    }
}
