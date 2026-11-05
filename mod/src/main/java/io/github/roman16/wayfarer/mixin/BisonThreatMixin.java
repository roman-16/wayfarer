package io.github.roman16.wayfarer.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.satisfy.wildernature.core.entity.animal.defensive.BisonEntity", remap = false)
abstract class BisonThreatMixin {
    @Inject(method = "isValidThreat", at = @At("HEAD"), cancellable = true)
    private void wayfarer$onlyWhenStruck(Player player, CallbackInfoReturnable<Boolean> info) {
        if (player == null || ((LivingEntity) (Object) this).getLastHurtByMob() != player) {
            info.setReturnValue(false);
        }
    }
}
