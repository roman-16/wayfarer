package io.github.roman16.wayfarer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class DeepSight {
    private static final String GIFT = "deep_sight";

    private DeepSight() {}

    public static boolean sees(Entity entity) {
        return Gifts.has(GIFT) && entity == Minecraft.getInstance().player && entity.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
    }
}
