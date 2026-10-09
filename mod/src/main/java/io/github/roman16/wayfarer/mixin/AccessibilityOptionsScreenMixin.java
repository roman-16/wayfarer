package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.HudSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AccessibilityOptionsScreen.class)
public abstract class AccessibilityOptionsScreenMixin {
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void wayfarer$hudSize(Options options, CallbackInfoReturnable<OptionInstance<?>[]> info) {
        List<OptionInstance<?>> all = new ArrayList<>(Arrays.asList(info.getReturnValue()));
        all.add(all.indexOf(options.showSubtitles()) + 1, HudSize.option());
        info.setReturnValue(all.toArray(OptionInstance<?>[]::new));
    }
}
