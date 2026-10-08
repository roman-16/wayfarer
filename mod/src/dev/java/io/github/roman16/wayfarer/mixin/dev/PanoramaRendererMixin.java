package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Driver;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PanoramaRenderer.class)
abstract class PanoramaRendererMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FFF)V"), index = 2)
    private float wayfarer$still(float spin) {
        return Driver.batching() ? 0 : spin;
    }
}
