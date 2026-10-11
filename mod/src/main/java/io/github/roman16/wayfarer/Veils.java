package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Progress;
import io.github.roman16.wayfarer.story.Sense;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Veils {
    private static final String SKY_VEIL = "sky_veil";
    private static final String NIGHT_WALK = "night_walk";
    private static final String HUSH = "hush";
    private static final String FLARE = "flare";
    private static final int LOOK_TICKS = 20;
    private static final int PROVOKED_TICKS = 4 * 20;
    private static final Map<UUID, Integer> spent = new HashMap<>();

    private Veils() {}

    private static boolean gift(ServerPlayer player, String gift) {
        return Engine.running(player.server) && Progress.of(player.server).gift(gift);
    }

    private static boolean provoked(LivingEntity hunter, ServerPlayer player) {
        return hunter.getLastHurtByMob() == player && hunter.tickCount - hunter.getLastHurtByMobTimestamp() < PROVOKED_TICKS;
    }

    static boolean hidden(ServerPlayer player, LivingEntity hunter) {
        if (provoked(hunter, player)) {
            return false;
        }
        if (hunter instanceof Phantom) {
            return gift(player, SKY_VEIL);
        }
        if (!Fire.fears(hunter)) {
            return false;
        }
        return gift(player, HUSH) && player.isShiftKeyDown() || gift(player, NIGHT_WALK) && player.level().isNight() && !player.isSprinting();
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && hidden(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LOOK_TICKS != 0 || !Engine.running(server)) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<? extends Mob> losing = level.getEntities(EntityTypeTest.forClass(Mob.class),
                    mob -> mob.getTarget() instanceof ServerPlayer player && hidden(player, mob));
            for (Mob mob : losing) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getEntity() instanceof Mob attacker) || !Fire.fears(attacker)
                || !gift(player, FLARE) || !Sense.held(player)) {
            return;
        }
        Balance.Flare balance = Balance.get().flare();
        int used = spent.getOrDefault(player.getUUID(), 0);
        if (used >= balance.charges()) {
            player.displayClientMessage(Component.translatable("wayfarer.flare.spent").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return;
        }
        spent.put(player.getUUID(), used + 1);
        event.setCanceled(true);
        ServerLevel level = player.serverLevel();
        int reach = balance.reachBlocks();
        List<Mob> crumbled = level.getEntities(EntityTypeTest.forClass(Mob.class), new AABB(player.blockPosition()).inflate(reach),
                mob -> Fire.fears(mob) && mob.distanceToSqr(player) <= (double) reach * reach);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY(1), player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.5), player.getZ(), 128, reach / 4.0, 1, reach / 4.0, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1, 0.6f);
        for (Mob mob : crumbled) {
            level.sendParticles(ParticleTypes.WHITE_ASH, mob.getX(), mob.getY(0.5), mob.getZ(), 40, mob.getBbWidth() / 2, mob.getBbHeight() / 2,
                    mob.getBbWidth() / 2, 0.02);
            mob.discard();
        }
    }

    @SubscribeEvent
    public static void onWarmed(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance().is(Wayfarer.WARM)) {
            spent.remove(player.getUUID());
        }
    }
}
