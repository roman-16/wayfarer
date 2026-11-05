package io.github.roman16.wayfarer;

import com.mojang.logging.LogUtils;
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
import net.minecraft.core.QuartPos;
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
import org.slf4j.Logger;

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

    private static final int ARC_STEP_BLOCKS = 32;
    private static final long INLAND = Climate.quantizeCoord(0.03f);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEA_LEVEL = 63;

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
            positions.add(CompletableFuture.supplyAsync(() -> new ChunkPos(land(biomeSource, sampler, angle, radius).orElseGet(() -> {
                LOGGER.warn("no land within {} blocks along the ring at {}, {}: the stronghold stays at the ring's point", Balance.get().strongholds().landSearchBlocks(), x, z);
                return new BlockPos(x, SEA_LEVEL, z);
            })), Util.backgroundExecutor()));
        }
        return Util.sequence(positions);
    }

    private Optional<BlockPos> land(BiomeSource biomeSource, Climate.Sampler sampler, double angle, double radius) {
        int steps = Balance.get().strongholds().landSearchBlocks() / ARC_STEP_BLOCKS;
        for (int step = 0; step <= steps * 2; step++) {
            int along = (step + 1) / 2 * (step % 2 == 0 ? 1 : -1);
            double turned = angle + along * ARC_STEP_BLOCKS / radius;
            BlockPos at = new BlockPos((int) Math.round(Math.cos(turned) * radius), SEA_LEVEL, (int) Math.round(Math.sin(turned) * radius));
            int quartX = QuartPos.fromBlock(at.getX());
            int quartY = QuartPos.fromBlock(at.getY());
            int quartZ = QuartPos.fromBlock(at.getZ());
            if (sampler.sample(quartX, quartY, quartZ).continentalness() >= INLAND && preferredBiomes().contains(biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler))) {
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    @Override
    public StructurePlacementType<?> type() {
        return Wayfarer.EXACT_RING.get();
    }
}
