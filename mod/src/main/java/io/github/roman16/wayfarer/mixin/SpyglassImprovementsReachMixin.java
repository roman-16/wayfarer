package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.juancarloscp52.spyglass_improvements.client.SpyglassImprovementsClient", remap = false)
abstract class SpyglassImprovementsReachMixin {
    @ModifyReturnValue(method = "findSlotByItem", at = @At("RETURN"))
    private int wayfarer$onlyTheHotbar(int slot) {
        return slot < Inventory.getSelectionSize() ? slot : -1;
    }
}
