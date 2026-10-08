package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Talk;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Daylight {
    private static final String GAZE_CRUMBLES = "gaze_crumbles";
    private static final String GAZE_RISES = "gaze_rises";
    private static final Set<MobSpawnType> RISING = Set.of(MobSpawnType.EVENT, MobSpawnType.NATURAL);
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
    public static void onSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && Nights.night(level)
                && RISING.contains(event.getSpawnType()) && Fire.fears(mob) && level.canSeeSky(BlockPos.containing(event.getX(), event.getY(), event.getZ()))) {
            Sounds.play(level, new Vec3(event.getX(), event.getY(), event.getZ()), GAZE_RISES);
        }
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

    public static void crumble(ServerLevel level, Mob mob) {
        level.sendParticles(ParticleTypes.WHITE_ASH, mob.getX(), mob.getY(0.5), mob.getZ(), 40, mob.getBbWidth() / 2, mob.getBbHeight() / 2, mob.getBbWidth() / 2, 0.02);
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY(0.5), mob.getZ(), 8, mob.getBbWidth() / 3, mob.getBbHeight() / 3, mob.getBbWidth() / 3, 0.02);
        Sounds.play(level, mob.position(), GAZE_CRUMBLES);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) < WITNESS_BLOCKS * WITNESS_BLOCKS && Firsts.first(player, "dawn")) {
                Talk.tell(player, Component.translatable("wayfarer.first.dawn"));
            }
        }
        mob.discard();
    }
}
