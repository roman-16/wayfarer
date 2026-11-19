package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Bell;
import io.github.roman16.wayfarer.story.Hold;
import io.github.roman16.wayfarer.story.Memories;
import io.github.roman16.wayfarer.story.Talk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Fire {
    private static final int SCAN_TICKS = 10;
    public static final int REST_CHECK_TICKS = 20;
    private static final int FLEE_MARGIN = 6;
    private static final double FLEE_SPEED = 1.2;
    private static final long TICKS_PER_DAY = 24000;
    private static final long DUSK = 12000;
    private static final Map<UUID, BlockPos> SHELTERED = new HashMap<>();
    private static final Set<UUID> RESTING = new HashSet<>();
    private static final Set<UUID> OWED = new HashSet<>();
    private static final ResourceLocation WARMED = Wayfarer.id("warm");
    private static final String RESTED = "rested";

    private Fire() {}

    public static boolean fears(Entity entity) {
        return entity instanceof Mob mob
                && (entity instanceof Enemy || mob.getTarget() instanceof Player hunted && mob.getLastHurtByMob() != hunted)
                && !(entity instanceof Raider)
                && !(entity instanceof Warden)
                && !(entity instanceof EnderDragon)
                && !(entity instanceof WitherBoss);
    }

    public static boolean sheltered(ServerPlayer player) {
        return SHELTERED.containsKey(player.getUUID());
    }

    public static boolean inside(BlockPos fire, Vec3 at, int radius) {
        return Vec3.atCenterOf(fire).distanceToSqr(at) < (double) radius * radius;
    }

    public static int untilTomorrowsDusk(long dayTime) {
        long toDusk = Math.floorMod(DUSK - dayTime, TICKS_PER_DAY);
        return (int) (toDusk < TICKS_PER_DAY / 2 ? toDusk + TICKS_PER_DAY : toDusk);
    }

    public static Optional<BlockPos> nearest(ServerLevel level, Vec3 at, int radius) {
        return fires(level, BlockPos.containing(at), radius).stream()
                .filter(fire -> inside(fire, at, radius))
                .min(Comparator.comparingDouble(fire -> Vec3.atCenterOf(fire).distanceToSqr(at)));
    }

    private static List<BlockPos> fires(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> fires = new ArrayList<>();
        ChunkPos low = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos high = new ChunkPos(center.offset(radius, 0, radius));
        for (int x = low.x; x <= high.x; x++) {
            for (int z = low.z; z <= high.z; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                chunk.getBlockEntities().values().stream()
                        .filter(entity -> entity instanceof CampfireBlockEntity && CampfireBlock.isLitCampfire(entity.getBlockState()))
                        .forEach(entity -> fires.add(entity.getBlockPos()));
            }
        }
        return fires;
    }

    public static List<ServerPlayer> around(ServerPlayer player, BlockPos fire) {
        int radius = Balance.get().fire().circleBlocks();
        List<ServerPlayer> listeners = new ArrayList<>();
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (member == player || member.level() == player.level() && inside(fire, member.position(), radius)) {
                listeners.add(member);
            }
        }
        return listeners;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % SCAN_TICKS != 0 || Tents.isTent(level.dimension())) {
            return;
        }
        int radius = Balance.get().fire().circleBlocks();
        Optional<BlockPos> ringing = level.dimension() == Level.OVERWORLD ? Bell.camp(level.getServer()) : Optional.empty();
        Set<BlockPos> holding = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            Optional<BlockPos> fire = Nights.fearless(player) || Hold.active(player.server) ? Optional.empty()
                    : ringing.filter(camp -> inside(camp, player.position(), radius * 2)).or(() -> nearest(level, player.position(), radius))
                            .or(() -> Talk.listening(player) ? Optional.of(player.blockPosition()) : Optional.empty());
            if (fire.isPresent()) {
                SHELTERED.put(player.getUUID(), fire.get());
                holding.add(fire.get());
            } else {
                SHELTERED.remove(player.getUUID());
            }
        }
        for (BlockPos fire : holding) {
            int circle = Bell.circle(level.getServer(), fire);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(fire).inflate(circle + 2), Fire::fears)) {
                if (mob.getTarget() instanceof ServerPlayer target && SHELTERED.containsKey(target.getUUID())) {
                    mob.setTarget(null);
                }
                if (inside(fire, mob.position(), circle)) {
                    flee(mob, fire, circle);
                }
            }
        }
    }

    private static void flee(Mob mob, BlockPos fire, int radius) {
        Vec3 centre = Vec3.atCenterOf(fire);
        Vec3 away = mob.position().subtract(centre).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0, mob.getRandom().nextDouble() - 0.5);
        }
        Vec3 goal = centre.add(away.normalize().scale(radius + FLEE_MARGIN));
        mob.getNavigation().moveTo(goal.x, mob.getY(), goal.z, FLEE_SPEED);
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (fears(event.getEntity())
                && event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && SHELTERED.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % REST_CHECK_TICKS != 0) {
            return;
        }
        if (OWED.contains(player.getUUID()) && !Talk.busy()) {
            OWED.remove(player.getUUID());
            warm(player);
        }
        Optional<BlockPos> fire = Tents.isTent(player.level().dimension()) || Memories.dream(player.level()) || Gaze.airborne(player) || player.isSpectator()
                ? Optional.empty()
                : nearest(player.serverLevel(), player.position(), Balance.get().fire().restBlocks());
        if (fire.isEmpty()) {
            RESTING.remove(player.getUUID());
            return;
        }
        if (RESTING.add(player.getUUID())) {
            Record.event(player.server, "rest", player);
        }
        rest(player, fire.get());
    }

    public static boolean resting(ServerPlayer player) {
        return RESTING.contains(player.getUUID());
    }

    private static void rest(ServerPlayer player, BlockPos fire) {
        player.setData(Wayfarer.LAST_FIRE, GlobalPos.of(player.level().dimension(), fire.immutable()));
        NeoForge.EVENT_BUS.post(new RestEvent(player, fire));
        if (Talk.busy()) {
            if (Journey.awake(player.server) && !Journey.over(player.server) && !player.hasEffect(Wayfarer.WARM)) {
                OWED.add(player.getUUID());
            }
            return;
        }
        warm(player);
    }

    private static void warm(ServerPlayer player) {
        if (Journey.awake(player.server) && !Journey.over(player.server) && !player.hasEffect(Wayfarer.WARM)) {
            player.addEffect(new MobEffectInstance(Wayfarer.WARM, untilTomorrowsDusk(player.server.overworld().getDayTime()), 0, true, false, true));
            Talk.tell(player, Component.translatable("wayfarer.fire.warm"));
            Firsts.first(player, "warm");
            AdvancementHolder advancement = player.server.getAdvancements().get(WARMED);
            if (advancement != null) {
                player.getAdvancements().award(advancement, RESTED);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHELTERED.remove(event.getEntity().getUUID());
        RESTING.remove(event.getEntity().getUUID());
        OWED.remove(event.getEntity().getUUID());
    }
}
