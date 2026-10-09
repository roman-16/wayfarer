package io.github.roman16.wayfarer;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class Ores {
    private static final TagKey<Structure> RICH = TagKey.create(Registries.STRUCTURE, Wayfarer.id("rich_ore"));

    private Ores() {}

    public static boolean keeps(FeaturePlaceContext<OreConfiguration> context) {
        double share = share(context.config());
        return share >= 1 || context.random().nextDouble() < share || rich(context.level(), context.origin());
    }

    private static double share(OreConfiguration config) {
        for (OreConfiguration.TargetBlockState target : config.targetStates) {
            for (Map.Entry<String, Double> ore : Balance.get().ore().shares().entrySet()) {
                if (target.state.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores/" + ore.getKey())))) {
                    return ore.getValue();
                }
            }
        }
        return 1;
    }

    private static boolean rich(WorldGenLevel level, BlockPos origin) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return level.getChunk(origin).getAllReferences().keySet().stream()
                .anyMatch(structure -> structures.wrapAsHolder(structure).is(RICH));
    }
}
