package io.github.roman16.wayfarer;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class Warm extends MobEffect {
    private static final int COLOR = 0xE8601C;

    public Warm() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
