package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.People;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Leaving {
    private static final int AWAY_BLOCKS = 48;
    private static final int CHECK_TICKS = 20;
    private static final double SPEED = 1.0;
    private static final String TAG = Wayfarer.MODID + "_leaving";

    private Leaving() {}

    public static void send(Mob mob, Vec3 from) {
        mob.addTag(TAG);
        mob.setTarget(null);
        away(mob, from);
    }

    public static void sendAll(ServerLevel level, String tag, Vec3 from, int blocks) {
        level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.isAlive() && mob.getTags().contains(tag) && mob.position().closerThan(from, blocks))
                .forEach(mob -> send(mob, from));
    }

    public static boolean leaving(Entity entity) {
        return entity.getTags().contains(TAG);
    }

    private static void away(Mob mob, Vec3 from) {
        Vec3 away = mob.position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = Vec3.directionFromRotation(0, mob.getYRot());
        }
        Vec3 goal = mob.position().add(away.normalize().scale(AWAY_BLOCKS));
        mob.getNavigation().moveTo(goal.x, mob.getY(), goal.z, SPEED);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (leaving(event.getEntity()) && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && leaving(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), Leaving::leaving)) {
                if (!People.seen(level, mob.position())) {
                    mob.discard();
                    continue;
                }
                mob.setTarget(null);
                if (mob.getNavigation().isDone()) {
                    Player nearest = level.getNearestPlayer(mob, -1);
                    away(mob, nearest != null ? nearest.position() : mob.position());
                }
            }
        }
    }
}
