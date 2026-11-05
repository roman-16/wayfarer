package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Hold;
import io.github.roman16.wayfarer.story.Talk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Stillness {
    public static final int MOVING = 0;
    public static final int DIMMING = 1;
    public static final int FOUND = 2;

    private static final String X = "x";
    private static final String Z = "z";
    private static final String SINCE = "since";
    private static final String STAGE = "stage";
    private static final int CHECK_TICKS = 20;
    private static final long TICKS_PER_DAY = 24000;

    private Stillness() {}

    public static int stage(long days) {
        Balance.Stillness balance = Balance.get().stillness();
        return days >= balance.hordeDays() ? FOUND : days >= balance.dimmingDays() ? DIMMING : MOVING;
    }

    public static boolean found(ServerPlayer player) {
        return !Journey.over(player.server) && player.getData(Wayfarer.STILLNESS).getInt(STAGE) == FOUND;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % CHECK_TICKS != 0
                || player.level().dimension() != Level.OVERWORLD
                || player.isSpectator()) {
            return;
        }
        CompoundTag anchor = player.getData(Wayfarer.STILLNESS);
        long now = player.server.overworld().getDayTime();
        int before = anchor.getInt(STAGE);
        if (Journey.over(player.server)) {
            if (before != MOVING) {
                player.setData(Wayfarer.STILLNESS, new CompoundTag());
            }
            return;
        }
        double away = Math.hypot(player.getX() - anchor.getInt(X), player.getZ() - anchor.getInt(Z));
        if (!anchor.contains(SINCE) || away > Balance.get().stillness().radiusBlocks() || Caravan.within(player.server, player.level(), player.position())
                || Hold.active(player.server) || Engine.sentNear(player, Balance.get().stillness().radiusBlocks())) {
            CompoundTag next = new CompoundTag();
            next.putInt(X, player.getBlockX());
            next.putInt(Z, player.getBlockZ());
            next.putLong(SINCE, now);
            next.putInt(STAGE, MOVING);
            player.setData(Wayfarer.STILLNESS, next);
            if (before != MOVING) {
                Nights.sync(player);
            }
            return;
        }
        int stage = stage((now - anchor.getLong(SINCE)) / TICKS_PER_DAY);
        if (stage <= before) {
            return;
        }
        CompoundTag next = anchor.copy();
        next.putInt(STAGE, stage);
        player.setData(Wayfarer.STILLNESS, next);
        if (before < DIMMING) {
            Talk.tell(player, Component.translatable("wayfarer.stillness.dimming"));
            Firsts.first(player, "dimming");
        }
        if (stage == FOUND) {
            Talk.tell(player, Component.translatable("wayfarer.stillness.found"));
            Nights.sync(player);
        }
    }
}
