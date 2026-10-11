package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.DeepSight;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "getWaterVision()F", at = @At("HEAD"), cancellable = true)
    private void wayfarer$deepSight(CallbackInfoReturnable<Float> info) {
        if (DeepSight.sees((LocalPlayer) (Object) this)) {
            info.setReturnValue(1f);
        }
    }
}
