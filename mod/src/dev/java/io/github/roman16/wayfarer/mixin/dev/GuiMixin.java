package io.github.roman16.wayfarer.mixin.dev;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.roman16.wayfarer.Still;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
    @Shadow
    private int tickCount;

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void wayfarer$still(boolean pause, CallbackInfo info) {
        if (Still.held()) {
            tickCount = 0;
        }
    }

    @ModifyConstant(method = "updateVignetteBrightness", constant = @Constant(floatValue = 0.01F))
    private float wayfarer$settled(float easing) {
        return Still.held() ? 1 : easing;
    }

    @ModifyExpressionValue(method = "renderEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffectInstance;endsWithin(I)Z"))
    private boolean wayfarer$steady(boolean ending) {
        return ending && !Still.held();
    }
}
