package io.github.roman16.wayfarer;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Beasts {
    private static final TagKey<EntityType<?>> BEASTS = TagKey.create(Registries.ENTITY_TYPE, Wayfarer.id("beasts"));
    private static final int YOUNG_REACH = 16;

    private Beasts() {}

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity beast = event.getEntity();
        if (event.getNewAboutToBeSetTarget() instanceof Player player && beast.getType().is(BEASTS) && beast.getLastHurtByMob() != player
                && beast.level().getEntitiesOfClass(AgeableMob.class, beast.getBoundingBox().inflate(YOUNG_REACH),
                        young -> young.getType() == beast.getType() && young.isBaby()).isEmpty()) {
            event.setCanceled(true);
        }
    }
}
