package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.DarkSight;
import io.github.roman16.wayfarer.client.DeepSight;
import io.github.roman16.wayfarer.client.StoneAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void wayfarer$aimAtEye(float partialTick, CallbackInfo info) {
        StoneAim.aim(minecraft, partialTick);
    }

    @Inject(method = "getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$darkSight(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> info) {
        if (DeepSight.sees(entity)) {
            info.setReturnValue(1f);
        } else if (DarkSight.sees(entity)) {
            info.setReturnValue(DarkSight.scale());
        }
    }
}
