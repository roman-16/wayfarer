package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.WorldShape;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DensityFunctions.HolderHolder.class)
abstract class ContinentsMixin {
    @Inject(method = "mapAll", at = @At("HEAD"), cancellable = true)
    private void wayfarer$shapeTheWorld(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> result) {
        Holder<DensityFunction> function = ((DensityFunctions.HolderHolder) (Object) this).function();
        if (function.unwrapKey().filter(key -> key.location().equals(WorldShape.continents())).isPresent()) {
            result.setReturnValue(visitor.apply(new DensityFunctions.HolderHolder(Holder.direct(WorldShape.around(function.value().mapAll(visitor))))));
        }
    }
}
