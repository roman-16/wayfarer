package io.github.roman16.wayfarer;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.material.Material;
import net.silentchaos512.gear.api.part.PartList;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.PartTypes;
import net.silentchaos512.gear.util.GearData;

public final class FrostIron {
    public static final ResourceLocation MATERIAL = Wayfarer.id("frost_iron");
    private static final ResourceLocation HANDLE = ResourceLocation.fromNamespaceAndPath("silentgear", "wood/oak");
    private static final String SILENT_GEAR = "silentgear";

    private FrostIron() {}

    public static Optional<String> shape(ItemStack stack) {
        if (stack.getItem() instanceof GearItem gear) {
            ResourceLocation type = SgRegistries.GEAR_TYPE.getKey(gear.getGearType());
            return Optional.ofNullable(type).map(ResourceLocation::getPath);
        }
        if (stack.getItem() instanceof ArmorItem armour) {
            return Optional.of(armour.getType().getName());
        }
        if (stack.is(ItemTags.SWORDS)) {
            return Optional.of("sword");
        }
        if (stack.is(ItemTags.PICKAXES)) {
            return Optional.of("pickaxe");
        }
        if (stack.is(ItemTags.AXES)) {
            return Optional.of("axe");
        }
        if (stack.is(ItemTags.SHOVELS)) {
            return Optional.of("shovel");
        }
        if (stack.is(ItemTags.HOES)) {
            return Optional.of("hoe");
        }
        return Optional.empty();
    }

    public static boolean made(ItemStack stack) {
        if (!(stack.getItem() instanceof GearItem)) {
            return false;
        }
        PartInstance main = GearData.getCoatingOrMainPart(stack);
        MaterialInstance material = main == null ? null : main.getPrimaryMaterial();
        return material != null && MATERIAL.equals(material.getId());
    }

    public static ItemStack forge(ItemStack old, String shape) {
        GearItem gear = (GearItem) BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SILENT_GEAR, shape));
        Material frost = SgRegistries.MATERIAL.get(MATERIAL);
        PartList kept = old.getItem() instanceof GearItem ? GearData.getConstruction(old).parts() : PartList.empty();
        PartList parts = PartList.of();
        for (PartType type : gear.getRequiredParts()) {
            if (type == PartTypes.MAIN.get()) {
                type.getCompoundPartItem(gear.getGearType()).ifPresent(item -> parts.add(PartInstance.from(item.create(MaterialInstance.of(frost)))));
            } else {
                Optional<PartInstance> same = kept.stream().filter(part -> part.getType() == type).findFirst();
                if (same.isPresent()) {
                    parts.add(same.get());
                } else {
                    type.getCompoundPartItem(gear.getGearType())
                            .ifPresent(item -> parts.add(PartInstance.from(item.create(MaterialInstance.of(SgRegistries.MATERIAL.get(HANDLE))))));
                }
            }
        }
        kept.stream().filter(part -> part.getType() != PartTypes.MAIN.get() && part.getType() != PartTypes.COATING.get()
                && !gear.getRequiredParts().contains(part.getType())).forEach(parts::add);
        ItemStack made = new ItemStack(gear.asItem());
        GearData.writeConstructionParts(made, parts);
        GearData.recalculateGearData(made, null);
        parts.forEach(part -> part.onAddToGear(made));
        EnchantmentHelper.setEnchantments(made, EnchantmentHelper.getEnchantmentsForCrafting(old));
        if (old.has(DataComponents.CUSTOM_NAME)) {
            made.set(DataComponents.CUSTOM_NAME, old.get(DataComponents.CUSTOM_NAME));
        }
        return made;
    }
}
