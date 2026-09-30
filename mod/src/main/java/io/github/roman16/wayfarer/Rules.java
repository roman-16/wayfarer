package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class Rules {
    public static final TagKey<EntityType<?>> SAILING_SHIPS = TagKey.create(Registries.ENTITY_TYPE, Wayfarer.id("sailing_ships"));

    private Rules() {}

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
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

    private static int claimed(Player player) {
        return player instanceof ServerPlayer serverPlayer ? Journey.claimed(serverPlayer).size() : HudState.compass.claimed().size();
    }
}
