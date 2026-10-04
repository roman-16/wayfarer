package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class WormSpike {
    private static final String GECKOLIB = "geckolib";
    private static final double HEALTH = 32;
    private static final double SPEED = 0.2;
    private static EntityType<Worm> worm;

    private WormSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithGeckoLib {
        private WithGeckoLib() {}

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            if (!ModList.get().isLoaded(GECKOLIB)) {
                return;
            }
            IEventBus bus = ModList.get().getModContainerById(Wayfarer.MODID).orElseThrow().getEventBus();
            bus.register(WormSpike.class);
            if (FMLEnvironment.dist == Dist.CLIENT) {
                bus.register(Client.class);
            }
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            worm = EntityType.Builder.<Worm>of(Worm::new, MobCategory.CREATURE).sized(0.6f, 0.5f).build(Wayfarer.id("worm").toString());
            helper.register(Wayfarer.id("worm"), worm);
        });
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(worm, PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).build());
    }

    public static final class Worm extends PathfinderMob implements GeoEntity {
        private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("animation.worm.crawl");
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public Worm(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(1, new RandomStrollGoal(this, 1));
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "crawl", 0, state -> state.setAndContinue(CRAWL)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    public static final class Client {
        private Client() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(worm, context -> new GeoEntityRenderer<>(context, new DefaultedEntityGeoModel<>(Wayfarer.id("worm"))));
        }
    }
}
