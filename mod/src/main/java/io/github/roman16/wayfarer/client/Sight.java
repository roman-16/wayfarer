package io.github.roman16.wayfarer.client;

import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.HudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

public final class Sight {
    private static final int STILL_TICKS = 20;
    private static final double STILL_BLOCKS = 0.05;
    private static Vec3 last = Vec3.ZERO;
    private static int still;

    private Sight() {}

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            still = 0;
            return;
        }
        still = player.position().distanceTo(last) < STILL_BLOCKS ? still + 1 : 0;
        last = player.position();
    }

    public static boolean shows(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !HudState.compass.sight() || still < STILL_TICKS || !player.level().isNight() || !(entity instanceof Enemy) || entity instanceof EasyNPC<?>) {
            return false;
        }
        int reach = Balance.get().sight().reachBlocks();
        return entity.distanceToSqr(player) <= (double) reach * reach;
    }
}
