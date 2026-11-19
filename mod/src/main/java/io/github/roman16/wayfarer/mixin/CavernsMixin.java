package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Caverns;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DensityFunctions.HolderHolder.class)
abstract class CavernsMixin {
    @Inject(method = "mapAll", at = @At("HEAD"), cancellable = true)
    private void wayfarer$keepCavernsUnder(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> result) {
        Holder<DensityFunction> function = ((DensityFunctions.HolderHolder) (Object) this).function();
        if (function instanceof Holder.Reference<DensityFunction> reference && reference.unwrapKey().filter(key -> key.location().equals(Caverns.cheese())).isPresent()) {
            DensityFunction depth = new DensityFunctions.HolderHolder(Caverns.depth(((HolderReferenceAccessor) reference).wayfarer$owner())).mapAll(visitor);
            result.setReturnValue(visitor.apply(new DensityFunctions.HolderHolder(Holder.direct(Caverns.under(function.value().mapAll(visitor), depth)))));
        }
    }
}
