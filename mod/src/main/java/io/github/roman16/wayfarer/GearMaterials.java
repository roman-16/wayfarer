package io.github.roman16.wayfarer;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.item.gear.GearArmorItem;
import net.silentchaos512.gear.util.GearData;

public final class GearMaterials {
    public static final String ARMOUR = "armour";

    private GearMaterials() {}

    public static Optional<ResourceLocation> main(ItemStack stack, String kind) {
        if (!(stack.getItem() instanceof GearItem) || stack.getItem() instanceof GearArmorItem != kind.equals(ARMOUR)) {
            return Optional.empty();
        }
        PartInstance part = GearData.getCoatingOrMainPart(stack);
        MaterialInstance material = part == null ? null : part.getPrimaryMaterial();
        return material == null ? Optional.empty() : Optional.of(material.getId());
    }
}
