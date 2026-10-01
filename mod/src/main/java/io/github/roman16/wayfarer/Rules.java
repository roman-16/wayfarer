package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Rules {
    public static final TagKey<EntityType<?>> SAILING_SHIPS = TagKey.create(Registries.ENTITY_TYPE, Wayfarer.id("sailing_ships"));

    private static final int PORTAL_WITNESS_BLOCKS = 8;

    private Rules() {}

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().closerThan(event.getPos(), PORTAL_WITNESS_BLOCKS)) {
                    player.displayClientMessage(Component.translatable("wayfarer.first.portal").withStyle(ChatFormatting.GRAY), true);
                    Notes.first(player, "portal");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof Player player)) {
            return;
        }
        Entity vehicle = event.getEntityBeingMounted();
        int needed = vehicle.getType().is(Gaze.AIRCRAFT) ? Balance.INSTANCE.unlocks().aircraftEyes()
                : vehicle.getType().is(SAILING_SHIPS) ? Balance.INSTANCE.unlocks().sailingShipEyes()
                : 0;
        if (claimed(player) >= needed) {
            return;
        }
        event.setCanceled(true);
        String key = vehicle.getType().is(Gaze.AIRCRAFT) ? "wayfarer.unlock.aircraft" : "wayfarer.unlock.sailing_ships";
        player.displayClientMessage(Component.translatable(key, needed).withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        int needed = Balance.INSTANCE.unlocks().aircraftEyes();
        if (event.getEntity() instanceof ServerPlayer player && player.isFallFlying() && claimed(player) < needed) {
            player.stopFallFlying();
            player.displayClientMessage(Component.translatable("wayfarer.unlock.aircraft", needed).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static int claimed(Player player) {
        return player instanceof ServerPlayer serverPlayer ? Journey.claimed(serverPlayer).size() : HudState.compass.claimed().size();
    }
}
