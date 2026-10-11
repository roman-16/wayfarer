package io.github.roman16.wayfarer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.monster.Guardian;

public final class DeepRenderer extends GuardianRenderer {
    private static final ResourceLocation TEXTURE = Wayfarer.id("textures/entity/deep.png");
    private static final ResourceLocation GUARDIAN = ResourceLocation.withDefaultNamespace("textures/entity/guardian.png");
    private static final Bundled<JsonObject> LOOK = Bundled.json("/wayfarer/deep.json");
    private static final float SHADOW_RADIUS = 0.5f;

    public DeepRenderer(EntityRendererProvider.Context context) {
        super(context, SHADOW_RADIUS, ModelLayers.GUARDIAN);
    }

    @Override
    public ResourceLocation getTextureLocation(Guardian entity) {
        return TEXTURE;
    }

    private record Look(int[] deep, int[] bone, int[] eye, int[] eyeBox, float[] spikeHue, float spikeSaturation) {}

    public static void reload(ResourceManager resources) {
        JsonObject look = LOOK.get();
        Look palette = new Look(colours(look, "deep"), colours(look, "bone"), colours(look, "eye"), ints(look, "eye_box"),
                new float[] {look.getAsJsonArray("spike_hue").get(0).getAsFloat(), look.getAsJsonArray("spike_hue").get(1).getAsFloat()},
                look.get("spike_saturation").getAsFloat());
        try (InputStream stream = resources.open(GUARDIAN); NativeImage image = NativeImage.read(stream)) {
            NativeImage deep = new NativeImage(image.getWidth(), image.getHeight(), true);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    deep.setPixelRGBA(x, y, recolour(palette, x, y, image.getPixelRGBA(x, y)));
                }
            }
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(deep));
        } catch (IOException failure) {
            throw new IllegalStateException("the deep's texture failed: the guardian's texture could not be read", failure);
        }
    }

    private static int[] colours(JsonObject look, String key) {
        return look.getAsJsonArray(key).asList().stream().mapToInt(colour -> Integer.parseInt(colour.getAsString().substring(1), 16)).toArray();
    }

    private static int[] ints(JsonObject look, String key) {
        return look.getAsJsonArray(key).asList().stream().mapToInt(JsonElement::getAsInt).toArray();
    }

    private static int recolour(Look look, int x, int y, int abgr) {
        int alpha = abgr >>> 24;
        if (alpha == 0) {
            return abgr;
        }
        int red = abgr & 0xFF;
        int green = abgr >> 8 & 0xFF;
        int blue = abgr >> 16 & 0xFF;
        int most = Math.max(red, Math.max(green, blue));
        int least = Math.min(red, Math.min(green, blue));
        float saturation = most == 0 ? 0 : (most - least) / (float) most;
        float hue = hue(red, green, blue, most, least);
        float light = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f;
        int[] box = look.eyeBox();
        int[] ramp = x >= box[0] && y >= box[1] && x < box[2] && y < box[3] ? look.eye()
                : hue >= look.spikeHue()[0] && hue <= look.spikeHue()[1] && saturation >= look.spikeSaturation() ? look.bone() : look.deep();
        int rgb = along(ramp, light);
        return alpha << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
    }

    private static float hue(int red, int green, int blue, int most, int least) {
        if (most == least) {
            return 0;
        }
        float span = most - least;
        float hue = most == red ? (green - blue) / span : most == green ? 2 + (blue - red) / span : 4 + (red - green) / span;
        return (hue * 60 + 360) % 360;
    }

    private static int along(int[] ramp, float light) {
        float at = Math.max(0, Math.min(1, light)) * (ramp.length - 1);
        int low = Math.min(ramp.length - 2, (int) at);
        float mix = at - low;
        int from = ramp[low];
        int to = ramp[low + 1];
        int red = Math.round((from >> 16 & 0xFF) * (1 - mix) + (to >> 16 & 0xFF) * mix);
        int green = Math.round((from >> 8 & 0xFF) * (1 - mix) + (to >> 8 & 0xFF) * mix);
        int blue = Math.round((from & 0xFF) * (1 - mix) + (to & 0xFF) * mix);
        return red << 16 | green << 8 | blue;
    }
}
