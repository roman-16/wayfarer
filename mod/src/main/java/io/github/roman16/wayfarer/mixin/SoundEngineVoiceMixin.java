package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.roman16.wayfarer.Balance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEngine.class)
abstract class SoundEngineVoiceMixin {
    @ModifyReturnValue(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"))
    private float wayfarer$voicesCarry(float volume, @Local(argsOnly = true) SoundSource source) {
        return source == SoundSource.VOICE ? volume * Balance.get().talk().voiceGain() : volume;
    }
}
