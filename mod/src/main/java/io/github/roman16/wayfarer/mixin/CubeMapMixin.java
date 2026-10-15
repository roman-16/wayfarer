package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Panorama;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CubeMap.class)
abstract class CubeMapMixin {
    @Shadow
    @Final
    private ResourceLocation[] images;

    @Unique
    private boolean wayfarer$dressed;

    @Inject(method = "preload", at = @At("HEAD"))
    private void wayfarer$beforeLoading(TextureManager textures, Executor executor, CallbackInfoReturnable<CompletableFuture<Void>> info) {
        wayfarer$dress();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void wayfarer$beforeDrawing(Minecraft minecraft, float pitch, float yaw, float alpha, CallbackInfo info) {
        wayfarer$dress();
    }

    @Unique
    private void wayfarer$dress() {
        if (!wayfarer$dressed) {
            wayfarer$dressed = true;
            Panorama.dress(images);
        }
    }
}
