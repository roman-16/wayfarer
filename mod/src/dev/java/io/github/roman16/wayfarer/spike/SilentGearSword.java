package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.material.Material;
import net.silentchaos512.gear.api.part.PartList;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.PartTypes;
import net.silentchaos512.gear.util.GearData;

final class SilentGearSword {
    private static final ResourceLocation HANDLE = ResourceLocation.fromNamespaceAndPath("silentgear", "wood/oak");
    private static final ResourceLocation SWORD = ResourceLocation.fromNamespaceAndPath("silentgear", "sword");

    private SilentGearSword() {}

    static ItemStack frostIron() {
        GearItem sword = (GearItem) BuiltInRegistries.ITEM.get(SWORD);
        Material head = SgRegistries.MATERIAL.get(Wayfarer.id("frost_iron"));
        Material handle = SgRegistries.MATERIAL.get(HANDLE);
        PartList parts = PartList.of();
        for (PartType type : sword.getRequiredParts()) {
            Material material = type == PartTypes.MAIN.get() ? head : handle;
            type.getCompoundPartItem(sword.getGearType()).ifPresent(item -> parts.add(PartInstance.from(item.create(MaterialInstance.of(material)))));
        }
        ItemStack result = new ItemStack(sword.asItem());
        GearData.writeConstructionParts(result, parts);
        GearData.recalculateGearData(result, null);
        parts.forEach(part -> part.onAddToGear(result));
        return result;
    }
}
