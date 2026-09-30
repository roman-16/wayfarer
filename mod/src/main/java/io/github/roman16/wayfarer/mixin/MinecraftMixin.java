package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.client.Sight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void wayfarer$glimpse(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (Sight.shows(entity)) {
            result.setReturnValue(true);
        }
    }
}
