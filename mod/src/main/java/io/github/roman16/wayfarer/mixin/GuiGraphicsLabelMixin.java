package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.stamp.Recolour;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphics.class)
abstract class GuiGraphicsLabelMixin {
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wayfarer$text(int color) {
        return Recolour.label(color);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("HEAD"), argsOnly = true,
            ordinal = 0)
    private int wayfarer$sequence(int color) {
        return Recolour.label(color);
    }
}
