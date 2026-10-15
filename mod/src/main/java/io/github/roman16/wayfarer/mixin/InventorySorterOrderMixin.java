package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Sorting;
import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cpw.mods.inventorysorter.InventoryHandler$ItemStackComparator", remap = false)
abstract class InventorySorterOrderMixin {
    @Unique
    private static Method wayfarer$stack;

    @Inject(method = "compare(Lcpw/mods/inventorysorter/ItemStackHolder;Lcpw/mods/inventorysorter/ItemStackHolder;)I", at = @At("HEAD"), cancellable = true)
    private void wayfarer$order(@Coerce Object first, @Coerce Object second, CallbackInfoReturnable<Integer> info) {
        ItemStack one = wayfarer$stack(first);
        ItemStack other = wayfarer$stack(second);
        int order = Sorting.ORDER.compare(one, other);
        if (order != 0) {
            info.setReturnValue(order);
        } else if (ItemStack.isSameItemSameComponents(one, other)) {
            info.setReturnValue(0);
        }
    }

    @Unique
    private static ItemStack wayfarer$stack(Object holder) {
        try {
            if (wayfarer$stack == null) {
                wayfarer$stack = holder.getClass().getMethod("is");
            }
            return (ItemStack) wayfarer$stack.invoke(holder);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Inventory Sorter's ItemStackHolder has no stack", exception);
        }
    }
}
