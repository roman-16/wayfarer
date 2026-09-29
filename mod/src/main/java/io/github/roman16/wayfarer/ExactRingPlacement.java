package io.github.roman16.wayfarer;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

@SuppressWarnings("deprecation")
public class ExactRingPlacement extends ConcentricRingsStructurePlacement {
    public static final MapCodec<ExactRingPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        var base = placementCodec(instance);
        var ring = instance.group(
                Codec.intRange(1, 4095).fieldOf("count").forGetter(ExactRingPlacement::count),
                Codec.intRange(0, 1_000_000).fieldOf("jitter_blocks").forGetter(ExactRingPlacement::jitterBlocks),
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("preferred_biomes").forGetter(ExactRingPlacement::preferredBiomes),
                Codec.intRange(0, 10_000_000).fieldOf("radius_blocks").forGetter(ExactRingPlacement::radiusBlocks));
        return instance.group(base.t1(), base.t2(), base.t3(), base.t4(), base.t5(), ring.t1(), ring.t2(), ring.t3(), ring.t4())
                .apply(instance, ExactRingPlacement::new);
    });

    private static final int BIOME_SEARCH_BLOCKS = 112;

    private final int jitterBlocks;
    private final int radiusBlocks;

    public ExactRingPlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            int count,
            int jitterBlocks,
            HolderSet<Biome> preferredBiomes,
            int radiusBlocks) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, 0, count, count, preferredBiomes);
        this.jitterBlocks = jitterBlocks;
        this.radiusBlocks = radiusBlocks;
    }

    public int jitterBlocks() {
        return jitterBlocks;
    }

    public int radiusBlocks() {
        return radiusBlocks;
    }

    public CompletableFuture<List<ChunkPos>> positions(long seed, BiomeSource biomeSource, Climate.Sampler sampler) {
        RandomSource random = RandomSource.create(seed);
        double start = random.nextDouble() * Math.PI * 2;
        double step = Math.PI * 2 / count();
        List<CompletableFuture<ChunkPos>> positions = new ArrayList<>(count());
        for (int i = 0; i < count(); i++) {
            double angle = start + i * step;
            double radius = radiusBlocks + (random.nextDouble() * 2 - 1) * jitterBlocks;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            RandomSource search = random.fork();
            positions.add(CompletableFuture.supplyAsync(() -> {
                Pair<BlockPos, ?> found = biomeSource.findBiomeHorizontal(
                        x, 0, z, BIOME_SEARCH_BLOCKS, preferredBiomes()::contains, search, sampler);
                BlockPos pos = found == null ? new BlockPos(x, 0, z) : found.getFirst();
                return new ChunkPos(pos);
            }, Util.backgroundExecutor()));
        }
        return Util.sequence(positions);
    }

    @Override
    public StructurePlacementType<?> type() {
        return Wayfarer.EXACT_RING.get();
    }
}
