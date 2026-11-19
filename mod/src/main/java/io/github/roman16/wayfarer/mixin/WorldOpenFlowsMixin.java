package io.github.roman16.wayfarer.mixin;

import com.mojang.serialization.Lifecycle;
import io.github.roman16.wayfarer.Release;
import io.github.roman16.wayfarer.WorldSetup;
import io.github.roman16.wayfarer.client.Recap;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
abstract class WorldOpenFlowsMixin {
    @Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
    private void wayfarer$open(String world, Runnable onFail, CallbackInfo info) {
        Path folder = Minecraft.getInstance().getLevelSource().getBaseDir().resolve(world);
        Optional<Component> refused = Release.refusal(folder);
        if (refused.isPresent()) {
            Minecraft.getInstance().setScreen(new AlertScreen(onFail, Component.literal(world), refused.get(), CommonComponents.GUI_BACK, true));
            info.cancel();
            return;
        }
        Recap.begin(WorldSetup.id(folder));
    }

    @Inject(method = {"createFreshLevel", "createLevelFromExistingSettings"}, at = @At("HEAD"))
    private void wayfarer$first(CallbackInfo info) {
        Recap.begin("");
    }

    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$withoutWarning(Minecraft minecraft, CreateWorldScreen screen, Lifecycle lifecycle, Runnable loadWorld, boolean skipWarnings,
            CallbackInfo info) {
        loadWorld.run();
        info.cancel();
    }
}
