package io.github.roman16.wayfarer.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class ChannelGainMixin {
    @Shadow
    @Final
    private int source;

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void wayfarer$gainAboveOne(float volume, CallbackInfo info) {
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, Math.max(1, volume));
    }
}
