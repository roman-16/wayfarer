package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Progress;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Stare {
    private static final String GIFT = "stare";
    private static final TagKey<EntityType<?>> STARED_DOWN = TagKey.create(Registries.ENTITY_TYPE, Wayfarer.id("stared_down"));
    private static final int CHECK_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final double RUN = 1.3;
    private static final Map<UUID, Flight> fleeing = new HashMap<>();

    private record Flight(long until, Vec3 goal) {}

    private Stare() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Progress.of(server).gift(GIFT)) {
            return;
        }
        Balance.Stare stare = Balance.get().stare();
        double cone = Math.cos(Math.toRadians(stare.coneDegrees()));
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        fleeing.values().removeIf(flight -> flight.until() < now);
        for (ServerPlayer player : level.players()) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(stare.reachBlocks()),
                    mob -> mob.isAlive() && mob.getType().is(STARED_DOWN) && !fleeing.containsKey(mob.getUUID()))) {
                Vec3 toward = mob.getEyePosition().subtract(eye);
                if (toward.length() > stare.reachBlocks() || toward.normalize().dot(look) < cone || !player.hasLineOfSight(mob)) {
                    continue;
                }
                Vec3 away = mob.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(stare.fleeBlocks());
                Vec3 goal = mob.position().add(away);
                fleeing.put(mob.getUUID(), new Flight(now + (long) stare.fleeSeconds() * TICKS_PER_SECOND, goal));
                mob.setTarget(null);
                if (mob.goalSelector.getAvailableGoals().stream().noneMatch(wrapped -> wrapped.getGoal() instanceof Flee)) {
                    mob.goalSelector.addGoal(0, new Flee(mob));
                }
            }
        }
    }

    private static final class Flee extends Goal {
        private final Mob mob;

        private Flee(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fleeing.containsKey(mob.getUUID());
        }

        @Override
        public void start() {
            run();
        }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone()) {
                run();
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        private void run() {
            Flight flight = fleeing.get(mob.getUUID());
            if (flight != null) {
                mob.getNavigation().moveTo(flight.goal().x, flight.goal().y, flight.goal().z, RUN);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer && fleeing.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        fleeing.clear();
    }
}
