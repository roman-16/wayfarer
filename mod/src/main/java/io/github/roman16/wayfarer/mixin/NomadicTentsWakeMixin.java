package io.github.roman16.wayfarer.mixin;

import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "nomadictents.event.NTEvents$ForgeHandler", remap = false)
abstract class NomadicTentsWakeMixin {
    @Inject(method = "onPlayerWake", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wayfarer$teamWakes(PlayerWakeUpEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
