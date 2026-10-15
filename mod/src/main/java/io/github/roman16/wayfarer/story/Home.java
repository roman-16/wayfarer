package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import java.util.function.BiPredicate;
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

    static long degrees(Vec3 from, Vec3 to) {
        return Math.round(Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, from.dot(to))))));
    }

    static Aim aim(BlockPos from, Vec3 way, int ahead) {
        return aim(from, way, ahead, (origin, bent) -> true);
    }

    static Aim aim(BlockPos from, Vec3 way, int ahead, BiPredicate<BlockPos, Vec3> fits) {
        int reach = reach();
        Vec3 start = Vec3.atLowerCornerOf(from).multiply(1, 0, 1);
        double out = start.length();
        if (out > reach) {
            start = start.scale(Math.max(0, reach - ahead) / out);
        }
        BlockPos origin = BlockPos.containing(start.x, from.getY(), start.z);
        Vec3 home = null;
        for (int turned = 0; turned <= HALF_TURN_DEGREES; turned += TURN_STEP_DEGREES) {
            for (int sign : turned == 0 ? new int[] {1} : new int[] {1, -1}) {
                Vec3 bent = way.yRot((float) Math.toRadians(turned * sign));
                if (start.add(bent.scale(ahead)).length() <= reach) {
                    if (fits.test(origin, bent)) {
                        return new Aim(origin, bent);
                    }
                    home = home == null ? bent : home;
                }
            }
        }
        return new Aim(origin, home != null ? home : start.lengthSqr() == 0 ? way : start.scale(-1).normalize());
    }
}
