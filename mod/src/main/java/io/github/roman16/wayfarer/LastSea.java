package io.github.roman16.wayfarer;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LastSea {
    public static final int FREEZING = Integer.MAX_VALUE;
    private static final int TOLD_TICKS = 20;
    private static final int TOLD_STEP_BLOCKS = 8;
    private static final int FREEZE_TICKS = 40;
    private static final int FROZEN_ABOVE = 2;
    private static final Map<UUID, Integer> TOLD = new HashMap<>();

    private LastSea() {}

    public static OptionalDouble past(Level level, double x, double z) {
        double distance = Math.sqrt(x * x + z * z);
        if (!(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD || distance < WorldShape.Layout.of(Balance.get()).lastSeaSlope()) {
            return OptionalDouble.empty();
        }
        return WorldShape.coasts(server).map(coasts -> distance - coasts.last(WorldShape.Coasts.angle(x, z))).filter(past -> past > 0)
                .map(OptionalDouble::of).orElseGet(OptionalDouble::empty);
    }

    public static int colder(Level level, double x, double z) {
        OptionalDouble past = past(level, x, z);
        if (past.isEmpty()) {
            return 0;
        }
        int freezing = Balance.get().lastSea().freezingBlocks();
        return past.getAsDouble() >= freezing ? FREEZING : past.getAsDouble() >= freezing / 2.0 ? 2 : 1;
    }

    public static boolean freezing(Player player) {
        return colder(player.level(), player.getX(), player.getZ()) == FREEZING;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % TOLD_TICKS == 0) {
            tell(player);
        }
        if (player.isCreative() || player.isSpectator() || !freezing(player)) {
            return;
        }
        player.setTicksFrozen(Math.max(player.getTicksFrozen(), player.getTicksRequiredToFreeze() + FROZEN_ABOVE));
        if (!player.canFreeze() && player.tickCount % FREEZE_TICKS == 0) {
            player.hurt(player.damageSources().freeze(), 1);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD.remove(event.getEntity().getUUID());
    }

    private static void tell(ServerPlayer player) {
        int past = (int) Math.round(past(player.level(), player.getX(), player.getZ()).orElse(0));
        Integer told = TOLD.get(player.getUUID());
        if (told != null && (told == past || past > 0 && Math.abs(told - past) < TOLD_STEP_BLOCKS)) {
            return;
        }
        TOLD.put(player.getUUID(), past);
        PacketDistributor.sendToPlayer(player, past == 0 ? LastSeaPayload.ASHORE : new LastSeaPayload(past));
    }
}
