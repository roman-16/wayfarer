package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Daylight {
    private static final int CHECK_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final double LEFT_AFTER_CRUMBLE_TIME = 0.05;
    private static final int WITNESS_BLOCKS = 32;

    private Daylight() {}

    public static double crumbleChance(int checkSeconds, int crumbleSeconds) {
        return 1 - Math.pow(LEFT_AFTER_CRUMBLE_TIME, (double) checkSeconds / crumbleSeconds);
    }

    public static boolean exposed(Mob mob) {
        return Fire.fears(mob) && !mob.hasCustomName() && mob.level().canSeeSky(mob.blockPosition());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_TICKS != 0
                || Nights.night(level)) {
            return;
        }
        double chance = crumbleChance(CHECK_TICKS / TICKS_PER_SECOND, Balance.INSTANCE.daylight().crumbleSeconds());
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), Daylight::exposed)) {
            if (level.random.nextDouble() < chance) {
                crumble(level, mob);
            }
        }
    }

    private static void crumble(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.WHITE_ASH, mob.getX(), mob.getY(0.5), mob.getZ(), 40, mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY(0.5), mob.getZ(), 8, mob.getBbWidth() / 3, mob.getBbHeight() / 3, mob.getBbWidth() / 3, 0.02);
        level.playSound(null, mob.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) < WITNESS_BLOCKS * WITNESS_BLOCKS && Firsts.first(player, "dawn")) {
                player.sendSystemMessage(Component.translatable("wayfarer.first.dawn").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        mob.discard();
    }
}
