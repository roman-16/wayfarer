package io.github.roman16.wayfarer;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public final class Clothes {
    public static final List<String> SLOTS = List.of("hood", "coat", "trousers", "boots");

    private Clothes() {}

    public static List<ItemStack> worn(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(curios -> SLOTS.stream()
                .flatMap(slot -> curios.getStacksHandler(slot).stream())
                .flatMap(handler -> Stream.iterate(0, index -> index < handler.getSlots(), index -> index + 1).map(handler.getStacks()::getStackInSlot))
                .filter(stack -> !stack.isEmpty()).toList()).orElse(List.of());
    }
}
