package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class FarSpike {
    private static final int GAMEMASTER = 2;
    private static final int REGIONS = 32;
    private static final Map<String, Site> VANILLA = Map.of(
            "desert_pyramid", new Site(14357617, Set.of(Biomes.DESERT)),
            "igloo", new Site(14357618, Set.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_SLOPES, Biomes.SNOWY_TAIGA)),
            "jungle_pyramid", new Site(14357619, Set.of(Biomes.BAMBOO_JUNGLE, Biomes.JUNGLE)),
            "swamp_hut", new Site(14357620, Set.of(Biomes.SWAMP)));
    private static final int SPACING = 32;
    private static final int SEPARATION = 8;

    private FarSpike() {}

    private record Site(int salt, Set<ResourceKey<Biome>> biomes) {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("far")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("phantom").then(Commands.argument("structure", ResourceLocationArgument.id())
                                .executes(context -> phantom(context.getSource(), ResourceLocationArgument.getId(context, "structure")))))));
    }

    private static int phantom(CommandSourceStack source, ResourceLocation structure) {
        Site site = VANILLA.get(structure.getPath());
        if (site == null) {
            source.sendFailure(Component.literal("far failed: SeedGen draws its own copies only of " + VANILLA.keySet()));
            return 0;
        }
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        long seed = level.getSeed();
        int fromX = (int) source.getPosition().x;
        int fromZ = (int) source.getPosition().z;
        int regionX = Math.floorDiv(fromX >> 4, SPACING);
        int regionZ = Math.floorDiv(fromZ >> 4, SPACING);
        int bestX = 0;
        int bestZ = 0;
        long best = Long.MAX_VALUE;
        for (int dz = -REGIONS; dz <= REGIONS; dz++) {
            for (int dx = -REGIONS; dx <= REGIONS; dx++) {
                long[] state = {((regionX + dx) * 341873128712L + (regionZ + dz) * 132897987541L + seed + site.salt() ^ 25214903917L) & 281474976710655L};
                int x = (((regionX + dx) * SPACING + nextInt(state, SPACING - SEPARATION)) << 4) + 8;
                int z = (((regionZ + dz) * SPACING + nextInt(state, SPACING - SEPARATION)) << 4) + 8;
                long distance = (long) (x - fromX) * (x - fromX) + (long) (z - fromZ) * (z - fromZ);
                if (distance >= best) {
                    continue;
                }
                int y = Math.max(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()), generator.getSeaLevel());
                Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                        level.getChunkSource().randomState().sampler());
                if (site.biomes().stream().anyMatch(biome::is)) {
                    best = distance;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        if (best == Long.MAX_VALUE) {
            source.sendFailure(Component.literal("far failed: SeedGen draws no %s within %d blocks".formatted(structure, REGIONS * SPACING * 16)));
            return 0;
        }
        int x = bestX;
        int z = bestZ;
        double away = Math.sqrt(best);
        source.sendSuccess(() -> Component.literal("far: SeedGen draws a %s of its own at %d %d, %.0f blocks away".formatted(structure, x, z, away)), false);
        return 1;
    }

    private static int nextInt(long[] state, int bound) {
        int mask = bound - 1;
        if ((bound & mask) == 0) {
            return (int) ((long) bound * next31(state) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next31(state);
            value = bits % bound;
        } while (bits - value + mask < 0);
        return value;
    }

    private static int next31(long[] state) {
        state[0] = state[0] * 25214903917L + 11L & 281474976710655L;
        return (int) (state[0] >>> 17);
    }
}
