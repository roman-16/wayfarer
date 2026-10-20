package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionSpecialEffects.class)
abstract class CloudHeightMixin {
    @Inject(method = "getCloudHeight", at = @At("HEAD"), cancellable = true)
    private void wayfarer$aboveTheBuildLimit(CallbackInfoReturnable<Float> info) {
        if ((Object) this instanceof DimensionSpecialEffects.OverworldEffects) {
            info.setReturnValue((float) Balance.get().sky().cloudHeight());
        }
    }
}
