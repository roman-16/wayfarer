package io.github.roman16.wayfarer.mixin;

import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
abstract class MonsterRestMixin {
    @Inject(method = "isPreventingPlayerRest", at = @At("HEAD"), cancellable = true)
    private void wayfarer$onlyMonstersInSight(Player player, CallbackInfoReturnable<Boolean> info) {
        if ((Object) this instanceof EasyNPC<?> || !((Monster) (Object) this).hasLineOfSight(player)) {
            info.setReturnValue(false);
        }
    }
}
