package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Still;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    private int ticks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void wayfarer$still(CallbackInfo info) {
        if (Still.held()) {
            ticks = 0;
        }
    }
}
