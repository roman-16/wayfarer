package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import toughasnails.thirst.ThirstHooks;

@Mixin(value = ThirstHooks.class, remap = false)
abstract class ToughAsNailsStarveMixin {
    @WrapOperation(method = "doFoodDataTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", remap = true))
    private static boolean wayfarer$starve(Player player, DamageSource thirst, float amount, Operation<Boolean> original) {
        return original.call(player, player.damageSources().starve(), amount);
    }
}
