package io.github.roman16.wayfarer.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.alrex.parcool.client.animation.PassiveCustomAnimation", remap = false)
public abstract class ParCoolFallingMixin {
    @Inject(method = "animate", at = @At("HEAD"), cancellable = true)
    private void wayfarer$stillUnderGlider(Player player, @Coerce Object parkourability, @Coerce Object transformer, CallbackInfo info) {
        if (player.getPose() == Pose.SPIN_ATTACK && !player.isAutoSpinAttack()) {
            info.cancel();
        }
    }
}
