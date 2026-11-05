package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Driver;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
abstract class ScreenMixin {
    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$controlled(CallbackInfoReturnable<Boolean> info) {
        if (Driver.controlling()) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$shifted(CallbackInfoReturnable<Boolean> info) {
        if (Driver.shifting()) {
            info.setReturnValue(true);
        }
    }
}
