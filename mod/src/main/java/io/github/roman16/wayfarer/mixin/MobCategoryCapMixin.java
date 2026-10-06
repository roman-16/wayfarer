package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobCategory.class)
public abstract class MobCategoryCapMixin {
    @Inject(method = "getMaxInstancesPerChunk", at = @At("HEAD"), cancellable = true)
    private void wayfarer$creatureCap(CallbackInfoReturnable<Integer> result) {
        if ((Object) this == MobCategory.CREATURE) {
            result.setReturnValue(Balance.INSTANCE.spawns().creatureCap());
        }
    }
}
