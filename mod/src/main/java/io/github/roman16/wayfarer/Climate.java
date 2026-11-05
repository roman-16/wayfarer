package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

public enum Climate implements StringRepresentable {
    ANY,
    COLD,
    HOT,
    MILD;

    public static final Codec<Climate> CODEC = StringRepresentable.fromEnum(Climate::values);
    private static final float COLD_BELOW = 0.45f;
    private static final float HOT_FROM = 0.85f;
    private static final TagKey<Biome> COLD_BIOMES = tan("cold_biomes");
    private static final TagKey<Biome> HOT_BIOMES = tan("hot_biomes");
    private static final TagKey<Biome> ICY_BIOMES = tan("icy_biomes");
    private static final TagKey<Biome> NEUTRAL_BIOMES = tan("neutral_biomes");
    private static final TagKey<Biome> WARM_BIOMES = tan("warm_biomes");

    public static Climate of(Holder<Biome> biome) {
        if (biome.is(ICY_BIOMES) || biome.is(COLD_BIOMES)) {
            return COLD;
        }
        if (biome.is(NEUTRAL_BIOMES)) {
            return MILD;
        }
        if (biome.is(WARM_BIOMES) || biome.is(HOT_BIOMES)) {
            return HOT;
        }
        float temperature = biome.value().getBaseTemperature();
        return temperature < COLD_BELOW ? COLD : temperature >= HOT_FROM ? HOT : MILD;
    }

    public boolean fits(Holder<Biome> biome) {
        return this == ANY || of(biome) == this;
    }

    public boolean bears(Holder<Biome> biome) {
        return fits(biome) || of(biome) == MILD;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static TagKey<Biome> tan(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("toughasnails", path));
    }
}
