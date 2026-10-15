package io.github.roman16.wayfarer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mobius.seedrenderer.dh.ThroughputLimits", remap = false)
public abstract class SeedGenBudgetMixin {
    private static final String RENDER_BUDGET = "seedrenderer.renderBudgetScale";
    private static final int DISTANT_HORIZONS_OWN = 1;

    @Inject(method = "readScale", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$keepDistantHorizonsBudget(String property, int fallback, CallbackInfoReturnable<Integer> info) {
        if (property.equals(RENDER_BUDGET)) {
            info.setReturnValue(DISTANT_HORIZONS_OWN);
        }
    }
}
