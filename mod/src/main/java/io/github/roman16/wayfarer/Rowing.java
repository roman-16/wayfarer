package io.github.roman16.wayfarer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Rowing {
    private Rowing() {}

    public static boolean afloat(Entity vehicle) {
        return vehicle.level().getFluidState(vehicle.blockPosition()).is(FluidTags.WATER)
                || vehicle.level().getFluidState(vehicle.blockPosition().below()).is(FluidTags.WATER);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != player || !afloat(vehicle)) {
            return;
        }
        Vec3 motion = vehicle.getDeltaMovement();
        double blocks = Math.hypot(motion.x, motion.z);
        if (blocks > 0) {
            player.causeFoodExhaustion((float) (blocks * Balance.get().hunger().rowingExhaustionPerBlock()));
        }
    }
}
