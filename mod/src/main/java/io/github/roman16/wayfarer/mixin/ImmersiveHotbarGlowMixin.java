package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.stamp.Stamp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "derp.immersivehotbar.animation.hotbar.HotbarAnimationRenderer", remap = false)
abstract class ImmersiveHotbarGlowMixin {
    @ModifyConstant(method = "drawDurabilityGlow", constant = @Constant(intValue = 0xFF0000))
    private static int wayfarer$danger(int red) {
        return Stamp.rgb(Stamp.DANGER);
    }
}
