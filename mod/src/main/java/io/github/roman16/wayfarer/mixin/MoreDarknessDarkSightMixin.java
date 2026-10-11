package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.DarkSight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.payangar.moredarkness.darkness.DarknessCalculator", remap = false)
abstract class MoreDarknessDarkSightMixin {
    @Redirect(method = "updateLuminance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private static boolean wayfarer$darkSight(LocalPlayer player, Holder<MobEffect> effect) {
        return player.hasEffect(effect) || effect.equals(MobEffects.NIGHT_VISION) && DarkSight.sees(player);
    }
}
