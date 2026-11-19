package io.github.roman16.wayfarer;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class Caverns {
    private static final ResourceLocation CHEESE = ResourceLocation.fromNamespaceAndPath("tectonic", "cave/cheese");
    private static final ResourceKey<DensityFunction> DEPTH = ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.fromNamespaceAndPath("tectonic", "depth"));
    private static final double BLOCKS_PER_DEPTH = 128;
    private static final double CHEESE_RANGE = 2;

    private Caverns() {}

    public static ResourceLocation cheese() {
        return CHEESE;
    }

    @SuppressWarnings("unchecked")
    public static Holder<DensityFunction> depth(HolderOwner<?> owner) {
        if (!(owner instanceof HolderGetter<?> getter)) {
            throw new IllegalStateException("caverns failed: Tectonic's cheese caves come from " + owner + ", which cannot look up " + DEPTH.location());
        }
        return ((HolderGetter<DensityFunction>) getter).get(DEPTH)
                .orElseThrow(() -> new IllegalStateException("caverns failed: Tectonic has no " + DEPTH.location()));
    }

    public static DensityFunction under(DensityFunction cheese, DensityFunction depth) {
        Balance.World world = Balance.get().world();
        DensityFunction open = DensityFunctions.mul(DensityFunctions.add(depth, DensityFunctions.constant(-world.cavernCoverBlocks() / BLOCKS_PER_DEPTH)),
                DensityFunctions.constant(BLOCKS_PER_DEPTH / world.cavernFadeBlocks())).clamp(0, 1);
        return DensityFunctions.add(cheese, DensityFunctions.mul(DensityFunctions.constant(-CHEESE_RANGE), DensityFunctions.add(open, DensityFunctions.constant(-1))));
    }
}
