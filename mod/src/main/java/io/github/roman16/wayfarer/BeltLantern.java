package io.github.roman16.wayfarer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public final class BeltLantern {
    public static final String SLOT = "lantern";
    public static final TagKey<Item> FITS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", SLOT));

    private BeltLantern() {}

    public static ItemStack worn(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).flatMap(curios -> curios.getStacksHandler(SLOT))
                .map(handler -> handler.getStacks().getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }
}
