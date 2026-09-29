package io.github.roman16.wayfarer.mixin;

import com.teamremastered.endrem.item.JsonEye;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Subtle Effects 1.14.3 expects End Remastered 6.0's JsonEye.getID() returning a ResourceLocation; 6.3.0 returns the raw id string.
@Mixin(targets = "einstein.subtle_effects.compat.EndRemasteredCompat", remap = false)
public abstract class SubtleEffectsEndRemasteredCompatMixin {
    @Inject(method = "getAllEyes", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$allEyes(CallbackInfoReturnable<List<ResourceLocation>> callback) {
        callback.setReturnValue(JsonEye.getEyes().stream().map(eye -> ResourceLocation.parse(eye.getID())).toList());
    }
}
