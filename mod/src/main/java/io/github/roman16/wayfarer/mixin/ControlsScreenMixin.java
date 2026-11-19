package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.AutoSort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ControlsScreen.class)
public abstract class ControlsScreenMixin {
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void wayfarer$autoSort(Options options, CallbackInfoReturnable<OptionInstance<?>[]> info) {
        List<OptionInstance<?>> all = new ArrayList<>(Arrays.asList(info.getReturnValue()));
        all.add(all.indexOf(options.autoJump()) + 1, AutoSort.option());
        info.setReturnValue(all.toArray(OptionInstance<?>[]::new));
    }
}
