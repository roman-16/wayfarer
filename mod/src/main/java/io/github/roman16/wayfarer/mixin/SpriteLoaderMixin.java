package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.stamp.Recolour;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteLoader.class)
abstract class SpriteLoaderMixin {
    @Shadow
    @Final
    private ResourceLocation location;

    @Inject(method = "stitch", at = @At("HEAD"))
    private void wayfarer$recolour(List<SpriteContents> contents, int mipLevel, Executor executor, CallbackInfoReturnable<SpriteLoader.Preparations> callback) {
        if (Recolour.guiAtlas(location)) {
            contents.forEach(sprite -> Recolour.sprite(sprite.name(), sprite.getOriginalImage()));
        }
    }
}
