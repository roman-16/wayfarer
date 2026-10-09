package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Cuts;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raid.class)
public abstract class RaidMixin {
    @Inject(method = "getDefaultNumSpawns", at = @At("HEAD"), cancellable = true)
    private void wayfarer$noCutRaiders(Raid.RaiderType type, int wave, boolean bonus, CallbackInfoReturnable<Integer> info) {
        if (Cuts.creature(((RaiderTypeAccessor) (Object) type).wayfarer$type().get())) {
            info.setReturnValue(0);
        }
    }

    @Inject(method = "getPotentialBonusSpawns", at = @At("HEAD"), cancellable = true)
    private void wayfarer$noCutBonus(Raid.RaiderType type, RandomSource random, int wave, DifficultyInstance difficulty, boolean bonus,
            CallbackInfoReturnable<Integer> info) {
        if (Cuts.creature(((RaiderTypeAccessor) (Object) type).wayfarer$type().get())) {
            info.setReturnValue(0);
        }
    }
}
