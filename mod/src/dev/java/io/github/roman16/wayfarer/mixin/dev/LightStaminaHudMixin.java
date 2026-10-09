package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Still;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.alrex.parcool.client.hud.impl.LightStaminaHUD", remap = false)
abstract class LightStaminaHudMixin {
    @Shadow
    private int randomOffset;

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void wayfarer$still(ClientTickEvent.Post event, LocalPlayer player, CallbackInfo info) {
        if (Still.held()) {
            randomOffset = 0;
            info.cancel();
        }
    }
}
