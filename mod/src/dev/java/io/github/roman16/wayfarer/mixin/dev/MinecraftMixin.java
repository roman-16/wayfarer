package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Driver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "isPaused", at = @At("HEAD"), cancellable = true)
    private void wayfarer$betweenTurns(CallbackInfoReturnable<Boolean> info) {
        if (Driver.betweenTurns()) {
            info.setReturnValue(true);
        }
    }
}
