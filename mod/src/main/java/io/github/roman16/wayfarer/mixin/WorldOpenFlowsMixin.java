package io.github.roman16.wayfarer.mixin;

import com.mojang.serialization.Lifecycle;
import io.github.roman16.wayfarer.client.Recap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
abstract class WorldOpenFlowsMixin {
    @Inject(method = "openWorld", at = @At("HEAD"))
    private void wayfarer$recap(String world, Runnable onFail, CallbackInfo info) {
        Recap.begin(Recap.WORLD + world);
    }

    @Inject(method = {"createFreshLevel", "createLevelFromExistingSettings"}, at = @At("HEAD"))
    private void wayfarer$first(CallbackInfo info) {
        Recap.begin(null);
    }

    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$withoutWarning(Minecraft minecraft, CreateWorldScreen screen, Lifecycle lifecycle, Runnable loadWorld, boolean skipWarnings,
            CallbackInfo info) {
        loadWorld.run();
        info.cancel();
    }
}
