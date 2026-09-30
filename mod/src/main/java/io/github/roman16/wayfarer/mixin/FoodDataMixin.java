package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float wayfarer$burn(float exhaustion) {
        return exhaustion * Balance.INSTANCE.hunger().exhaustionFactor();
    }
}
