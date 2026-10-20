package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.payangar.moredarkness.darkness.DarknessCalculator", remap = false)
abstract class MoreDarknessMoonMixin {
    private static final int FULL = 0;
    private static final int NEW = 4;

    @Redirect(method = "skyFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMoonBrightness()F"), remap = false)
    private static float wayfarer$theShadersMoon(Level level) {
        Balance.Sky sky = Balance.get().sky();
        int phase = level.getMoonPhase();
        return (float) Math.sqrt(phase == FULL ? sky.moonFull() : phase == NEW ? sky.moonNew() : sky.moonPartial());
    }
}
