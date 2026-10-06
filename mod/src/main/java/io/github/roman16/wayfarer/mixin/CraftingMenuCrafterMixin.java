package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.FireRepair;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuCrafterMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"))
    private static void wayfarer$crafter(AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid, ResultContainer result,
            RecipeHolder<CraftingRecipe> recipe, CallbackInfo info) {
        FireRepair.crafter = player;
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void wayfarer$crafted(AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid, ResultContainer result,
            RecipeHolder<CraftingRecipe> recipe, CallbackInfo info) {
        FireRepair.crafter = null;
    }
}
