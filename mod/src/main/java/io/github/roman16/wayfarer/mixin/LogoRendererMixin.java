package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.FrontDoor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
abstract class LogoRendererMixin {
    @Shadow
    @Final
    private boolean keepLogoThroughFade;

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    private void wayfarer$logo(GuiGraphics graphics, int screenWidth, float transparency, int height, CallbackInfo info) {
        FrontDoor.logo(graphics, screenWidth, keepLogoThroughFade ? 1 : transparency, height);
        info.cancel();
    }
}
