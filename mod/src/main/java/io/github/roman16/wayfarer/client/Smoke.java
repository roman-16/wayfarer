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

    private Smoke() {}

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Optional<BlockPos> fire = level == null ? Optional.empty() : HudState.smoke;
        boolean near = fire.isPresent() && level.isLoaded(fire.get());
        if (near && !minecraft.isPaused() && level.getGameTime() % Balance.get().smoke().puffTicks() == 0) {
            RandomSource random = level.getRandom();
            BlockPos at = fire.get();
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.getX() + 0.5 + random.triangle(0, SPREAD), at.getY() + 1,
                    at.getZ() + 0.5 + random.triangle(0, SPREAD), random.triangle(0, DRIFT), RISE, random.triangle(0, DRIFT));
        }
        if (ModList.get().isLoaded(FAR_VIEW)) {
            FarSmoke.show(fire, !near);
        }
    }
}
