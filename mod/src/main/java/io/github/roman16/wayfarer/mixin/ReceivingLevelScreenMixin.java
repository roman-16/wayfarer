package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Recap;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wayfarer$holdRecap(CallbackInfo info) {
        if (Recap.holding()) {
            info.cancel();
        }
    }
}
