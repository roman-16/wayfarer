package io.github.roman16.wayfarer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mobius.seedrenderer.net.SeedSharing", remap = false)
public abstract class SeedGenSharingMixin {
    @Inject(method = "say", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$quiet(String line, CallbackInfo info) {
        info.cancel();
    }
}
