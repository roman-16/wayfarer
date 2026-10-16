package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.HudState;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;

final class Smoke {
    private static final String FAR_VIEW = "distanthorizons";
    private static final double DRIFT = 0.02;
    private static final double RISE = 0.07;
    private static final double SPREAD = 0.25;
    private static final double LEAN = 0.32;
    private static final double SMALLEST = 2.5;
    private static final int FILL_PER_BLOCK = 2;
    private static BlockPos filled;

    private Smoke() {}

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Optional<BlockPos> fire = level == null ? Optional.empty() : HudState.smoke;
        boolean near = fire.isPresent() && level.isLoaded(fire.get());
        if (!near) {
            filled = null;
        } else if (!minecraft.isPaused()) {
            Balance.Smoke smoke = Balance.get().smoke();
            RandomSource random = level.getRandom();
            BlockPos at = fire.get();
            if (!at.equals(filled)) {
                filled = at;
                for (int puff = 0; puff < (smoke.columnBlocks() - smoke.aloftFromBlocks()) * FILL_PER_BLOCK; puff++) {
                    aloft(level, random, at, smoke);
                }
            }
            if (level.getGameTime() % smoke.puffTicks() == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.getX() + 0.5 + random.triangle(0, SPREAD), at.getY() + 1,
                        at.getZ() + 0.5 + random.triangle(0, SPREAD), random.triangle(0, DRIFT), RISE, random.triangle(0, DRIFT));
                for (int puff = 0; puff < smoke.aloftPuffs(); puff++) {
                    aloft(level, random, at, smoke);
                }
            }
        }
        if (ModList.get().isLoaded(FAR_VIEW)) {
            FarSmoke.show(fire, !near);
        }
    }

    static double lean(double rise, Balance.Smoke smoke) {
        return rise * rise * smoke.columnBlocks() * LEAN;
    }

    static double size(double rise, Balance.Smoke smoke) {
        return SMALLEST + rise * (smoke.widthBlocks() * 2 - SMALLEST);
    }

    private static void aloft(ClientLevel level, RandomSource random, BlockPos fire, Balance.Smoke smoke) {
        double height = smoke.aloftFromBlocks() + random.nextDouble() * (smoke.columnBlocks() - smoke.aloftFromBlocks());
        double rise = height / smoke.columnBlocks();
        double spread = size(rise, smoke) / 2;
        double lean = lean(rise, smoke);
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, fire.getX() + 0.5 + lean + random.triangle(0, spread), fire.getY() + 1 + height,
                fire.getZ() + 0.5 + lean + random.triangle(0, spread), random.triangle(0, DRIFT), RISE, random.triangle(0, DRIFT));
    }
}
