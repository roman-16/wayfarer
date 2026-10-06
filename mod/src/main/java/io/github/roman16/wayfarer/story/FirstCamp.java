package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class FirstCamp {
    static final String NAME = "first_camp";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] WAYS = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
    private static final int SAMPLE = 32;
    private static final int SHORE = 16;

    private FirstCamp() {}

    static void find(MinecraftServer server) {
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        BlockPos from = progress.place(Caravan.NAME).orElseGet(level::getSharedSpawnPos);
        Vec3 way = Caravan.way(server);
        Balance.FirstCamp balance = Balance.INSTANCE.firstCamp();
        int aheadX = from.getX() + (int) Math.round(way.x * balance.aheadBlocks());
        int aheadZ = from.getZ() + (int) Math.round(way.z * balance.aheadBlocks());
        Unseen.Heights heights = new Unseen.Heights(level);
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (int dx = -balance.lakeBlocks(); dx <= balance.lakeBlocks(); dx += SAMPLE) {
            for (int dz = -balance.lakeBlocks(); dz <= balance.lakeBlocks(); dz += SAMPLE) {
                int x = aheadX + dx;
                int z = aheadZ + dz;
                double distance = (double) dx * dx + dz * dz;
                if (distance >= nearest || !heights.wet(x, z) || heights.biome(x, heights.surface(x, z), z).is(BiomeTags.IS_OCEAN)
                        || heights.biome(x, heights.surface(x, z), z).is(BiomeTags.IS_RIVER)) {
                    continue;
                }
                BlockPos shore = shore(heights, x, z);
                if (shore != null) {
                    best = shore;
                    nearest = distance;
                }
            }
        }
        BlockPos spot = best != null ? best : new BlockPos(aheadX, heights.surface(aheadX, aheadZ), aheadZ);
        progress.place(NAME, spot);
        LOGGER.info("his first camp lies at {}{}", spot, best != null ? ", by a lake" : ", with no lake near");
    }

    static String direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        return WAYS[Math.floorMod((int) Math.round(angle / 45), WAYS.length)];
    }

    private static BlockPos shore(Unseen.Heights heights, int x, int z) {
        for (int reach = SHORE; reach <= SAMPLE * 2; reach += SHORE) {
            for (int step = 0; step < WAYS.length; step++) {
                double angle = Math.PI * 2 * step / WAYS.length;
                int sx = x + (int) Math.round(Math.cos(angle) * reach);
                int sz = z + (int) Math.round(Math.sin(angle) * reach);
                if (!heights.wet(sx, sz)) {
                    return new BlockPos(sx, heights.surface(sx, sz), sz);
                }
            }
        }
        return null;
    }
}
