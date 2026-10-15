package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Dressed;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void wayfarer$dressed(CallbackInfoReturnable<PlayerSkin> info) {
        PlayerSkin worn = Dressed.worn((AbstractClientPlayer) (Object) this);
        if (worn != null) {
            info.setReturnValue(worn);
        }
    }
}
