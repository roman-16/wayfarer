package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Driver;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Redirect(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getLookVector()Lorg/joml/Vector3f;"))
    private static Vector3f wayfarer$evenDawn(Camera camera) {
        return Driver.panoramic() ? new Vector3f(0, 1, 0) : camera.getLookVector();
    }
}
