package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.LastUse;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackLastUseMixin {
    @Unique
    private boolean wayfarer$wearsOut;

    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wayfarer$stopAtLastUse(int damage) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.isDamageableItem()) {
            return damage;
        }
        int room = LastUse.room(stack, damage);
        wayfarer$wearsOut = room > 0 && !LastUse.lastUse(stack) && stack.getDamageValue() + room >= stack.getMaxDamage() - 1;
        return room;
    }

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("RETURN"))
    private void wayfarer$tellLastUse(int damage, ServerLevel level, LivingEntity holder, Consumer<Item> onBreak, CallbackInfo info) {
        if (wayfarer$wearsOut && holder instanceof ServerPlayer player) {
            LastUse.worn(player, (ItemStack) (Object) this);
        }
        wayfarer$wearsOut = false;
    }
}
