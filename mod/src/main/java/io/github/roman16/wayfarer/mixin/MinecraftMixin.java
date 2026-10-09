package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.roman16.wayfarer.client.Sight;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
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

    @WrapWithCondition(method = "updateScreenAndTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;stop()V"))
    private boolean wayfarer$keepTitleMusic(SoundManager sounds) {
        return level != null;
    }
}
