package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import toughasnails.temperature.TemperatureHelperImpl;

@Mixin(value = TemperatureHelperImpl.class, remap = false)
abstract class ToughAsNailsShelterMixin {
    @ModifyExpressionValue(method = {"getBiomeTemperatureLevel", "nightModifier"},
            at = @At(value = "FIELD", target = "Ltoughasnails/config/TemperatureConfig;environmentalModifierAltitude:I"))
    private static int wayfarer$sheltered(int altitude) {
        return Integer.MAX_VALUE;
    }
}
