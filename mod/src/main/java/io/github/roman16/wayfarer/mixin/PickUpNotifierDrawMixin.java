package io.github.roman16.wayfarer.mixin;

import fuzs.pickupnotifier.client.gui.PositionPreset;
import io.github.roman16.wayfarer.client.Hud;
import io.github.roman16.wayfarer.client.HudSize;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "fuzs.pickupnotifier.client.handler.DrawEntriesHandler", remap = false)
abstract class PickUpNotifierDrawMixin {
    @ModifyVariable(method = "onRenderGui", at = @At("STORE"), ordinal = 0)
    private float wayfarer$hudSize(float scale) {
        return HudSize.scale();
    }

    @Redirect(method = "onRenderGui", at = @At(value = "INVOKE", target = "Lfuzs/pickupnotifier/client/gui/PositionPreset;getY(III)I"))
    private int wayfarer$aboveSubtitles(PositionPreset position, int height, int screenHeight, int offset) {
        int lift = Minecraft.getInstance().options.showSubtitles().get() ? Math.round(Hud.SUBTITLE_ROOM / HudSize.scale()) : 0;
        return position.getY(height, screenHeight, offset + lift);
    }
}
