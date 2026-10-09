package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

@SuppressWarnings("deprecation")
public class BandPlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<BandPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        var base = placementCodec(instance);
        var band = instance.group(
                Codec.intRange(1, 4096).fieldOf("spacing").forGetter(BandPlacement::spacing),
                Codec.intRange(0, 4096).fieldOf("separation").forGetter(BandPlacement::separation),
                Codec.intRange(0, 30_000_000).fieldOf("from_blocks").forGetter(BandPlacement::fromBlocks),
                Codec.intRange(0, 30_000_000).fieldOf("to_blocks").forGetter(BandPlacement::toBlocks));
        return instance.group(base.t1(), base.t2(), base.t3(), base.t4(), base.t5(), band.t1(), band.t2(), band.t3(), band.t4())
                .apply(instance, BandPlacement::new);
    });
    private static final int CHUNK_CENTRE = 8;

    private final int fromBlocks;
    private final int toBlocks;

    public BandPlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            int spacing,
            int separation,
            int fromBlocks,
            int toBlocks) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, RandomSpreadType.LINEAR);
        this.fromBlocks = fromBlocks;
        this.toBlocks = toBlocks;
    }

    public int fromBlocks() {
        return fromBlocks;
    }

    public int toBlocks() {
        return toBlocks;
    }

    @Override
    public boolean applyAdditionalChunkRestrictions(int chunkX, int chunkZ, long levelSeed) {
        double x = chunkX * 16.0 + CHUNK_CENTRE;
        double z = chunkZ * 16.0 + CHUNK_CENTRE;
        double distance = Math.sqrt(x * x + z * z);
        return distance >= fromBlocks && distance < toBlocks && super.applyAdditionalChunkRestrictions(chunkX, chunkZ, levelSeed);
    }

    @Override
    public StructurePlacementType<?> type() {
        return Wayfarer.BAND.get();
    }
}
