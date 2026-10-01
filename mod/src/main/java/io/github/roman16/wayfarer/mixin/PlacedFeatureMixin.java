package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Spread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
abstract class PlacedFeatureMixin {
    @Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
    private void wayfarer$spread(PlacementContext context, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        PlacedFeature self = (PlacedFeature) (Object) this;
        if (!Spread.keeps(context.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(self), random)) {
            result.setReturnValue(false);
        }
    }
}
