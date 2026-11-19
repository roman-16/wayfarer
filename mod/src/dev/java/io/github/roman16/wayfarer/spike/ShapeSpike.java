package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.WorldShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class ShapeSpike {
    private static final int GAMEMASTER = 2;
    private static final int SURFACE = 63;
    private static final byte SEA = 0;
    private static final byte DEEP = 1;
    private static final byte RIVER = 2;
    private static final byte LAND = 3;
    private static final String[] HALL_LANDS = {"c:is_badlands", "c:is_beach", "c:is_dark_forest", "c:is_desert", "c:is_forest", "c:is_icy", "c:is_jungle",
            "c:is_mountain", "c:is_old_growth", "c:is_plains", "c:is_savanna", "c:is_snowy", "c:is_swamp"};
    private static final int HOME = 0x4E8A3A;
    private static final int AWAY = 0x9A8A4A;
    private static final int OCEAN = 0x2F5E9E;
    private static final int ABYSS = 0x1B3561;
    private static final int STREAM = 0x6FA8DC;
    private static final int RING = 0xFFFFFF;
    private static final int ORIGIN = 0xE03030;
    private static final int[] WARMTH = {0xE8F4FF, 0x5B8FD6, 0x4E8A3A, 0xD9A441, 0xC0392B};
    private static final int WAYS = 16;
    private static final int CROSSING_WAYS = 360;
    private static final int FLOOR_WAYS = 64;
    private static final int OUTER_EDGE_BLOCKS = 1024;
    private static final int LAST_SEA_FLOOR_BLOCKS = 1024;
    private static final int FAR_FROM_CHANGE = 1024;
    private static final int ZONE_BLOCKS = 512;
    private static final int CENSUS = 4;
    private static final int COLDER_ABOVE = 160;
    private static final TagKey<Biome> COLD_BIOMES = toughAsNails("cold_biomes");
    private static final TagKey<Biome> HOT_BIOMES = toughAsNails("hot_biomes");
    private static final TagKey<Biome> ICY_BIOMES = toughAsNails("icy_biomes");
    private static final TagKey<Biome> NEUTRAL_BIOMES = toughAsNails("neutral_biomes");
    private static final TagKey<Biome> WARM_BIOMES = toughAsNails("warm_biomes");
    private static final TagKey<Biome> SKYLANDS = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terralith", "skylands"));

    private enum Warmth {
        ICY,
        COLD,
        NEUTRAL,
        WARM,
        HOT;

        private Warmth colder() {
            return values()[Math.max(0, ordinal() - 1)];
        }
    }

    private ShapeSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("shape")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(256))
                                        .then(Commands.argument("step", IntegerArgumentType.integer(16))
                                                .executes(context -> shape(context.getSource(), LongArgumentType.getLong(context, "seed"),
                                                        IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "step"), null, null))
                                                .then(Commands.argument("beyond_lift", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("far_shore", IntegerArgumentType.integer(0))
                                                                .executes(context -> shape(context.getSource(), LongArgumentType.getLong(context, "seed"),
                                                                        IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "step"),
                                                                        DoubleArgumentType.getDouble(context, "beyond_lift"), IntegerArgumentType.getInteger(context, "far_shore")))))))))
                .then(Commands.literal("climate")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .then(Commands.argument("step", IntegerArgumentType.integer(16))
                                        .executes(context -> climate(context.getSource(), LongArgumentType.getLong(context, "seed"),
                                                IntegerArgumentType.getInteger(context, "step")))))));
    }

    private static int climate(CommandSourceStack source, long seed, int step) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            source.sendFailure(Component.literal("climate failed: the overworld is not drawn from noise"));
            return 0;
        }
        RandomState state = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        CompletableFuture.supplyAsync(() -> measure(level, generator, state, seed, step), Util.backgroundExecutor())
                .whenComplete((lines, error) -> server.execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("climate failed: " + error.getMessage()));
                    } else {
                        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
                    }
                }));
        source.sendSuccess(() -> Component.literal("climate " + seed + ": measuring"), false);
        return 1;
    }

    private static List<String> measure(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState state, long seed, int step) {
        int radius = Balance.get().world().homeBlocks();
        int size = radius * 2 / step + 1;
        int centre = size / 2;
        BiomeSource biomes = generator.getBiomeSource();
        byte[] cells = new byte[size * size];
        for (int index = 0; index < cells.length; index++) {
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(-radius + index % size * step), QuartPos.fromBlock(SURFACE),
                    QuartPos.fromBlock(-radius + index / size * step), state.sampler());
            cells[index] = biome.is(BiomeTags.IS_DEEP_OCEAN) ? DEEP : biome.is(BiomeTags.IS_OCEAN) ? SEA : biome.is(BiomeTags.IS_RIVER) ? RIVER : LAND;
        }
        int start = nearestLand(cells, size, centre);
        if (start < 0) {
            throw new IllegalStateException("no land within " + radius + " blocks");
        }
        boolean[] home = new boolean[size * size];
        long homeCells = fill(cells, size, start, home);
        int drop = COLDER_ABOVE;
        Warmth[] felt = new Warmth[size * size];
        long high = 0;
        long[] shares = new long[Warmth.values().length];
        List<Map<String, Long>> census = new ArrayList<>();
        for (Warmth ignored : Warmth.values()) {
            census.add(new TreeMap<>());
        }
        long measured = 0;
        for (int index = 0; index < cells.length; index++) {
            if (!home[index] || cells[index] != LAND) {
                continue;
            }
            int x = -radius + index % size * step;
            int z = -radius + index / size * step;
            int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, state);
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), state.sampler());
            Warmth warmth = warmth(biome);
            String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("?");
            if (y > drop) {
                high++;
                warmth = warmth.colder();
                name += " high";
            }
            felt[index] = warmth;
            shares[warmth.ordinal()]++;
            census.get(warmth.ordinal()).merge(name, 1L, Long::sum);
            measured++;
        }
        int camp = felt[start] != null ? start : nearestFelt(felt, size, start);
        Warmth[] raw = felt;
        felt = zones(raw, size, ZONE_BLOCKS * ZONE_BLOCKS / (step * step));
        List<String> lines = new ArrayList<>();
        StringBuilder land = new StringBuilder(String.format(Locale.ROOT, "climate %d: the Last Camp's land around %d %d, %.0f km2; by day on open ground:",
                seed, -radius + camp % size * step, -radius + camp / size * step, homeCells * (double) step * step / 1_000_000));
        for (Warmth warmth : Warmth.values()) {
            land.append(String.format(Locale.ROOT, " %s %.0f%%", warmth.name().toLowerCase(Locale.ROOT), 100.0 * shares[warmth.ordinal()] / measured));
        }
        land.append(String.format(Locale.ROOT, "; %.0f%% lies above y %d, a step colder there", 100.0 * high / measured, drop));
        lines.add(land.toString());
        for (Warmth warmth : Warmth.values()) {
            long all = shares[warmth.ordinal()];
            if (all == 0) {
                continue;
            }
            StringBuilder made = new StringBuilder(String.format(Locale.ROOT, "climate %d: %s is", seed, warmth.name().toLowerCase(Locale.ROOT)));
            census.get(warmth.ordinal()).entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(CENSUS)
                    .forEach(entry -> made.append(String.format(Locale.ROOT, " %s %.0f%%", entry.getKey(), 100.0 * entry.getValue() / all)));
            lines.add(made.toString());
        }
        lines.add(String.format(Locale.ROOT, "climate %d: zones count from %d by %d blocks; smaller patches go with what surrounds them", seed, ZONE_BLOCKS, ZONE_BLOCKS));
        List<Integer> firsts = new ArrayList<>();
        int unchanged = 0;
        for (int way = 0; way < WAYS; way++) {
            double angle = Math.PI * 2 * way / WAYS;
            int found = -1;
            for (int along = step; ; along += step) {
                int column = camp % size + (int) Math.round(Math.cos(angle) * along / step);
                int row = camp / size + (int) Math.round(Math.sin(angle) * along / step);
                if (column < 0 || row < 0 || column >= size || row >= size || !home[row * size + column]) {
                    break;
                }
                Warmth here = felt[row * size + column];
                if (here != null && here != felt[camp]) {
                    found = along;
                    break;
                }
            }
            if (found < 0) {
                unchanged++;
            } else {
                firsts.add(found);
            }
        }
        Collections.sort(firsts);
        lines.add(String.format(Locale.ROOT, "climate %d: from the camp (%s) the temperature first changes after %s blocks (median of %d ways, %s), %d reach the coast unchanged",
                seed, felt[camp].name().toLowerCase(Locale.ROOT), firsts.isEmpty() ? "no" : String.valueOf(median(firsts)), firsts.size(),
                firsts.isEmpty() ? "none" : firsts.getFirst() + " to " + firsts.getLast(), unchanged));
        long pairs = 0;
        long changes = 0;
        for (int line = 0; line < size; line++) {
            for (boolean rows : new boolean[] {true, false}) {
                Warmth previous = null;
                for (int along = 0; along < size; along++) {
                    int index = rows ? line * size + along : along * size + line;
                    if (!home[index]) {
                        previous = null;
                        continue;
                    }
                    if (felt[index] == null) {
                        continue;
                    }
                    if (previous != null) {
                        pairs++;
                        if (felt[index] != previous) {
                            changes++;
                        }
                    }
                    previous = felt[index];
                }
            }
        }
        lines.add(String.format(Locale.ROOT, "climate %d: a straight line across the land changes temperature every %d blocks on average", seed,
                changes == 0 ? 0 : pairs * step / changes));
        lines.add(String.format(Locale.ROOT, "climate %d: from icy or cold land the nearest neutral or warmer land lies %s; from warm or hot land the nearest neutral or colder %s",
                seed, reach(felt, size, step, true), reach(felt, size, step, false)));
        Path picture = FMLPaths.GAMEDIR.get().resolve("../shape").normalize().resolve(seed + "-climate.png");
        try {
            Files.createDirectories(picture.getParent());
            ImageIO.write(drawClimate(cells, home, raw, size, camp), "png", picture.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException(picture + " could not be written: " + exception.getMessage());
        }
        lines.add("climate " + seed + " drawn at " + picture);
        return lines;
    }

    private static Warmth warmth(Holder<Biome> biome) {
        if (biome.is(ICY_BIOMES)) {
            return Warmth.ICY;
        }
        if (biome.is(COLD_BIOMES)) {
            return Warmth.COLD;
        }
        if (biome.is(NEUTRAL_BIOMES)) {
            return Warmth.NEUTRAL;
        }
        if (biome.is(WARM_BIOMES)) {
            return Warmth.WARM;
        }
        if (biome.is(HOT_BIOMES)) {
            return Warmth.HOT;
        }
        float temperature = biome.value().getBaseTemperature();
        return temperature < 0.15F ? Warmth.ICY : temperature < 0.45F ? Warmth.COLD : temperature < 0.85F ? Warmth.NEUTRAL
                : temperature < 1.0F ? Warmth.WARM : Warmth.HOT;
    }

    private static Warmth[] zones(Warmth[] felt, int size, int smallest) {
        Warmth[] zoned = felt.clone();
        boolean merged = true;
        while (merged) {
            merged = false;
            int[] region = new int[zoned.length];
            Arrays.fill(region, -1);
            for (int seed = 0; seed < zoned.length; seed++) {
                if (zoned[seed] == null || region[seed] >= 0) {
                    continue;
                }
                List<Integer> members = new ArrayList<>();
                Map<Warmth, Long> bordering = new TreeMap<>();
                Deque<Integer> open = new ArrayDeque<>();
                open.add(seed);
                region[seed] = seed;
                while (!open.isEmpty()) {
                    int index = open.poll();
                    members.add(index);
                    int column = index % size;
                    int row = index / size;
                    int[][] around = {{column + 1, row}, {column - 1, row}, {column, row + 1}, {column, row - 1}};
                    for (int[] next : around) {
                        if (next[0] < 0 || next[1] < 0 || next[0] >= size || next[1] >= size) {
                            continue;
                        }
                        int neighbour = next[1] * size + next[0];
                        if (zoned[neighbour] == null) {
                            continue;
                        }
                        if (zoned[neighbour] != zoned[seed]) {
                            bordering.merge(zoned[neighbour], 1L, Long::sum);
                        } else if (region[neighbour] < 0) {
                            region[neighbour] = seed;
                            open.add(neighbour);
                        }
                    }
                }
                if (members.size() < smallest && !bordering.isEmpty()) {
                    Warmth surrounding = Collections.max(bordering.entrySet(), Map.Entry.comparingByValue()).getKey();
                    members.forEach(index -> zoned[index] = surrounding);
                    merged = true;
                }
            }
        }
        return zoned;
    }

    private static TagKey<Biome> toughAsNails(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("toughasnails", path));
    }

    private static int nearestFelt(Warmth[] felt, int size, int from) {
        int best = from;
        long nearest = Long.MAX_VALUE;
        for (int index = 0; index < felt.length; index++) {
            if (felt[index] != null) {
                long dx = index % size - from % size;
                long dz = index / size - from / size;
                if (dx * dx + dz * dz < nearest) {
                    nearest = dx * dx + dz * dz;
                    best = index;
                }
            }
        }
        return best;
    }

    private static String reach(Warmth[] felt, int size, int step, boolean fromCold) {
        int neutral = Warmth.NEUTRAL.ordinal();
        double[] distance = new double[felt.length];
        for (int index = 0; index < felt.length; index++) {
            boolean target = felt[index] != null && (fromCold ? felt[index].ordinal() >= neutral : felt[index].ordinal() <= neutral);
            distance[index] = target ? 0 : Double.MAX_VALUE;
        }
        double diagonal = Math.sqrt(2);
        int[][] before = {{-1, -1, 1}, {0, -1, 0}, {1, -1, 1}, {-1, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int at = 0; at < felt.length; at++) {
                int index = pass == 0 ? at : felt.length - 1 - at;
                int column = index % size;
                int row = index / size;
                for (int[] offset : before) {
                    int dx = pass == 0 ? offset[0] : -offset[0];
                    int dz = pass == 0 ? offset[1] : -offset[1];
                    int x = column + dx;
                    int z = row + dz;
                    if (x < 0 || z < 0 || x >= size || z >= size || distance[z * size + x] == Double.MAX_VALUE) {
                        continue;
                    }
                    distance[index] = Math.min(distance[index], distance[z * size + x] + (offset[2] == 1 ? diagonal : 1));
                }
            }
        }
        List<Integer> found = new ArrayList<>();
        for (int index = 0; index < felt.length; index++) {
            boolean source = felt[index] != null && (fromCold ? felt[index].ordinal() < neutral : felt[index].ordinal() > neutral);
            if (source && distance[index] != Double.MAX_VALUE) {
                found.add((int) Math.round(distance[index] * step));
            }
        }
        if (found.isEmpty()) {
            return "nowhere, since there is none";
        }
        Collections.sort(found);
        long far = found.stream().filter(blocks -> blocks > FAR_FROM_CHANGE).count();
        return String.format(Locale.ROOT, "%d blocks away on average (median %d, over %d for %.0f%% of it)",
                Math.round(found.stream().mapToInt(Integer::intValue).average().orElse(0)), median(found), FAR_FROM_CHANGE, 100.0 * far / found.size());
    }

    private static int median(List<Integer> sorted) {
        return sorted.get(sorted.size() / 2);
    }

    private static BufferedImage drawClimate(byte[] cells, boolean[] home, Warmth[] felt, int size, int camp) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int index = 0; index < cells.length; index++) {
            int color = switch (cells[index]) {
                case DEEP -> ABYSS;
                case SEA -> OCEAN;
                case RIVER -> OCEAN;
                default -> felt[index] != null ? WARMTH[felt[index].ordinal()] : home[index] ? HOME : AWAY;
            };
            image.setRGB(index % size, index / size, color);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = camp % size + dx;
                int z = camp / size + dz;
                if (x >= 0 && z >= 0 && x < size && z < size) {
                    image.setRGB(x, z, ORIGIN);
                }
            }
        }
        return image;
    }

    private static int shape(CommandSourceStack source, long seed, int radius, int step, @Nullable Double lift, @Nullable Integer farShore) {
        ServerLevel level = source.getServer().overworld();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            source.sendFailure(Component.literal("shape failed: the overworld is not drawn from noise"));
            return 0;
        }
        RandomState state = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        boolean trial = lift != null || farShore != null;
        NoiseRouter router = trial ? state.router().mapAll(function -> function instanceof WorldShape shaped
                ? new WorldShape(shaped.input(), tried(shaped.layout(), lift, farShore)) : function) : state.router();
        List<WorldShape> shapes = new ArrayList<>();
        router.continents().mapAll(function -> {
            if (function instanceof WorldShape shaped) {
                shapes.add(shaped);
            }
            return function;
        });
        if (shapes.isEmpty()) {
            source.sendFailure(Component.literal("shape failed: the overworld's continents carry no world shape"));
            return 0;
        }
        WorldShape.Layout layout = shapes.getFirst().layout();
        WorldShape.Coasts coasts = shapes.getFirst().coasts();
        int ring = Balance.get().strongholds().radiusBlocks();
        int[] rings = {layout.home(), ring, layout.lastSeaSlope(), layout.lastSeaSlope() + layout.coast()};
        Climate.Sampler sampler = new Climate.Sampler(router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(),
                List.of());
        BiomeSource biomes = generator.getBiomeSource();
        int size = radius * 2 / step + 1;
        int centre = size / 2;
        byte[] cells = new byte[size * size];
        List<Holder<Biome>> found = new ArrayList<>(Collections.nCopies(size * size, null));
        for (int index = 0; index < cells.length; index++) {
            int x = -radius + index % size * step;
            int z = -radius + index / size * step;
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SURFACE), QuartPos.fromBlock(z), sampler);
            found.set(index, biome);
            cells[index] = biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(SKYLANDS) ? DEEP : biome.is(BiomeTags.IS_OCEAN) ? SEA : biome.is(BiomeTags.IS_RIVER) ? RIVER : LAND;
        }
        String named = trial ? String.format(Locale.ROOT, "shape %d (beyond lift %.3f, far shore %d)", seed, layout.beyondLift(), layout.farShore())
                : "shape " + seed;
        int start = nearestLand(cells, size, centre);
        boolean[] home = new boolean[size * size];
        long homeCells = start < 0 ? 0 : fill(cells, size, start, home);
        int farthest = 0;
        for (int index = 0; index < cells.length; index++) {
            if (home[index]) {
                farthest = Math.max(farthest, distance(index, size, centre, step));
            }
        }
        List<String> lines = new ArrayList<>();
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "%s: the Last Camp's land is %.0f km2, reaching %d blocks out", named,
                homeCells * (double) step * step / 1_000_000, farthest));
        int inner = 0;
        for (int band : rings) {
            long land = 0;
            long all = 0;
            for (int index = 0; index < cells.length; index++) {
                int at = distance(index, size, centre, step);
                if (at >= inner && at < band) {
                    all++;
                    land += cells[index] >= RIVER ? 1 : 0;
                }
            }
            report.append(String.format(Locale.ROOT, "; %d to %d: land %.0f%%", inner, band, 100.0 * land / Math.max(1, all)));
            inner = band;
        }
        lines.add(report.toString());
        int outerStart = nearestLandAt(cells, size, centre, step, ring);
        boolean[] outer = new boolean[size * size];
        long outerCells = outerStart < 0 || home[outerStart] ? 0 : fill(cells, size, outerStart, outer);
        lines.add(crossing(named, home, outer, size, centre, step, outerStart >= 0 && !home[outerStart]));
        lines.add(outerLand(named, cells, outer, outerCells, size, centre, step, coasts, ring));
        lines.add(lakes(named, cells, size, centre, step, coasts, layout));
        lines.add(islets(named, cells, home, outer, size, step));
        lines.add(lastSea(named, outer, size, centre, step));
        StringBuilder census = new StringBuilder(named).append(": halls' land beyond the sea:");
        Map<String, Long> counts = new TreeMap<>();
        long beyond = 0;
        for (int index = 0; index < cells.length; index++) {
            if (!outer[index] || cells[index] != LAND) {
                continue;
            }
            beyond++;
            for (String tag : HALL_LANDS) {
                if (found.get(index).is(TagKey.create(Registries.BIOME, ResourceLocation.parse(tag)))) {
                    counts.merge(tag, 1L, Long::sum);
                }
            }
        }
        long land = beyond;
        counts.forEach((tag, count) -> census.append(String.format(Locale.ROOT, " %s %.1f%%", tag.substring(tag.indexOf(':') + 1), 100.0 * count / Math.max(1, land))));
        lines.add(census.toString());
        if (!trial) {
            lines.add(floors(named, level, generator, state, coasts));
        }
        Path picture = FMLPaths.GAMEDIR.get().resolve("../shape").normalize().resolve((trial ? named.substring(6).replaceAll("[^0-9.]+", "-") : String.valueOf(seed)) + ".png");
        try {
            Files.createDirectories(picture.getParent());
            ImageIO.write(draw(cells, home, size, centre, step, rings), "png", picture.toFile());
        } catch (IOException exception) {
            source.sendFailure(Component.literal("shape failed: " + picture + " could not be written: " + exception.getMessage()));
            return 0;
        }
        lines.add(named + " drawn at " + picture);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static WorldShape.Layout tried(WorldShape.Layout layout, @Nullable Double lift, @Nullable Integer farShore) {
        return new WorldShape.Layout(lift == null ? layout.beyondLift() : lift, layout.coast(), farShore == null ? layout.farShore() : farShore, layout.highest(),
                layout.home(), layout.lastSeaSlope(), layout.lift(), layout.seaFloor(), layout.seaSink(), layout.shore());
    }

    private static int cell(int size, int centre, int step, double angle, int along) {
        int column = centre + (int) Math.round(Math.cos(angle) * along / step);
        int row = centre + (int) Math.round(Math.sin(angle) * along / step);
        return column < 0 || row < 0 || column >= size || row >= size ? -1 : row * size + column;
    }

    private static String crossing(String named, boolean[] home, boolean[] outer, int size, int centre, int step, boolean closed) {
        List<int[]> gaps = new ArrayList<>();
        for (int way = 0; way < CROSSING_WAYS; way++) {
            double angle = 2 * Math.PI * way / CROSSING_WAYS;
            int edge = -1;
            for (int along = 0; cell(size, centre, step, angle, along) >= 0; along += step) {
                if (home[cell(size, centre, step, angle, along)]) {
                    edge = along;
                }
            }
            if (edge < 0) {
                continue;
            }
            for (int along = edge + step; cell(size, centre, step, angle, along) >= 0; along += step) {
                int index = cell(size, centre, step, angle, along);
                if (outer[index]) {
                    gaps.add(new int[] {along - edge, way});
                    break;
                }
            }
        }
        if (gaps.isEmpty()) {
            return named + ": the crossing: no land beyond the sea";
        }
        gaps.sort((a, b) -> Integer.compare(a[0], b[0]));
        int[] narrowest = gaps.getFirst();
        return String.format(Locale.ROOT, "%s: the crossing: typical %d, narrowest %d (towards %d degrees), widest %d, over %d ways; %s", named,
                gaps.get(gaps.size() / 2)[0], narrowest[0], narrowest[1] * 360 / CROSSING_WAYS, gaps.getLast()[0], gaps.size(),
                closed ? "the sea closed all round" : "the sea is not closed: the home land runs on into the land beyond");
    }

    private static String outerLand(String named, byte[] cells, boolean[] outer, long outerCells, int size, int centre, int step, WorldShape.Coasts coasts, int ring) {
        long land = 0;
        long all = 0;
        boolean[] near = new boolean[WAYS];
        boolean[] far = new boolean[WAYS];
        for (int index = 0; index < cells.length; index++) {
            int x = index % size - centre;
            int z = index / size - centre;
            double angle = WorldShape.Coasts.angle(x, z);
            int at = distance(index, size, centre, step);
            if (at < coasts.far(angle) || at > coasts.last(angle)) {
                continue;
            }
            all++;
            land += cells[index] >= RIVER ? 1 : 0;
            if (outer[index]) {
                int way = (int) Math.floorMod(Math.round(angle / (2 * Math.PI) * WAYS), (long) WAYS);
                near[way] |= at <= coasts.far(angle) + OUTER_EDGE_BLOCKS;
                far[way] |= at >= Math.max(ring, coasts.last(angle) - OUTER_EDGE_BLOCKS);
            }
        }
        int through = 0;
        for (int way = 0; way < WAYS; way++) {
            through += near[way] && far[way] ? 1 : 0;
        }
        return String.format(Locale.ROOT, "%s: the outer continent: land %.0f%%, %.0f%% of it one piece, the far shore reaching the Last Sea's coast over land in %d of %d ways",
                named, 100.0 * land / Math.max(1, all), 100.0 * outerCells / Math.max(1, land), through, WAYS);
    }

    private static String lakes(String named, byte[] cells, int size, int centre, int step, WorldShape.Coasts coasts, WorldShape.Layout layout) {
        boolean[] open = new boolean[cells.length];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < cells.length; index++) {
            int column = index % size;
            int row = index / size;
            boolean border = column == 0 || row == 0 || column == size - 1 || row == size - 1;
            if (border && cells[index] <= DEEP) {
                open[index] = true;
                queue.add(index);
            }
        }
        for (int way = 0; way < CROSSING_WAYS; way++) {
            double angle = 2 * Math.PI * way / CROSSING_WAYS;
            int index = cell(size, centre, step, angle, (int) Math.round((coasts.home(angle) + coasts.far(angle)) / 2));
            if (index >= 0 && cells[index] <= DEEP && !open[index]) {
                open[index] = true;
                queue.add(index);
            }
        }
        flood(cells, size, open, queue, null);
        long homeLake = 0;
        long beyondLake = 0;
        boolean[] seen = open.clone();
        for (int index = 0; index < cells.length; index++) {
            if (seen[index] || cells[index] > DEEP) {
                continue;
            }
            seen[index] = true;
            List<Integer> members = new ArrayList<>(List.of(index));
            flood(cells, size, seen, new ArrayDeque<>(List.of(index)), members);
            boolean inHome = false;
            boolean inBeyond = false;
            for (int member : members) {
                int at = distance(member, size, centre, step);
                double angle = WorldShape.Coasts.angle(member % size - centre, member / size - centre);
                inHome |= at < layout.home() - layout.coast();
                inBeyond |= at > coasts.far(angle) + layout.shore() && at < layout.lastSeaSlope();
            }
            homeLake = inHome ? Math.max(homeLake, members.size()) : homeLake;
            beyondLake = inBeyond ? Math.max(beyondLake, members.size()) : beyondLake;
        }
        double cell = (double) step * step / 1_000_000;
        return String.format(Locale.ROOT, "%s: the largest sea water inland: on the outer continent %.2f km2, on the home land %.2f km2 (coasts' bays and lagoons not counted)",
                named, beyondLake * cell, homeLake * cell);
    }

    private static String islets(String named, byte[] cells, boolean[] home, boolean[] outer, int size, int step) {
        long islets = 0;
        for (int index = 0; index < cells.length; index++) {
            islets += cells[index] == LAND && !home[index] && !outer[index] ? 1 : 0;
        }
        return String.format(Locale.ROOT, "%s: islands in the seas: %.2f km2", named, islets * (double) step * step / 1_000_000);
    }

    private static long flood(byte[] cells, int size, boolean[] marked, Deque<Integer> queue, @Nullable List<Integer> members) {
        long count = 0;
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int column = index % size;
            int row = index / size;
            int[][] around = {{column + 1, row}, {column - 1, row}, {column, row + 1}, {column, row - 1}};
            for (int[] next : around) {
                if (next[0] < 0 || next[1] < 0 || next[0] >= size || next[1] >= size) {
                    continue;
                }
                int neighbour = next[1] * size + next[0];
                if (!marked[neighbour] && cells[neighbour] <= DEEP) {
                    marked[neighbour] = true;
                    queue.add(neighbour);
                    count++;
                    if (members != null) {
                        members.add(neighbour);
                    }
                }
            }
        }
        return count;
    }

    private static String lastSea(String named, boolean[] outer, int size, int centre, int step) {
        List<Integer> coast = new ArrayList<>();
        for (int way = 0; way < CROSSING_WAYS; way++) {
            double angle = 2 * Math.PI * way / CROSSING_WAYS;
            int edge = -1;
            for (int along = 0; cell(size, centre, step, angle, along) >= 0; along += step) {
                if (outer[cell(size, centre, step, angle, along)]) {
                    edge = along;
                }
            }
            if (edge >= 0) {
                coast.add(edge);
            }
        }
        if (coast.isEmpty()) {
            return named + ": the Last Sea's coast: no outer continent";
        }
        Collections.sort(coast);
        return String.format(Locale.ROOT, "%s: the Last Sea's coast %d to %d blocks out, typical %d", named, coast.getFirst(), coast.getLast(), median(coast));
    }

    private static String floors(String named, ServerLevel level, NoiseBasedChunkGenerator generator, RandomState state, WorldShape.Coasts coasts) {
        List<Integer> crossing = new ArrayList<>();
        List<Integer> last = new ArrayList<>();
        for (int way = 0; way < FLOOR_WAYS; way++) {
            double angle = 2 * Math.PI * way / FLOOR_WAYS;
            double middle = (coasts.home(angle) + coasts.far(angle)) / 2;
            double out = coasts.last(angle) + LAST_SEA_FLOOR_BLOCKS;
            crossing.add(generator.getBaseHeight((int) Math.round(Math.cos(angle) * middle), (int) Math.round(Math.sin(angle) * middle), Heightmap.Types.OCEAN_FLOOR_WG,
                    level, state));
            last.add(generator.getBaseHeight((int) Math.round(Math.cos(angle) * out), (int) Math.round(Math.sin(angle) * out), Heightmap.Types.OCEAN_FLOOR_WG, level,
                    state));
        }
        Collections.sort(crossing);
        Collections.sort(last);
        return String.format(Locale.ROOT, "%s: the floors: the crossing y %d to %d (typical %d), the Last Sea y %d to %d (typical %d)", named, crossing.getFirst(),
                crossing.getLast(), median(crossing), last.getFirst(), last.getLast(), median(last));
    }

    private static int nearestLandAt(byte[] cells, int size, int centre, int step, int ring) {
        for (int way = 0; way < CROSSING_WAYS; way++) {
            double angle = 2 * Math.PI * way / CROSSING_WAYS;
            int index = cell(size, centre, step, angle, ring);
            if (index >= 0 && cells[index] == LAND) {
                return index;
            }
        }
        return -1;
    }

    private static int nearestLand(byte[] cells, int size, int centre) {
        int best = -1;
        long nearest = Long.MAX_VALUE;
        for (int index = 0; index < cells.length; index++) {
            if (cells[index] == LAND) {
                long dx = index % size - centre;
                long dz = index / size - centre;
                if (dx * dx + dz * dz < nearest) {
                    nearest = dx * dx + dz * dz;
                    best = index;
                }
            }
        }
        return best;
    }

    private static long fill(byte[] cells, int size, int start, boolean[] home) {
        Deque<Integer> open = new ArrayDeque<>();
        open.add(start);
        home[start] = true;
        long count = 0;
        while (!open.isEmpty()) {
            int index = open.poll();
            count++;
            int column = index % size;
            int row = index / size;
            int[][] around = {{column + 1, row}, {column - 1, row}, {column, row + 1}, {column, row - 1}};
            for (int[] next : around) {
                if (next[0] < 0 || next[1] < 0 || next[0] >= size || next[1] >= size) {
                    continue;
                }
                int neighbour = next[1] * size + next[0];
                if (!home[neighbour] && cells[neighbour] >= RIVER) {
                    home[neighbour] = true;
                    open.add(neighbour);
                }
            }
        }
        return count;
    }

    private static int distance(int index, int size, int centre, int step) {
        long dx = (long) (index % size - centre) * step;
        long dz = (long) (index / size - centre) * step;
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static BufferedImage draw(byte[] cells, boolean[] home, int size, int centre, int step, int[] rings) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int index = 0; index < cells.length; index++) {
            int color = switch (cells[index]) {
                case DEEP -> ABYSS;
                case SEA -> OCEAN;
                case RIVER -> STREAM;
                default -> home[index] ? HOME : AWAY;
            };
            image.setRGB(index % size, index / size, color);
        }
        for (int ring : rings) {
            double scaled = ring / (double) step;
            for (int angle = 0; angle < 3600; angle++) {
                int x = centre + (int) Math.round(Math.cos(Math.toRadians(angle / 10.0)) * scaled);
                int z = centre + (int) Math.round(Math.sin(Math.toRadians(angle / 10.0)) * scaled);
                if (x >= 0 && z >= 0 && x < size && z < size) {
                    image.setRGB(x, z, RING);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                image.setRGB(centre + dx, centre + dz, ORIGIN);
            }
        }
        return image;
    }
}
