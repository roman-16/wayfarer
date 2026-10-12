package io.github.roman16.wayfarer.mixin;

import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.roman16.wayfarer.Clothes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import toughasnails.temperature.TemperatureHelperImpl;

@Mixin(value = TemperatureHelperImpl.class, remap = false)
abstract class ToughAsNailsClothesMixin {
    @WrapOperation(method = "armorModifier", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getArmorSlots()Ljava/lang/Iterable;", remap = true))
    private static Iterable<ItemStack> wayfarer$clothes(Player player, Operation<Iterable<ItemStack>> original) {
        return Iterables.concat(original.call(player), Clothes.worn(player));
    }
}
