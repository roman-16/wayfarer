package io.github.roman16.wayfarer;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class Gliding {
    private static final Map<Player, Flight> FLIGHTS = new WeakHashMap<>();

    private Gliding() {}

    private static final class Flight {
        private double takeoff;
        private boolean gliding;
        private boolean aloft;
        private int asked;
    }

    public static boolean allowed(Player player, boolean glider) {
        Flight flight = FLIGHTS.computeIfAbsent(player, key -> new Flight());
        flight.asked = player.tickCount;
        flight.aloft = false;
        if (player.onGround() || player.isInWater() || player.isPassenger()) {
            flight.takeoff = player.getY();
            flight.gliding = false;
            return glider;
        }
        if (!glider) {
            return false;
        }
        if (!flight.gliding) {
            flight.gliding = dropped(player, flight.takeoff);
        }
        flight.aloft = flight.gliding;
        return flight.gliding;
    }

    public static boolean gliding(Player player) {
        Flight flight = FLIGHTS.get(player);
        return flight != null && flight.aloft && player.tickCount - flight.asked <= 1;
    }

    private static boolean dropped(Player player, double takeoff) {
        int lowest = Mth.floor(takeoff - Balance.get().glider().dropBlocks());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(player.getBlockX(), Mth.floor(player.getY()), player.getBlockZ());
        for (; at.getY() >= lowest; at.move(0, -1, 0)) {
            BlockState state = player.level().getBlockState(at);
            if (!state.getCollisionShape(player.level(), at).isEmpty() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
