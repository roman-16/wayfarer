package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Library;
import io.github.roman16.wayfarer.Balance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Library.class)
abstract class LibraryStreamsMixin {
    @ModifyExpressionValue(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 0))
    private int wayfarer$streamsForEveryVoice(int streams, @Local(ordinal = 0) int channels) {
        return Math.max(streams, Math.min(Balance.get().sound().streams(), channels / 2));
    }
}
