package io.github.roman16.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.fml.ModList;

public final class WorldShape implements DensityFunction {
    public static final MapCodec<WorldShape> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(WorldShape::input),
            Layout.CODEC.forGetter(WorldShape::layout)).apply(instance, WorldShape::new));
    private static final KeyDispatchDataCodec<WorldShape> DISPATCH = KeyDispatchDataCodec.of(CODEC);
    private static final ResourceLocation TECTONIC = ResourceLocation.fromNamespaceAndPath("tectonic", "noise/raw_continents");
    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("overworld/continents");
    private static final double LAND = -0.19;
    public static final int DIRECTIONS = 720;
    private static final int SMOOTHED = 10;
    private static final int SCAN_BLOCKS = 32;
    private static final int FINGERPRINT_BLOCKS = 8192;
    private static final int FINGERPRINTS = 8;
    private static final int KEPT = 4;
    private static final Map<Key, Coasts> MEASURED = new LinkedHashMap<>(KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Coasts> eldest) {
            return size() > KEPT;
        }
    };
    private static final Map<RandomState, Optional<Coasts>> WORLDS = new WeakHashMap<>();
    private final DensityFunction input;
    private final Layout layout;
    @Nullable
    private volatile Coasts coasts;

    public WorldShape(DensityFunction input, Layout layout) {
        this.input = input;
        this.layout = layout;
    }

    public DensityFunction input() {
        return input;
    }

    public Layout layout() {
        return layout;
    }

    public record Layout(double beyondLift, int coast, int farShore, double highest, int home, int lastSeaSlope, double lift, double seaFloor, double seaSink, int shore) {
        static final MapCodec<Layout> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.DOUBLE.fieldOf("beyond_lift").forGetter(Layout::beyondLift),
                Codec.INT.fieldOf("coast").forGetter(Layout::coast),
                Codec.INT.fieldOf("far_shore").forGetter(Layout::farShore),
                Codec.DOUBLE.fieldOf("highest").forGetter(Layout::highest),
                Codec.INT.fieldOf("home").forGetter(Layout::home),
                Codec.INT.fieldOf("last_sea_slope").forGetter(Layout::lastSeaSlope),
                Codec.DOUBLE.fieldOf("lift").forGetter(Layout::lift),
                Codec.DOUBLE.fieldOf("sea_floor").forGetter(Layout::seaFloor),
                Codec.DOUBLE.fieldOf("sea_sink").forGetter(Layout::seaSink),
                Codec.INT.fieldOf("shore").forGetter(Layout::shore)).apply(instance, Layout::new));

        public static Layout of(Balance balance) {
            Balance.World world = balance.world();
            return new Layout(world.beyondLift(), world.coastBlocks(), world.farShoreBlocks(), world.highest(), world.homeBlocks(),
                    balance.strongholds().radiusBlocks() + world.lastSeaSlopePastRingBlocks(), world.lift(), world.seaFloor(), world.seaSink(), world.shoreBlocks());
        }

        int homeLand() {
            return home - coast;
        }

        double homeFall(double distance) {
            if (distance < homeLand()) {
                return lift;
            }
            if (distance < home) {
                return lift - (lift + seaSink) * (distance - homeLand()) / coast;
            }
            return -seaSink;
        }

        double bias(double distance, double farShoreEnd) {
            if (distance >= lastSeaSlope()) {
                return Math.max(-seaSink, beyondLift - (beyondLift + seaSink) * (distance - lastSeaSlope()) / coast);
            }
            if (distance >= farShoreEnd) {
                return beyondLift;
            }
            double fall = homeFall(distance);
            double riseFrom = farShoreEnd - shore;
            return distance < riseFrom ? fall : Math.max(fall, -seaSink + (beyondLift + seaSink) * (distance - riseFrom) / shore);
        }

        double shaped(double continents, double distance, double farShoreEnd) {
            return Math.min(highest, Math.max(seaFloor, continents + bias(distance, farShoreEnd)));
        }
    }

    public record Coasts(int[] home, int[] far, int[] last, int farShore) {
        public double home(double angle) {
            return along(home, angle);
        }

        public double far(double angle) {
            return along(far, angle);
        }

        public double last(double angle) {
            return along(last, angle);
        }

        double farShoreEnd(double angle) {
            return home(angle) + farShore;
        }

        public boolean beyond(BlockPos at) {
            double angle = angle(at.getX(), at.getZ());
            return Math.sqrt((double) at.getX() * at.getX() + (double) at.getZ() * at.getZ()) > (home(angle) + far(angle)) / 2;
        }

        public OptionalDouble shore(double angle, double distance) {
            double last = last(angle);
            if (distance > last) {
                return OptionalDouble.of(last);
            }
            double home = home(angle);
            double far = far(angle);
            if (distance > home && distance < far) {
                return OptionalDouble.of(distance - home < far - distance ? home : far);
            }
            return OptionalDouble.empty();
        }

        public static double angle(double x, double z) {
            return Math.atan2(z, x);
        }

        private static double along(int[] coast, double angle) {
            double at = Math.floorMod((long) Math.floor(angle / (2 * Math.PI) * DIRECTIONS * 1024), (long) DIRECTIONS * 1024) / 1024.0;
            int index = (int) at;
            double past = at - index;
            return coast[index] * (1 - past) + coast[(index + 1) % DIRECTIONS] * past;
        }
    }

    private record Key(Layout layout, List<Double> fingerprint) {}

    public static WorldShape around(DensityFunction input) {
        return new WorldShape(input, Layout.of(Balance.get()));
    }

    public static ResourceLocation continents() {
        return ModList.get().isLoaded("tectonic") ? TECTONIC : VANILLA;
    }

    public static Optional<Coasts> coasts(ServerLevel level) {
        RandomState state = level.getChunkSource().randomState();
        synchronized (WORLDS) {
            Optional<Coasts> known = WORLDS.get(state);
            if (known != null) {
                return known;
            }
        }
        List<WorldShape> found = new ArrayList<>();
        state.router().continents().mapAll(function -> {
            if (function instanceof WorldShape shape) {
                found.add(shape);
            }
            return function;
        });
        Optional<Coasts> coasts = found.stream().findFirst().map(WorldShape::coasts);
        synchronized (WORLDS) {
            WORLDS.put(state, coasts);
        }
        return coasts;
    }

    public Coasts coasts() {
        Coasts known = coasts;
        if (known == null) {
            known = measured();
            coasts = known;
        }
        return known;
    }

    private Coasts measured() {
        List<Double> fingerprint = new ArrayList<>(FINGERPRINTS);
        for (int index = 0; index < FINGERPRINTS; index++) {
            double angle = 2 * Math.PI * index / FINGERPRINTS;
            fingerprint.add(input.compute(point(Math.cos(angle) * FINGERPRINT_BLOCKS, Math.sin(angle) * FINGERPRINT_BLOCKS)));
        }
        synchronized (MEASURED) {
            return MEASURED.computeIfAbsent(new Key(layout, fingerprint), key -> measure());
        }
    }

    private Coasts measure() {
        int[] reached = new int[DIRECTIONS];
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            double angle = 2 * Math.PI * direction / DIRECTIONS;
            reached[direction] = layout.homeLand();
            for (int distance = layout.home(); distance > layout.homeLand(); distance -= SCAN_BLOCKS) {
                if (input.compute(point(Math.cos(angle) * distance, Math.sin(angle) * distance)) + layout.homeFall(distance) > LAND) {
                    reached[direction] = distance;
                    break;
                }
            }
        }
        int[] outermost = new int[DIRECTIONS];
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int near = -SMOOTHED; near <= SMOOTHED; near++) {
                outermost[direction] = Math.max(outermost[direction], reached[Math.floorMod(direction + near, DIRECTIONS)]);
            }
        }
        int[] home = new int[DIRECTIONS];
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            long sum = 0;
            for (int near = -SMOOTHED; near <= SMOOTHED; near++) {
                sum += outermost[Math.floorMod(direction + near, DIRECTIONS)];
            }
            home[direction] = (int) Math.round(sum / (2.0 * SMOOTHED + 1));
        }
        int[] far = new int[DIRECTIONS];
        int[] last = new int[DIRECTIONS];
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            double angle = 2 * Math.PI * direction / DIRECTIONS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int end = home[direction] + layout.farShore();
            far[direction] = end;
            for (int distance = home[direction] + SCAN_BLOCKS; distance < end; distance += SCAN_BLOCKS) {
                if (layout.shaped(input.compute(point(cos * distance, sin * distance)), distance, end) > LAND) {
                    far[direction] = distance;
                    break;
                }
            }
            last[direction] = layout.lastSeaSlope();
            for (int distance = layout.lastSeaSlope() + layout.coast(); distance > layout.lastSeaSlope(); distance -= SCAN_BLOCKS) {
                if (layout.shaped(input.compute(point(cos * distance, sin * distance)), distance, end) > LAND) {
                    last[direction] = distance;
                    break;
                }
            }
        }
        return new Coasts(home, far, last, layout.farShore());
    }

    private static FunctionContext point(double x, double z) {
        return new SinglePointContext((int) Math.round(x), 0, (int) Math.round(z));
    }

    @Override
    public double compute(FunctionContext context) {
        double x = context.blockX();
        double z = context.blockZ();
        double distance = Math.sqrt(x * x + z * z);
        double continents = input.compute(context);
        if (distance < layout.homeLand()) {
            return Math.min(layout.highest(), Math.max(layout.seaFloor(), continents + layout.lift()));
        }
        return layout.shaped(continents, distance, coasts().farShoreEnd(Coasts.angle(x, z)));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new WorldShape(input.mapAll(visitor), layout));
    }

    @Override
    public double minValue() {
        return Math.min(layout.highest(), Math.max(layout.seaFloor(), input.minValue() - layout.seaSink()));
    }

    @Override
    public double maxValue() {
        return Math.min(layout.highest(), input.maxValue() + Math.max(layout.lift(), layout.beyondLift()));
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DISPATCH;
    }
}
