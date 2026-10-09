package io.github.roman16.wayfarer.mixin;

import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
abstract class CreateWorldMoreTabMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement wayfarer$withoutExperiments(GridLayout.RowHelper rows, LayoutElement element) {
        return element;
    }
}
