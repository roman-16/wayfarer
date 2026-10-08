package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Recap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void wayfarer$recap(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay,
            @Nullable TransferState transfer, CallbackInfo info) {
        Recap.begin(Recap.SERVER + data.ip);
    }
}
