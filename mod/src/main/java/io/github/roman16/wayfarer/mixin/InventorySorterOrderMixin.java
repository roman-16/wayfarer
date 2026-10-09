package io.github.roman16.wayfarer.mixin;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import cpw.mods.inventorysorter.ItemStackHolder;
import io.github.roman16.wayfarer.Sorting;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "cpw.mods.inventorysorter.SortingHandler", remap = false)
abstract class InventorySorterOrderMixin {
    @Redirect(method = "compactInventory", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/Multisets;copyHighestCountFirst(Lcom/google/common/collect/Multiset;)Lcom/google/common/collect/ImmutableMultiset;"))
    private ImmutableMultiset<ItemStackHolder> wayfarer$order(Multiset<ItemStackHolder> counts) {
        ImmutableMultiset.Builder<ItemStackHolder> ordered = ImmutableMultiset.builder();
        counts.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getElement().is(), Sorting.ORDER))
                .forEach(entry -> ordered.addCopies(entry.getElement(), entry.getCount()));
        return ordered.build();
    }
}
