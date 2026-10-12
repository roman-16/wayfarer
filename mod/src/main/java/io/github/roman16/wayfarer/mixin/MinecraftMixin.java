package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.roman16.wayfarer.client.Sight;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    @Nullable
    public ClientLevel level;

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void wayfarer$glimpse(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (Sight.shows(entity)) {
            result.setReturnValue(true);
        }
    }

    @Shadow
    private boolean clientLevelTeardownInProgress;

    @Shadow
    public abstract MusicManager getMusicManager();

    @WrapOperation(method = "updateScreenAndTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private void wayfarer$keepMusic(SoundManager sounds, Operation<Void> original) {
        if (level == null) {
            return;
        }
        if (clientLevelTeardownInProgress) {
            original.call(sounds);
            return;
        }
        for (SoundSource source : SoundSource.values()) {
            if (source != SoundSource.MUSIC) {
                sounds.stop(null, source);
            }
        }
        getMusicManager().stopPlaying();
    }
}
