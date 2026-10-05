package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

public final class Burning extends SavedData {
    private static final String NAME = "wayfarer_burning";
    private final List<BoundingBox> boxes = new ArrayList<>();

    public static void hold(ServerLevel level, BoundingBox box) {
        Burning burning = of(level);
        burning.boxes.add(box);
        burning.setDirty();
    }

    public static boolean held(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD && of(level).boxes.stream().anyMatch(box -> box.isInside(pos));
    }

    private static Burning of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(Burning::new, Burning::load, null), NAME);
    }

    private static Burning load(CompoundTag tag, HolderLookup.Provider registries) {
        Burning burning = new Burning();
        for (Tag entry : tag.getList("boxes", Tag.TAG_INT_ARRAY)) {
            int[] box = ((IntArrayTag) entry).getAsIntArray();
            burning.boxes.add(new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
        }
        return burning;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        boxes.forEach(box -> list.add(new IntArrayTag(new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})));
        tag.put("boxes", list);
        return tag;
    }
}
