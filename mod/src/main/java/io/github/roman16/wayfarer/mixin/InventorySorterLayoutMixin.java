package io.github.roman16.wayfarer.mixin;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSortedMultiset;
import com.google.common.collect.Multiset;
import cpw.mods.inventorysorter.InventoryHandler;
import cpw.mods.inventorysorter.ItemStackHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "cpw.mods.inventorysorter.SortingHandler", remap = false)
abstract class InventorySorterLayoutMixin {
    @Redirect(method = "compactInventory", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Multisets;copyHighestCountFirst(Lcom/google/common/collect/Multiset;)Lcom/google/common/collect/ImmutableMultiset;"))
    private ImmutableMultiset<ItemStackHolder> wayfarer$inOrder(Multiset<ItemStackHolder> counts) {
        return ImmutableSortedMultiset.copyOf(new InventoryHandler.ItemStackComparator(), counts);
    }
}
