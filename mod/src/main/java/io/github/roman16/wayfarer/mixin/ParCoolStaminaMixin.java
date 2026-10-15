package io.github.roman16.wayfarer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.alrex.parcool.common.info.ActionInfo", remap = false)
abstract class ParCoolStaminaMixin {
    @Unique
    private static Object wayfarer$infinite;

    @Inject(method = "getStaminaType", at = @At("HEAD"), cancellable = true)
    private void wayfarer$infinite(CallbackInfoReturnable<Object> info) {
        info.setReturnValue(wayfarer$infinite());
    }

    @Unique
    private static Object wayfarer$infinite() {
        if (wayfarer$infinite == null) {
            try {
                Class<?> type = Class.forName("com.alrex.parcool.common.stamina.StaminaType");
                wayfarer$infinite = type.getField("NONE").get(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("ParCool's StaminaType has no NONE", exception);
            }
        }
        return wayfarer$infinite;
    }
}
