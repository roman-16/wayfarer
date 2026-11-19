package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Doors;
import io.github.roman16.wayfarer.story.Talk;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

public final class Gaze {
    private static final int SYNC_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final int PHANTOM_HEIGHT = 20;
    private static final int PHANTOM_SPREAD = 16;
    private static final int SLAYER_REACH = 256;

    private Gaze() {}

    public static boolean airborne(Player player) {
        return Gliding.gliding(player);
    }

    public static int capacity(int veil) {
        Balance.Gaze balance = Balance.get().gaze();
        return balance.capacitySeconds().get(veil) * TICKS_PER_SECOND * balance.drainFactor();
    }

    public static int step(int filled, boolean airborne, boolean night, int capacity) {
        if (!airborne) {
            return Math.max(0, filled - 1);
        }
        if (night) {
            return capacity;
        }
        return Math.min(capacity, filled + Balance.get().gaze().drainFactor());
    }

    public static int veil(ServerPlayer player) {
        List<Item> veils = Wayfarer.VEILS.stream().map(veil -> veil.get()).toList();
        return CuriosApi.getCuriosInventory(player)
                .map(curios -> {
                    for (int level = veils.size(); level > 0; level--) {
                        if (curios.isEquipped(veils.get(level - 1))) {
                            return level;
                        }
                    }
                    return 0;
                })
                .orElse(0);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || !(dragon.level() instanceof ServerLevel level)) {
            return;
        }
        boolean witnessed = !level.getPlayers(player -> player.distanceToSqr(dragon) < SLAYER_REACH * SLAYER_REACH).isEmpty();
        if (witnessed && Journey.of(level.getServer()).end()) {
            if (!Doors.blinding(level.getServer())) {
                Talk.notice(Component.translatable("wayfarer.gaze.lifted"));
            }
            for (ServerPlayer member : level.getServer().getPlayerList().getPlayers()) {
                Nights.sync(member);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Journey.over(player.server)) {
            if (player.getData(Wayfarer.GAZE) != 0) {
                player.setData(Wayfarer.GAZE, 0);
                PacketDistributor.sendToPlayer(player, GazePayload.CLOSED);
            }
            return;
        }
        boolean airborne = airborne(player);
        int veil = veil(player);
        int capacity = capacity(veil);
        int filled = step(player.getData(Wayfarer.GAZE), airborne, player.level().isNight(), capacity);
        player.setData(Wayfarer.GAZE, filled);
        Balance.Gaze balance = Balance.get().gaze();
        if (airborne && filled >= capacity && player.tickCount % (balance.huntIntervalSeconds() * TICKS_PER_SECOND) == 0) {
            hunt(player, balance.huntPhantoms());
        }
        if (player.tickCount % SYNC_TICKS == 0) {
            float fill = capacity == 0 ? 0 : (float) filled / capacity;
            PacketDistributor.sendToPlayer(player, new GazePayload(airborne || filled > 0, fill, veil, balance.capacitySeconds().get(veil)));
        }
    }

    private static void hunt(ServerPlayer player, int phantoms) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < phantoms; i++) {
            Phantom phantom = EntityType.PHANTOM.create(level);
            if (phantom == null) {
                return;
            }
            double x = player.getX() + level.random.nextInt(PHANTOM_SPREAD * 2) - PHANTOM_SPREAD;
            double z = player.getZ() + level.random.nextInt(PHANTOM_SPREAD * 2) - PHANTOM_SPREAD;
            phantom.moveTo(x, player.getY() + PHANTOM_HEIGHT, z, level.random.nextFloat() * 360, 0);
            phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(phantom.blockPosition()), MobSpawnType.EVENT, null);
            phantom.setTarget(player);
            level.addFreshEntity(phantom);
        }
    }
}
