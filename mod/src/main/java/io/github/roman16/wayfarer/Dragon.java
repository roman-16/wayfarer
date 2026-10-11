package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Dragon {
    private static final String EDF_HEALTH = "DragonHealth";
    private static final String EDF_OBJECTIVE = "EDFR.config";
    private static final String TICKED = "ticked";
    private static final int END_REACH = 192;
    private static final int SHARDS = 12;
    private static final int LOOK_TICKS = 20;
    private static final Set<UUID> sized = new HashSet<>();
    private static final Map<UUID, Vec3> charges = new HashMap<>();

    private Dragon() {}

    private static int shards(MinecraftServer server) {
        return Journey.claimed(server).size();
    }

    private static double aimError(MinecraftServer server) {
        return Balance.get().dragon().blindAimBlocks() * Math.max(0, SHARDS - shards(server)) / (double) SHARDS;
    }

    private static int health(MinecraftServer server) {
        Balance.Dragon balance = Balance.get().dragon();
        int players = Math.max(1, server.getPlayerList().getPlayerCount());
        return balance.healthBase() + balance.healthPerShard() * shards(server) + balance.healthPerPlayer() * (players - 1);
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.END || !Engine.running(level.getServer())) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (event.getEntity() instanceof EnderDragon && !event.loadedFromDisk()) {
            Objective objective = server.getScoreboard().getObjective(EDF_OBJECTIVE);
            if (objective != null) {
                server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(EDF_HEALTH), objective).set(health(server));
            }
        } else if (event.getEntity() instanceof DragonFireball fireball && !event.loadedFromDisk() && aimError(server) > 0) {
            Vec3 motion = fireball.getDeltaMovement();
            double spread = Math.atan2(aimError(server), Math.max(1, nearest(level, fireball)));
            double yaw = (fireball.getRandom().nextDouble() * 2 - 1) * spread;
            double pitch = (fireball.getRandom().nextDouble() * 2 - 1) * spread;
            fireball.setDeltaMovement(motion.yRot((float) yaw).xRot((float) pitch));
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getDimension() == Level.END && event.getEntity() instanceof ServerPlayer player && Engine.running(player.server)) {
            player.seenCredits = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null || !Engine.running(server)) {
            return;
        }
        for (EnderDragon dragon : end.getEntities(EntityTypeTest.forClass(EnderDragon.class), Entity::isAlive)) {
            if (!sized.contains(dragon.getUUID()) && (dragon.getTags().contains(TICKED) || dragon.tickCount > LOOK_TICKS)) {
                size(end, dragon, health(server));
            }
            EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
            double error = aimError(server);
            if (phase == EnderDragonPhase.CHARGING_PLAYER && error > 0) {
                DragonChargePlayerPhase charge = dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER);
                Vec3 target = charge.getFlyTargetLocation();
                if (target != null && !target.equals(charges.get(dragon.getUUID()))) {
                    Vec3 wide = target.add((dragon.getRandom().nextDouble() * 2 - 1) * error, 0, (dragon.getRandom().nextDouble() * 2 - 1) * error);
                    charge.setTarget(wide);
                    charges.put(dragon.getUUID(), wide);
                }
            }
            if ((phase == EnderDragonPhase.STRAFE_PLAYER || phase == EnderDragonPhase.CHARGING_PLAYER) && shards(server) <= Balance.get().dragon().sightShards()
                    && server.getTickCount() % LOOK_TICKS == 0) {
                Optional<ServerPlayer> hunted = end.players().stream().min(Comparator.comparingDouble(player -> player.distanceToSqr(dragon)));
                if (hunted.isPresent() && !sees(dragon, hunted.get())) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                }
            }
        }
    }

    private static boolean sees(EnderDragon dragon, ServerPlayer player) {
        HitResult hit = dragon.level().clip(new ClipContext(dragon.getEyePosition(), player.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                dragon));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static double nearest(ServerLevel level, Entity entity) {
        return level.players().stream().mapToDouble(player -> player.distanceTo(entity)).min().orElse(1);
    }

    private static void size(ServerLevel level, EnderDragon dragon, int health) {
        dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        dragon.setHealth(health);
        sized.add(dragon.getUUID());
        int shards = shards(level.getServer());
        List<EndCrystal> crystals = level.getEntities(EntityType.END_CRYSTAL, new AABB(0, 0, 0, 0, 0, 0).inflate(END_REACH), Entity::isAlive);
        crystals.sort(Comparator.comparingDouble(crystal -> Math.atan2(crystal.getZ(), crystal.getX())));
        int keep = Math.min(shards, crystals.size());
        int spacing = crystals.isEmpty() ? 1 : Math.max(1, crystals.size() / Math.max(1, keep));
        int kept = 0;
        for (int index = 0; index < crystals.size(); index++) {
            if (kept < keep && index % spacing == 0) {
                kept++;
            } else {
                crystals.get(index).discard();
            }
        }
    }
}
