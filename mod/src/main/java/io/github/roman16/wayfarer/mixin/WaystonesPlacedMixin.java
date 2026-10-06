package io.github.roman16.wayfarer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.blay09.mods.waystones.block.WaystoneBlockBase", remap = false)
public abstract class WaystonesPlacedMixin {
    @Inject(method = "shouldOpenMenuWhenPlaced", at = @At("HEAD"), cancellable = true)
    private void wayfarer$named(CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(false);
    }
}
