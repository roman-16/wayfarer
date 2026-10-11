package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class DarkSight {
    private static final String GIFT = "dark_sight";
    private static final float SIXTEENTHS = 16;

    private DarkSight() {}

    public static boolean sees(LivingEntity entity) {
        return Gifts.has(GIFT) && entity == Minecraft.getInstance().player && entity.level().dimension() == Level.OVERWORLD && !entity.hasEffect(MobEffects.NIGHT_VISION);
    }

    public static float scale() {
        return Balance.get().frost().darkSightSixteenths() / SIXTEENTHS;
    }
}
