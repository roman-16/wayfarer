package io.github.roman16.wayfarer.story;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;

final class Sites {
    private Sites() {}

    static boolean open(Holder<Biome> biome) {
        return !biome.is(Tags.Biomes.IS_SWAMP) && !biome.is(BiomeTags.IS_JUNGLE) && !biome.is(Biomes.DARK_FOREST);
    }

}
