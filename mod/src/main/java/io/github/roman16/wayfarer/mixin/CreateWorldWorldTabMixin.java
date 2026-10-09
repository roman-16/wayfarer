package io.github.roman16.wayfarer.mixin;

import java.util.function.Consumer;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class CreateWorldWorldTabMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement wayfarer$withoutWorldType(GridLayout.RowHelper rows, LayoutElement element) {
        return element;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$Builder;build(Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid;"))
    private Consumer<LayoutElement> wayfarer$withoutSwitches(Consumer<LayoutElement> add) {
        return element -> {};
    }
}
