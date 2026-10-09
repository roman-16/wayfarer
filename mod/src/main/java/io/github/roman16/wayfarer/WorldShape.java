package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.fml.ModList;

public record WorldShape(DensityFunction input, int home, int coast, int sea, int shore, int lastSea, double lift, double seaSink, double outerSink,
        double lastSeaSink, double seaFloor, double highest) implements DensityFunction {
    public static final MapCodec<WorldShape> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(WorldShape::input),
            Codec.INT.fieldOf("home").forGetter(WorldShape::home),
            Codec.INT.fieldOf("coast").forGetter(WorldShape::coast),
            Codec.INT.fieldOf("sea").forGetter(WorldShape::sea),
            Codec.INT.fieldOf("shore").forGetter(WorldShape::shore),
            Codec.INT.fieldOf("last_sea").forGetter(WorldShape::lastSea),
            Codec.DOUBLE.fieldOf("lift").forGetter(WorldShape::lift),
            Codec.DOUBLE.fieldOf("sea_sink").forGetter(WorldShape::seaSink),
            Codec.DOUBLE.fieldOf("outer_sink").forGetter(WorldShape::outerSink),
            Codec.DOUBLE.fieldOf("last_sea_sink").forGetter(WorldShape::lastSeaSink),
            Codec.DOUBLE.fieldOf("sea_floor").forGetter(WorldShape::seaFloor),
            Codec.DOUBLE.fieldOf("highest").forGetter(WorldShape::highest)).apply(instance, WorldShape::new));
    private static final KeyDispatchDataCodec<WorldShape> DISPATCH = KeyDispatchDataCodec.of(CODEC);
    private static final ResourceLocation TECTONIC = ResourceLocation.fromNamespaceAndPath("tectonic", "noise/raw_continents");
    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("overworld/continents");

    public static WorldShape around(DensityFunction input) {
        Balance.World world = Balance.get().world();
        return new WorldShape(input, world.homeBlocks(), world.coastBlocks(), world.seaBlocks(), world.shoreBlocks(),
                Balance.get().strongholds().radiusBlocks() + world.lastSeaPastRingBlocks(), world.lift(), world.seaSink(), world.outerSink(),
                world.lastSeaSink(), world.seaFloor(), world.highest());
    }

    public static ResourceLocation continents() {
        return ModList.get().isLoaded("tectonic") ? TECTONIC : VANILLA;
    }

    public double bias(double distance) {
        if (distance >= lastSea) {
            return -lastSeaSink;
        }
        if (distance >= lastSea - shore) {
            return -outerSink + (outerSink - lastSeaSink) * (distance - (lastSea - shore)) / shore;
        }
        if (distance < home - coast) {
            return lift;
        }
        if (distance < home) {
            return lift - (lift + seaSink) * (distance - (home - coast)) / coast;
        }
        if (distance < home + sea - shore) {
            return -seaSink;
        }
        if (distance < home + sea) {
            return -seaSink + (seaSink - outerSink) * (distance - (home + sea - shore)) / shore;
        }
        return -outerSink;
    }

    public boolean open(double distance) {
        return distance >= home && distance < home + sea || distance >= lastSea - shore;
    }

    public double shaped(double continents, double distance) {
        double raised = continents + bias(distance);
        return Math.min(highest, open(distance) ? Math.max(seaFloor, raised) : raised);
    }

    @Override
    public double compute(FunctionContext context) {
        return shaped(input.compute(context), Math.sqrt((double) context.blockX() * context.blockX() + (double) context.blockZ() * context.blockZ()));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new WorldShape(input.mapAll(visitor), home, coast, sea, shore, lastSea, lift, seaSink, outerSink, lastSeaSink, seaFloor, highest));
    }

    @Override
    public double minValue() {
        return Math.min(input.minValue() - Math.max(seaSink, lastSeaSink), seaFloor);
    }

    @Override
    public double maxValue() {
        return Math.min(highest, input.maxValue() + lift);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
