package io.github.roman16.wayfarer.spike;

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
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
                                                        IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "step"))))))));
    }

    private static int shape(CommandSourceStack source, long seed, int radius, int step) {
        WorldShape shape = WorldShape.around(DensityFunctions.zero());
        int far = Balance.get().halls().actThreeFromBlocks();
        int[] rings = {shape.home(), shape.home() + shape.sea(), far, Balance.get().strongholds().radiusBlocks(), shape.lastSea()};
        ServerLevel level = source.getServer().overworld();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            source.sendFailure(Component.literal("shape failed: the overworld is not drawn from noise"));
            return 0;
        }
        RandomState state = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        BiomeSource biomes = generator.getBiomeSource();
        int size = radius * 2 / step + 1;
        byte[] cells = new byte[size * size];
        List<Holder<Biome>> found = new ArrayList<>(Collections.nCopies(size * size, null));
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int x = -radius + column * step;
                int z = -radius + row * step;
                Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SURFACE), QuartPos.fromBlock(z), state.sampler());
                found.set(row * size + column, biome);
                cells[row * size + column] = biome.is(BiomeTags.IS_DEEP_OCEAN) ? DEEP : biome.is(BiomeTags.IS_OCEAN) ? SEA : biome.is(BiomeTags.IS_RIVER) ? RIVER : LAND;
            }
        }
        int centre = size / 2;
        for (int index = 0; index < cells.length; index++) {
            if (cells[index] == LAND && distance(index, size, centre, step) > shape.lastSea()) {
                int x = -radius + index % size * step;
                int z = -radius + index / size * step;
                Climate.TargetPoint point = state.sampler().sample(QuartPos.fromBlock(x), QuartPos.fromBlock(SURFACE), QuartPos.fromBlock(z));
                String biome = found.get(index).unwrapKey().map(key -> key.location().toString()).orElse("?");
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "land past the Last Sea at %d %d: %s, continentalness %.3f, erosion %.3f, weirdness %.3f",
                        x, z, biome, Climate.unquantizeCoord(point.continentalness()), Climate.unquantizeCoord(point.erosion()),
                        Climate.unquantizeCoord(point.weirdness()))), false);
                break;
            }
        }
        int start = nearestLand(cells, size, centre);
        boolean[] home = new boolean[size * size];
        long homeCells = start < 0 ? 0 : fill(cells, size, start, home);
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "shape %d: the Last Camp's land is %.0f km2, the land %s blocks from the origin",
                seed, homeCells * (double) step * step / 1_000_000, start < 0 ? "none" : String.valueOf(distance(start, size, centre, step))));
        int farthest = 0;
        for (int index = 0; index < cells.length; index++) {
            if (home[index]) {
                farthest = Math.max(farthest, distance(index, size, centre, step));
            }
        }
        report.append(", reaching ").append(farthest).append(" blocks out");
        int inner = 0;
        for (int ring : rings) {
            if (ring > radius) {
                break;
            }
            long land = 0;
            long ownLand = 0;
            long all = 0;
            for (int index = 0; index < cells.length; index++) {
                int at = distance(index, size, centre, step);
                if (at >= inner && at < ring) {
                    all++;
                    if (cells[index] >= RIVER) {
                        land++;
                    }
                    if (home[index]) {
                        ownLand++;
                    }
                }
            }
            report.append(String.format(Locale.ROOT, "; %d to %d: land %.0f%%, the camp's own %.0f%%", inner, ring, 100.0 * land / all, 100.0 * ownLand / all));
            inner = ring;
        }
        source.sendSuccess(() -> Component.literal(report.toString()), false);
        for (int[] band : new int[][] {{0, shape.home()}, {shape.home() + shape.sea(), far}, {far, shape.lastSea()}}) {
                StringBuilder census = new StringBuilder("halls' land ").append(band[0]).append(" to ").append(band[1]).append(":");
                long land = 0;
                Map<String, Long> counts = new TreeMap<>();
                for (int index = 0; index < cells.length; index++) {
                    int at = distance(index, size, centre, step);
                    boolean inside = band[0] == 0 ? home[index] : at >= band[0] && at < band[1] && cells[index] == LAND;
                    if (!inside || found.get(index) == null) {
                        continue;
                    }
                    land++;
                    for (String tag : HALL_LANDS) {
                        if (found.get(index).is(TagKey.create(Registries.BIOME, ResourceLocation.parse(tag)))) {
                            counts.merge(tag, 1L, Long::sum);
                        }
                    }
                }
                long all = land;
                counts.forEach((tag, count) -> census.append(String.format(Locale.ROOT, " %s %.1f%%", tag.substring(tag.indexOf(':') + 1), 100.0 * count / Math.max(1, all))));
                source.sendSuccess(() -> Component.literal(census.toString()), false);
        }
        Path picture = FMLPaths.GAMEDIR.get().resolve("../shape").normalize()
                .resolve(seed + ".png");
        try {
            Files.createDirectories(picture.getParent());
            ImageIO.write(draw(cells, home, size, centre, step, rings), "png", picture.toFile());
        } catch (IOException exception) {
            source.sendFailure(Component.literal("shape failed: " + picture + " could not be written: " + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("shape " + seed + " drawn at " + picture), false);
        return 1;
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
