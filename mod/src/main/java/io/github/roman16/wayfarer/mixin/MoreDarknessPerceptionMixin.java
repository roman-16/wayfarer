package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Graphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.payangar.moredarkness.darkness.PerceptionEffects", remap = false)
abstract class MoreDarknessPerceptionMixin {
    @Inject(method = {"processFrame", "processDarkSight"}, at = @At("HEAD"), cancellable = true)
    private static void wayfarer$leavePassesToShader(CallbackInfo info) {
        if (Graphics.shaded()) {
            info.cancel();
        }
    }
}
