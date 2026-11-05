package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Driver;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = {"useItemOn", "useItem", "interact", "interactAt"}, at = @At("HEAD"))
    private void wayfarer$clicked(CallbackInfoReturnable<?> info) {
        Driver.clicked();
    }
}
