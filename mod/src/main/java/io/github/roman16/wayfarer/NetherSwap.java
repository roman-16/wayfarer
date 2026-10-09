package io.github.roman16.wayfarer;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

public final class NetherSwap extends StructureProcessor {
    public static final NetherSwap INSTANCE = new NetherSwap();
    public static final MapCodec<NetherSwap> CODEC = MapCodec.unit(INSTANCE);
    private static final String ANCIENT_CITY = "ancient_city/";
    private static final Bundled<Map<Block, Block>> SWAPS = Bundled.json("/wayfarer/swaps.json", json -> json.entrySet().stream().collect(
            Collectors.toUnmodifiableMap(entry -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getKey())),
                    entry -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getValue().getAsString())))));

    private NetherSwap() {}

    public static boolean swaps(ResourceLocation template) {
        return !(template.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && template.getPath().startsWith(ANCIENT_CITY));
    }

    public static void addTo(StructurePlaceSettings settings) {
        if (!settings.getProcessors().contains(INSTANCE)) {
            settings.addProcessor(INSTANCE);
        }
    }

    @Override
    public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo,
            StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings, @Nullable StructureTemplate template) {
        Block swap = SWAPS.get().get(relativeBlockInfo.state().getBlock());
        if (swap == null) {
            return relativeBlockInfo;
        }
        return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), carry(relativeBlockInfo.state(), swap.defaultBlockState()), relativeBlockInfo.nbt());
    }

    private static BlockState carry(BlockState from, BlockState to) {
        BlockState carried = to;
        for (Property<?> property : from.getProperties()) {
            carried = copy(from, carried, property);
        }
        return carried;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        for (Property<?> target : to.getProperties()) {
            if (target.getName().equals(property.getName()) && target.getValueClass() == property.getValueClass()) {
                @SuppressWarnings("unchecked")
                Property<T> same = (Property<T>) target;
                T value = from.getValue(property);
                return same.getPossibleValues().contains(value) ? to.setValue(same, value) : to;
            }
        }
        return to;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Wayfarer.NETHER_SWAP.get();
    }
}
