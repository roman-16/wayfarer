package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

public final class Robes {
    static final String MARK = Wayfarer.MODID + "_mark";
    private static final String ROBE = "robe";
    private static final TagKey<EntityType<?>> GAZEBOUND = TagKey.create(Registries.ENTITY_TYPE, Wayfarer.id("gazebound"));

    private Robes() {}

    static boolean worn(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        CustomData data = chest.get(DataComponents.CUSTOM_DATA);
        return data != null && ROBE.equals(data.copyTag().getString(MARK));
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && event.getEntity().getType().is(GAZEBOUND) && worn(player)
                && event.getEntity().getLastHurtByMob() != player) {
            event.setCanceled(true);
        }
    }
}
