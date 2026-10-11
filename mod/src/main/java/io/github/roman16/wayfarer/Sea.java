package io.github.roman16.wayfarer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Sea {
    private static final double ABOARD = 2;
    private static final int BEAST_REACH = 128;
    private static final double BEAST_SCALE = 4;
    private static final int RAM_REACH = 4;
    private static final int RISE_DEPTH = 8;
    private static final int SAMPLE_STEP = 8;
    private static final int STEER_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final double BELOW = 2;
    private static final double CHASE = 1.5;
    private static final int SHY = 16;
    private static final int BUBBLES = 64;
    private static final Map<UUID, Long> rammed = new HashMap<>();
    private static final Map<UUID, UUID> prey = new HashMap<>();

    private Sea() {}

    public static boolean afloat(ServerPlayer player) {
        return aboard(player) != null || player.isInWater();
    }

    @Nullable
    public static Boat aboard(Entity entity) {
        if (entity.getVehicle() instanceof Boat boat) {
            return boat;
        }
        return entity.level().getEntities(EntityTypeTest.forClass(Boat.class), entity.getBoundingBox().inflate(ABOARD), Entity::isAlive).stream().findFirst()
                .orElse(null);
    }

    public static boolean calling(Level level) {
        return Nights.night(level.getServer().overworld()) || level.isRaining();
    }

    public static boolean open(ServerLevel level, BlockPos at) {
        int reach = Balance.get().sea().openWaterBlocks();
        for (int dx = -reach; dx <= reach; dx += SAMPLE_STEP) {
            for (int dz = -reach; dz <= reach; dz += SAMPLE_STEP) {
                int x = at.getX() + dx;
                int z = at.getZ() + dz;
                if (dx * dx + dz * dz > reach * reach || !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (level.getFluidState(top).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<Deep> beasts(ServerLevel level, Vec3 near) {
        return level.getEntities(EntityTypeTest.forClass(Deep.class), new AABB(BlockPos.containing(near)).inflate(BEAST_REACH), Entity::isAlive);
    }

    public static Optional<Deep> rise(ServerLevel level, Vec3 beside, @Nullable Entity hunted) {
        BlockPos at = BlockPos.containing(beside).below(RISE_DEPTH);
        Deep beast = Wayfarer.DEEP.get().create(level);
        if (beast == null || !level.getFluidState(at).isSource()) {
            return Optional.empty();
        }
        beast.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        beast.getAttribute(Attributes.SCALE).setBaseValue(BEAST_SCALE);
        beast.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Balance.get().sea().beastHealth());
        beast.setHealth(beast.getMaxHealth());
        beast.setPersistenceRequired();
        level.addFreshEntity(beast);
        if (hunted != null) {
            prey.put(beast.getUUID(), hunted.getUUID());
        }
        return Optional.of(beast);
    }

    public static void hunt(Entity beast, Entity hunted) {
        prey.put(beast.getUUID(), hunted.getUUID());
    }

    public static void dive(ServerLevel level, Entity beast) {
        level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, beast.getX(), beast.getY(), beast.getZ(), BUBBLES, 1, 1, 1, 0.1);
        level.playSound(null, beast.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 1, 0.5f);
        prey.remove(beast.getUUID());
        rammed.remove(beast.getUUID());
        beast.discard();
    }

    private static int oneIn(MinecraftServer server) {
        Balance.Sea sea = Balance.get().sea();
        return Math.max(sea.beastOneInLeast(), sea.beastOneIn() - Shine.of(server) / sea.shinePerStep());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Balance.Sea sea = Balance.get().sea();
        if (server.getTickCount() % (sea.checkSeconds() * TICKS_PER_SECOND) == 0 && Rules.shipsOpen(server)) {
            for (ServerPlayer player : level.players()) {
                if (afloat(player) && calling(level) && beasts(level, player.position()).isEmpty() && open(level, player.blockPosition())
                        && level.random.nextInt(oneIn(server)) == 0) {
                    Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + 90).scale(SHY);
                    rise(level, player.position().add(side), null);
                }
            }
        }
        if (server.getTickCount() % STEER_TICKS == 0) {
            level.getEntities(EntityTypeTest.forClass(Deep.class), Entity::isAlive).forEach(beast -> steer(server, level, beast, sea));
        }
    }

    private static void steer(MinecraftServer server, ServerLevel level, Deep beast, Balance.Sea sea) {
        UUID hunting = prey.get(beast.getUUID());
        if (hunting == null && !calling(level)) {
            dive(level, beast);
            return;
        }
        Entity target = hunting != null ? level.getEntity(hunting) : null;
        if (target == null || !target.isAlive()) {
            ServerPlayer nearest = level.getNearestPlayer(beast, BEAST_REACH) instanceof ServerPlayer player ? player : null;
            if (nearest == null) {
                return;
            }
            target = aboard(nearest) instanceof Boat hull ? hull : nearest;
        }
        boolean flinching = beast.tickCount - beast.getLastHurtByMobTimestamp() < sea.flinchSeconds() * TICKS_PER_SECOND && beast.getLastHurtByMob() != null;
        if (flinching) {
            Vec3 away = beast.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(SHY);
            beast.getNavigation().moveTo(beast.getX() + away.x, target.getY() - RISE_DEPTH, beast.getZ() + away.z, CHASE);
            return;
        }
        beast.getNavigation().moveTo(target.getX(), target.getY() - BELOW, target.getZ(), CHASE);
        long now = server.getTickCount();
        boolean close = beast.position().subtract(target.position()).horizontalDistance() < RAM_REACH + beast.getBbWidth() / 2;
        if (close && now - rammed.getOrDefault(beast.getUUID(), 0L) >= (long) sea.ramSeconds() * TICKS_PER_SECOND) {
            rammed.put(beast.getUUID(), now);
            target.hurt(level.damageSources().mobAttack(beast), sea.ramDamage());
            level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1, 0.6f);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        rammed.clear();
        prey.clear();
    }
}
