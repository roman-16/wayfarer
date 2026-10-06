package io.github.roman16.wayfarer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.init.ModCompatibility;

@Mixin(value = ModCompatibility.class, remap = false)
public abstract class ToughAsNailsSeasonMixin {
    @Inject(method = "seasonModifier", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$landOnly(Level level, BlockPos pos, TemperatureLevel current, CallbackInfoReturnable<TemperatureLevel> result) {
        result.setReturnValue(current);
    }
}
