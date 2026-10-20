package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Wayfarer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class Clues {
    static final String VEIL = Wayfarer.MODID + "_veil";
    static final String SEEN = Wayfarer.MODID + "_clue_seen";
    private static final float BOX = 1.0625f;
    private static final float MARKER_SCALE = 1.02f;
    private static final int FOUND_REACH = 64;
    private static final String DOOR = Wayfarer.MODID + "_door_glow";
    private static final float PRINT_DEPTH = 0.45f;
    private static final float PRINT_HEIGHT = 0.02f;
    private static final float PRINT_WIDTH = 0.25f;

    private Clues() {}

    static List<Entity> marker(ServerLevel level, BlockPos at, String clue) {
        return List.of(display(level, at, MARKER_SCALE, clue), box(level, Vec3.atBottomCenterOf(at), clue));
    }

    static void door(ServerLevel level, BlockPos lower, String clue) {
        display(level, lower, MARKER_SCALE, clue).addTag(DOOR);
        display(level, lower.above(), MARKER_SCALE, clue).addTag(DOOR);
        box(level, Vec3.atBottomCenterOf(lower), clue);
        box(level, Vec3.atBottomCenterOf(lower.above()), clue);
    }

    static void found(ServerLevel level, Entity found, String clue) {
        for (Entity entity : level.getEntities((Entity) null, found.getBoundingBox().inflate(FOUND_REACH), entity -> entity.getTags().contains(Steps.CLUE + clue))) {
            if (entity.getTags().contains(DOOR)) {
                entity.discard();
                continue;
            }
            entity.removeTag(Steps.CLUE + clue);
            if (entity instanceof Display) {
                CompoundTag tag = entity.saveWithoutId(new CompoundTag());
                tag.putInt("glow_color_override", Sense.SEEN);
                entity.load(tag);
                entity.addTag(SEEN);
            }
        }
    }

    static List<Entity> print(ServerLevel level, BlockPos ground, String clue) {
        CompoundTag tag = tag(Blocks.COARSE_DIRT.defaultBlockState());
        tag.put("transformation", transformation(PRINT_WIDTH, PRINT_HEIGHT, PRINT_DEPTH, 0, 0, 0));
        return List.of(spawn(level, tag, new Vec3(ground.getX() + (1 - PRINT_WIDTH) / 2, ground.getY() + 0.01, ground.getZ() + (1 - PRINT_DEPTH) / 2), clue),
                box(level, Vec3.atBottomCenterOf(ground), clue));
    }

    static Entity mark(ServerLevel level, Vec3 at, BlockState state, float width, float length, Vec3 along, String clue) {
        double turn = Math.atan2(along.x, along.z);
        float sin = (float) Math.sin(turn);
        float cos = (float) Math.cos(turn);
        CompoundTag tag = tag(state);
        CompoundTag transformation = transformation(width, PRINT_HEIGHT, length, -(width / 2 * cos + length / 2 * sin), 0, width / 2 * sin - length / 2 * cos);
        transformation.put("left_rotation", floats(0, (float) Math.sin(turn / 2), 0, (float) Math.cos(turn / 2)));
        tag.put("transformation", transformation);
        return spawn(level, tag, at, clue);
    }

    static Entity display(ServerLevel level, BlockPos at, float scale, String clue) {
        BlockState state = level.getBlockState(at);
        CompoundTag tag = tag(state);
        tag.put("transformation", transformation(scale, scale, scale, (1 - scale) / 2, (1 - scale) / 2, (1 - scale) / 2));
        if (state.isSolidRender(level, at)) {
            tag.put("brightness", brightness(level, at));
        }
        return spawn(level, tag, Vec3.atLowerCornerOf(at), clue);
    }

    private static CompoundTag brightness(ServerLevel level, BlockPos at) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("block", Arrays.stream(Direction.values()).mapToInt(side -> level.getBrightness(LightLayer.BLOCK, at.relative(side))).max().orElseThrow());
        tag.putInt("sky", Arrays.stream(Direction.values()).mapToInt(side -> level.getBrightness(LightLayer.SKY, at.relative(side))).max().orElseThrow());
        return tag;
    }

    static Entity veil(ServerLevel level, BlockPos at) {
        CompoundTag tag = tag(level.getBlockState(at));
        tag.put("transformation", transformation(MARKER_SCALE, MARKER_SCALE, MARKER_SCALE, (1 - MARKER_SCALE) / 2, (1 - MARKER_SCALE) / 2, (1 - MARKER_SCALE) / 2));
        tag.put("brightness", brightness(level, at));
        tag.putInt("glow_color_override", Sense.TRUE);
        Entity veil = EntityType.loadEntityRecursive(tag, level, loaded -> {
            loaded.moveTo(at.getX(), at.getY(), at.getZ(), 0, 0);
            return loaded;
        });
        if (veil == null) {
            throw new IllegalStateException("a block display could not be made for the false wall at " + at);
        }
        veil.addTag(VEIL);
        level.addFreshEntity(veil);
        return veil;
    }

    static Entity item(ServerLevel level, BlockState state, Vec3 at, float x, float y, float z, String clue) {
        CompoundTag tag = tag(state);
        tag.put("transformation", transformation(x, y, z, (1 - x) / 2, 0, (1 - z) / 2));
        return spawn(level, tag, at, clue);
    }

    static Entity box(ServerLevel level, Vec3 at, String clue) {
        return box(level, at.subtract(0, (BOX - 1) / 2, 0), clue, BOX, BOX);
    }

    static Entity box(ServerLevel level, Vec3 bottom, String clue, float width, float height) {
        Entity box = EntityType.INTERACTION.create(level);
        CompoundTag size = box.saveWithoutId(new CompoundTag());
        size.putFloat("width", width);
        size.putFloat("height", height);
        box.load(size);
        box.moveTo(bottom.x, bottom.y, bottom.z);
        box.addTag(Steps.CLUE + clue);
        box.addTag(Steps.CLUE_BOX);
        level.addFreshEntity(box);
        return box;
    }

    private static CompoundTag tag(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.putInt("glow_color_override", Sense.AMBER);
        return tag;
    }

    private static Entity spawn(ServerLevel level, CompoundTag tag, Vec3 at, String clue) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> {
            loaded.moveTo(at.x, at.y, at.z, 0, 0);
            return loaded;
        });
        if (entity == null) {
            throw new IllegalStateException("a block display could not be made for the clue " + clue);
        }
        entity.addTag(Steps.CLUE + clue);
        level.addFreshEntity(entity);
        return entity;
    }

    private static CompoundTag transformation(float x, float y, float z, float shiftX, float shiftY, float shiftZ) {
        CompoundTag tag = new CompoundTag();
        tag.put("left_rotation", floats(0, 0, 0, 1));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("scale", floats(x, y, z));
        tag.put("translation", floats(shiftX, shiftY, shiftZ));
        return tag;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
