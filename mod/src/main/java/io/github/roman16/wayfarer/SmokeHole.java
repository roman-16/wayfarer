package io.github.roman16.wayfarer;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

public final class SmokeHole extends StructureProcessor {
    public static final SmokeHole INSTANCE = new SmokeHole();
    public static final MapCodec<SmokeHole> CODEC = MapCodec.unit(INSTANCE);
    private static final String TENTS = "nomadictents";
    private static final Map<StructureTemplate, Set<BlockPos>> HOLES = Collections.synchronizedMap(new WeakHashMap<>());

    private SmokeHole() {}

    @Override
    public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo,
            StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings, @Nullable StructureTemplate template) {
        if (!blockInfo.state().is(Blocks.BARRIER)) {
            return relativeBlockInfo;
        }
        return template != null && HOLES.computeIfAbsent(template, SmokeHole::holes).contains(blockInfo.pos()) ? relativeBlockInfo : null;
    }

    private static Set<BlockPos> holes(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        Set<BlockPos> tent = new HashSet<>();
        Set<BlockPos> barriers = new HashSet<>();
        for (Tag entry : saved.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            ListTag at = block.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(at.getInt(0), at.getInt(1), at.getInt(2));
            String name = palette.getCompound(block.getInt("state")).getString("Name");
            if (name.startsWith(TENTS + ":")) {
                tent.add(pos);
            } else if (name.equals("minecraft:barrier")) {
                barriers.add(pos);
            }
        }
        Set<BlockPos> holes = new HashSet<>();
        for (BlockPos barrier : barriers) {
            if (Direction.Plane.HORIZONTAL.stream().allMatch(side -> tent.contains(barrier.relative(side)))) {
                holes.add(barrier);
            }
        }
        return holes;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Wayfarer.SMOKE_HOLE.get();
    }
}
