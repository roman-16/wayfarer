package io.github.roman16.wayfarer.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.animations.fullbody.FallingAnimation", remap = false)
public abstract class NotEnoughAnimationsFallingMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void wayfarer$stillUnderGlider(AbstractClientPlayer player, @Coerce Object data, CallbackInfoReturnable<Boolean> info) {
        if (player.getPose() == Pose.SPIN_ATTACK && !player.isAutoSpinAttack()) {
            info.setReturnValue(false);
        }
    }
}
