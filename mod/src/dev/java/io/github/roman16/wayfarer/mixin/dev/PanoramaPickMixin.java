package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.client.Panorama;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Panorama.class)
abstract class PanoramaPickMixin {
    @Inject(method = "pick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wayfarer$first(List<String> sets, CallbackInfoReturnable<String> info) {
        info.setReturnValue(sets.getFirst());
    }
}
