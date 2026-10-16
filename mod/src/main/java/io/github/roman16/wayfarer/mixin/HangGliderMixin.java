package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Gliding;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fuzs.hangglider.helper.PlayerGlidingHelper", remap = false)
public abstract class HangGliderMixin {
    @Inject(method = "isAllowedToGlide", at = @At("RETURN"), cancellable = true)
    private static void wayfarer$onlyOverADrop(Player player, CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(Gliding.allowed(player, info.getReturnValueZ()));
    }
}
