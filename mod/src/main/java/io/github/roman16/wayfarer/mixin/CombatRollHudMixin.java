package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.RollHud;
import net.combat_roll.client.gui.HudRenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HudRenderHelper.class, remap = false)
abstract class CombatRollHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$stamp(GuiGraphics graphics, float tickDelta, CallbackInfo info) {
        RollHud.render(graphics);
        info.cancel();
    }
}
