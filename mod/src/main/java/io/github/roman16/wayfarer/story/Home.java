package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class Home {
    private static final int TURN_STEP_DEGREES = 15;
    private static final int HALF_TURN_DEGREES = 180;

    private Home() {}

    record Aim(BlockPos from, Vec3 way) {}

    static int reach() {
        Balance.World world = Balance.get().world();
        return world.homeBlocks() - world.coastBlocks() / 2;
    }

    static Aim aim(BlockPos from, Vec3 way, int ahead) {
        int reach = reach();
        Vec3 start = Vec3.atLowerCornerOf(from).multiply(1, 0, 1);
        double out = start.length();
        if (out > reach) {
            start = start.scale(Math.max(0, reach - ahead) / out);
        }
        BlockPos origin = BlockPos.containing(start.x, from.getY(), start.z);
        for (int turned = 0; turned <= HALF_TURN_DEGREES; turned += TURN_STEP_DEGREES) {
            for (int sign : new int[] {1, -1}) {
                Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                if (start.add(bent.scale(ahead)).length() <= reach) {
                    return new Aim(origin, bent);
                }
            }
        }
        return new Aim(origin, start.lengthSqr() == 0 ? way : start.scale(-1).normalize());
    }
}
