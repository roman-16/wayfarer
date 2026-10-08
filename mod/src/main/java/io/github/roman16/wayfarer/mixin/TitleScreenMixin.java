package io.github.roman16.wayfarer.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    @Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
    private void wayfarer$withoutRealms(CallbackInfoReturnable<Boolean> enabled) {
        enabled.setReturnValue(false);
    }
}
