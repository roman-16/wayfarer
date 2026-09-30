package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
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
    private static final String DAY = "day";
    private static final String STAGE = "stage";
    private static final int CHECK_TICKS = 20;

    private Stillness() {}

    public static int stage(long days) {
        Balance.Stillness balance = Balance.INSTANCE.stillness();
        return days >= balance.hordeDays() ? FOUND : days >= balance.dimmingDays() ? DIMMING : MOVING;
    }

    public static boolean found(ServerPlayer player) {
        return !Journey.slain(player) && player.getData(Wayfarer.STILLNESS).getInt(STAGE) == FOUND;
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
        long today = Story.day(player.server);
        int before = anchor.getInt(STAGE);
        if (Journey.slain(player)) {
            if (before != MOVING) {
                player.setData(Wayfarer.STILLNESS, new CompoundTag());
            }
            return;
        }
        double away = Math.hypot(player.getX() - anchor.getInt(X), player.getZ() - anchor.getInt(Z));
        if (!anchor.contains(DAY) || away > Balance.INSTANCE.stillness().radiusBlocks()) {
            CompoundTag next = new CompoundTag();
            next.putInt(X, player.getBlockX());
            next.putInt(Z, player.getBlockZ());
            next.putLong(DAY, today);
            next.putInt(STAGE, MOVING);
            player.setData(Wayfarer.STILLNESS, next);
            if (before != MOVING) {
                player.displayClientMessage(Component.translatable("wayfarer.stillness.brightens").withStyle(ChatFormatting.GOLD), false);
                Nights.sync(player);
            }
            return;
        }
        int stage = stage(today - anchor.getLong(DAY));
        if (stage <= before) {
            return;
        }
        CompoundTag next = anchor.copy();
        next.putInt(STAGE, stage);
        player.setData(Wayfarer.STILLNESS, next);
        if (before < DIMMING) {
            player.displayClientMessage(Component.translatable("wayfarer.stillness.dimming").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            Notes.first(player, "dimming");
            if (Story.voiceStarted(player)) {
                Story.scene(player, "dimming", Teams.onlineMembers(player), () -> {});
            }
        }
        if (stage == FOUND) {
            player.displayClientMessage(Component.translatable("wayfarer.stillness.found").withStyle(ChatFormatting.RED), false);
            Nights.sync(player);
        }
    }
}
