package io.github.roman16.wayfarer.story;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class Clues {
    private static final float MARKER_SCALE = 1.02f;
    private static final float PRINT_DEPTH = 0.45f;
    private static final float PRINT_HEIGHT = 0.02f;
    private static final float PRINT_WIDTH = 0.25f;

    private Clues() {}

    static List<Entity> marker(ServerLevel level, BlockPos at, String clue) {
        return List.of(display(level, level.getBlockState(at), Vec3.atLowerCornerOf(at), MARKER_SCALE, clue), box(level, Vec3.atBottomCenterOf(at), clue));
    }

    static Entity print(ServerLevel level, BlockPos ground, String clue) {
        CompoundTag tag = tag(Blocks.COARSE_DIRT.defaultBlockState());
        tag.put("transformation", transformation(PRINT_WIDTH, PRINT_HEIGHT, PRINT_DEPTH, 0, 0, 0));
        return spawn(level, tag, new Vec3(ground.getX() + (1 - PRINT_WIDTH) / 2, ground.getY() + 0.01, ground.getZ() + (1 - PRINT_DEPTH) / 2), clue);
    }

    static Entity display(ServerLevel level, BlockState state, Vec3 at, float scale, String clue) {
        return display(level, state, at, scale, scale, scale, (1 - scale) / 2, clue);
    }

    static Entity item(ServerLevel level, BlockState state, Vec3 at, float x, float y, float z, String clue) {
        return display(level, state, at, x, y, z, 0, clue);
    }

    private static Entity display(ServerLevel level, BlockState state, Vec3 at, float x, float y, float z, float lift, String clue) {
        CompoundTag tag = tag(state);
        tag.put("transformation", transformation(x, y, z, (1 - x) / 2, lift, (1 - z) / 2));
        return spawn(level, tag, at, clue);
    }

    static Entity box(ServerLevel level, Vec3 at, String clue) {
        Entity box = EntityType.INTERACTION.create(level);
        box.moveTo(at.x, at.y, at.z);
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
