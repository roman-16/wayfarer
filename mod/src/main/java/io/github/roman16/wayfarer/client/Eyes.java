package io.github.roman16.wayfarer.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Faces;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class Eyes extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation ENDERMAN_EYES = ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman_eyes.png");
    private static final int FULL = 255;
    private static final Map<Look, Optional<ResourceLocation>> GLOWS = new HashMap<>();
    private static final int TEXTURE_2D = 3553;
    private static final int TEXTURE_HEIGHT = 4097;
    private static final int TEXTURE_WIDTH = 4096;
    private static int iris;
    private static float strength;
    private static int white;

    private record Look(ResourceLocation skin, boolean hat) {}

    private Eyes(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void register(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : PlayerSkin.Model.values()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new Eyes(renderer));
            }
        }
    }

    static void remember(String view) {
        JsonObject parsed = JsonParser.parseString(view).getAsJsonObject();
        strength = parsed.has("eyes") ? parsed.get("eyes").getAsFloat() : 0;
    }

    static void forget() {
        strength = 0;
        drop();
    }

    static void reload(ResourceManager resources) {
        drop();
        iris = 0;
        white = 0;
        try (InputStream stream = resources.open(ENDERMAN_EYES); NativeImage image = NativeImage.read(stream)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int colour = FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
                    if (FastColor.ARGB32.alpha(colour) == FULL) {
                        white = white == 0 || brightness(colour) > brightness(white) ? colour : white;
                        iris = iris == 0 || saturation(colour) > saturation(iris) ? colour : iris;
                    }
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("the glowing eyes failed: the enderman's eyes could not be read", failure);
        }
    }

    private static int brightness(int colour) {
        return FastColor.ARGB32.red(colour) + FastColor.ARGB32.green(colour) + FastColor.ARGB32.blue(colour);
    }

    private static int saturation(int colour) {
        int high = Math.max(FastColor.ARGB32.red(colour), Math.max(FastColor.ARGB32.green(colour), FastColor.ARGB32.blue(colour)));
        int low = Math.min(FastColor.ARGB32.red(colour), Math.min(FastColor.ARGB32.green(colour), FastColor.ARGB32.blue(colour)));
        return high - low;
    }

    static void tick(Minecraft minecraft) {
        if (strength <= 0 || minecraft.level == null) {
            return;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            Look look = look(player);
            if (!GLOWS.containsKey(look)) {
                int[] skin = read(minecraft, look);
                if (skin != null) {
                    GLOWS.put(look, glow(minecraft, skin, look.hat));
                }
            }
        }
    }

    private static Look look(AbstractClientPlayer player) {
        return new Look(player.getSkin().texture(), player.isModelPartShown(PlayerModelPart.HAT));
    }

    private static void drop() {
        GLOWS.values().forEach(glow -> glow.ifPresent(Minecraft.getInstance().getTextureManager()::release));
        GLOWS.clear();
    }

    @Nullable
    private static int[] read(Minecraft minecraft, Look look) {
        AbstractTexture texture = minecraft.getTextureManager().getTexture(look.skin);
        RenderSystem.bindTexture(texture.getId());
        int width = GlStateManager._getTexLevelParameter(TEXTURE_2D, 0, TEXTURE_WIDTH);
        int height = GlStateManager._getTexLevelParameter(TEXTURE_2D, 0, TEXTURE_HEIGHT);
        int[] skin = width == 0 ? null : new int[width == Faces.SKIN && height == Faces.SKIN ? Faces.SKIN * Faces.SKIN : 0];
        if (skin != null && skin.length > 0) {
            try (NativeImage image = new NativeImage(width, height, true)) {
                image.downloadTexture(0, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        skin[y * width + x] = FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
                    }
                }
            }
        }
        RenderSystem.bindTexture(0);
        return skin;
    }

    private static Optional<ResourceLocation> glow(Minecraft minecraft, int[] skin, boolean hat) {
        List<Faces.Pixel> eyes = skin.length == 0 ? List.of() : Faces.eyes(skin, hat);
        if (eyes.isEmpty()) {
            return Optional.empty();
        }
        NativeImage image = new NativeImage(Faces.SKIN, Faces.SKIN, true);
        for (Faces.Pixel eye : eyes) {
            image.setPixelRGBA((eye.hat() ? Faces.HAT_X : Faces.FACE_X) + eye.x(), Faces.FACE_Y + eye.y(), FastColor.ABGR32.fromArgb32(eye.white() ? white : iris));
        }
        ResourceLocation location = Wayfarer.id("eyes/" + GLOWS.size());
        minecraft.getTextureManager().register(location, new DynamicTexture(image));
        return Optional.of(location);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float swing, float swingAmount, float partialTick,
                       float age, float headYaw, float headPitch) {
        if (strength <= 0 || player.isInvisible()) {
            return;
        }
        Optional<ResourceLocation> glow = GLOWS.getOrDefault(look(player), Optional.empty());
        if (glow.isEmpty()) {
            return;
        }
        int dark = Balance.get().shine().glowLight();
        int seen = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition(partialTick)));
        float shine = strength * Math.max(0, dark - seen) / dark;
        if (shine <= 0) {
            return;
        }
        int channel = Math.round(FULL * shine);
        int colour = FastColor.ARGB32.color(FULL, channel, channel, channel);
        RenderType type = RenderType.eyes(glow.get());
        getParentModel().head.render(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
        getParentModel().hat.render(pose, buffers.getBuffer(type), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }
}
