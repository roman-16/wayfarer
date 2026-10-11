package io.github.roman16.wayfarer.worm;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GreatWorms {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Wayfarer.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<GreatWorm>> GREAT_WORM = TYPES.register("great_worm",
            () -> EntityType.Builder.<GreatWorm>of(GreatWorm::new, MobCategory.MONSTER).sized(1.5F, 5.5F).clientTrackingRange(16).build("great_worm"));

    private GreatWorms() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put(GREAT_WORM.get(), GreatWorm.attributes().build()));
    }
}
