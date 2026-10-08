package io.github.roman16.wayfarer.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.roman16.wayfarer.client.stamp.Recolour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.texture.SimpleTexture$TextureImage")
abstract class TextureImageMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void wayfarer$recolour(ResourceManager manager, ResourceLocation location, CallbackInfoReturnable<Object> callback) {
        NativeImage image = ((TextureImageAccessor) callback.getReturnValue()).wayfarer$image();
        if (image != null) {
            Recolour.texture(location, image);
        }
    }
}
