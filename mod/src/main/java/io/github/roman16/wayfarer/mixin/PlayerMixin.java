package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float wayfarer$burn(float exhaustion) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer server && Journey.slain(server.server)) {
            return exhaustion;
        }
        Balance.Hunger hunger = Balance.INSTANCE.hunger();
        return exhaustion * (player.hasEffect(Wayfarer.WARM) ? hunger.warmExhaustionFactor() : hunger.exhaustionFactor());
    }
}
