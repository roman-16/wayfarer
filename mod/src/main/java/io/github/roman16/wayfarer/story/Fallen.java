package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Fallen extends Entity {
    private static final EntityDataAccessor<String> CREATURE = SynchedEntityData.defineId(Fallen.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LEFT_DOWN = SynchedEntityData.defineId(Fallen.class, EntityDataSerializers.BOOLEAN);
    private static final String CREATURE_KEY = "Creature";
    private static final String LEFT_DOWN_KEY = "LeftDown";
    private static final double MODEL_REACH = 1;

    public Fallen(EntityType<? extends Fallen> type, Level level) {
        super(type, level);
    }

    public static Fallen lay(ServerLevel level, EntityType<?> creature, Vec3 at, float yaw, boolean leftDown, String... tags) {
        Fallen body = new Fallen(Wayfarer.FALLEN.get(), level);
        body.entityData.set(CREATURE, BuiltInRegistries.ENTITY_TYPE.getKey(creature).toString());
        body.entityData.set(LEFT_DOWN, leftDown);
        body.moveTo(at.x, at.y, at.z, yaw, 0);
        for (String tag : tags) {
            body.addTag(tag);
        }
        level.addFreshEntity(body);
        return body;
    }

    public EntityType<?> creature() {
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityData.get(CREATURE)));
    }

    public boolean leftDown() {
        return entityData.get(LEFT_DOWN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CREATURE, BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.MULE).toString());
        builder.define(LEFT_DOWN, true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(CREATURE, tag.getString(CREATURE_KEY));
        entityData.set(LEFT_DOWN, tag.getBoolean(LEFT_DOWN_KEY));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(CREATURE_KEY, entityData.get(CREATURE));
        tag.putBoolean(LEFT_DOWN_KEY, leftDown());
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable(getType().getDescriptionId(), creature().getDescription());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(MODEL_REACH);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }
}
