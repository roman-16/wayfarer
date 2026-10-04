package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Ores;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({OreFeature.class, ScatteredOreFeature.class})
abstract class OreFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void wayfarer$thin(FeaturePlaceContext<OreConfiguration> context, CallbackInfoReturnable<Boolean> result) {
        if (!Ores.keeps(context)) {
            result.setReturnValue(false);
        }
    }
}
