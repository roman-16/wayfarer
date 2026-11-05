package io.github.roman16.wayfarer.worm;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class GreatWorm extends PathfinderMob implements GeoEntity {
    private static final RawAnimation SWAY = RawAnimation.begin().thenLoop("animation.great_worm.sway");
    private static final double LOOK = 32;
    private static final double EYE_SHARE = 0.25;
    private static final double EYE_SLACK = 0.5;
    private static final double REACH = 8;
    private static final SoundEvent HURT = SoundEvent.createVariableRangeEvent(Wayfarer.id("great_worm.hurt"));
    private static final SoundEvent DEATH = SoundEvent.createVariableRangeEvent(Wayfarer.id("great_worm.death"));
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GreatWorm(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
        Player nearest = level().getNearestPlayer(this, LOOK);
        if (nearest != null) {
            getLookControl().setLookAt(nearest, 30, 30);
            float yaw = (float) Math.toDegrees(Math.atan2(nearest.getZ() - getZ(), nearest.getX() - getX())) - 90;
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || inEye(source)) {
            return super.hurt(source, amount);
        }
        if (!level().isClientSide()) {
            level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1, 0.5f);
        }
        return false;
    }

    private boolean inEye(DamageSource source) {
        AABB body = getBoundingBox();
        AABB eye = new AABB(body.minX, body.maxY - body.getYsize() * EYE_SHARE, body.minZ, body.maxX, body.maxY, body.maxZ).inflate(EYE_SLACK);
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) {
            return eye.contains(direct.position()) || eye.inflate(direct.getBbWidth()).intersects(direct.getBoundingBox());
        }
        if (direct instanceof LivingEntity striker) {
            Vec3 from = striker.getEyePosition();
            Vec3 to = from.add(striker.getLookAngle().scale(REACH));
            return eye.clip(from, to).isPresent();
        }
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "sway", 0, state -> state.setAndContinue(SWAY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
