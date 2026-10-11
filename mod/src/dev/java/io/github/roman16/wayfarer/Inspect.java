package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.Comparator;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

final class Inspect {
    private static final int REACH = 64;

    private Inspect() {}

    static String block(ServerLevel level, BlockPos pos) {
        JsonObject result = new JsonObject();
        result.addProperty("block", BlockStateParser.serialize(level.getBlockState(pos)));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) {
            result.add("entity", json(entity.saveWithFullMetadata(level.registryAccess())));
        }
        return result.toString();
    }

    static String creature(ServerPlayer player, ResourceLocation id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("there is no creature called " + id));
        Entity nearest = player.serverLevel().getEntities(type, player.getBoundingBox().inflate(REACH), entity -> entity != player).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElseThrow(() -> new IllegalArgumentException("no " + id + " within " + REACH + " blocks"));
        JsonObject result = new JsonObject();
        result.addProperty("type", id.toString());
        result.addProperty("uuid", nearest.getStringUUID());
        JsonArray pos = new JsonArray();
        pos.add(nearest.getX());
        pos.add(nearest.getY());
        pos.add(nearest.getZ());
        result.add("pos", pos);
        if (nearest instanceof Mob mob) {
            result.addProperty("target", mob.getTarget() == null ? "none" : mob.getTarget().getName().getString());
            JsonArray goals = new JsonArray();
            mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(goal -> goals.add(goal.getGoal().getClass().getName()));
            mob.targetSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(goal -> goals.add(goal.getGoal().getClass().getName()));
            result.add("goals", goals);
        }
        result.add("nbt", json(nearest.saveWithoutId(new CompoundTag())));
        return result.toString();
    }

    static String item(ServerPlayer player) {
        return stack(player, player.getMainHandItem()).toString();
    }

    static String inventory(ServerPlayer player) {
        JsonArray slots = new JsonArray();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", slot);
                entry.add("item", stack(player, stack));
                slots.add(entry);
            }
        }
        return slots.toString();
    }

    private static JsonElement stack(ServerPlayer player, ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack)
                .result()
                .orElseGet(() -> new JsonPrimitive("unreadable " + stack));
    }

    private static JsonElement json(CompoundTag tag) {
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
    }
}
