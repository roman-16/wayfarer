package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Graphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.payangar.moredarkness.darkness.DarknessCalculator", remap = false)
abstract class MoreDarknessLightMixin {
    @Shadow
    private static boolean active;

    @Inject(method = "updateLuminance", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$leaveNightToShader(CallbackInfo info) {
        if (Graphics.shaded()) {
            active = false;
            info.cancel();
        }
    }
}
