package io.github.roman16.wayfarer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.Level;

public final class Deep extends Guardian {
    public Deep(EntityType<? extends Guardian> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
