package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Progress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
    public static final String MOORED = Wayfarer.MODID + "_moored";

    private static final int PORTAL_WITNESS_BLOCKS = 8;
    private static final String SHIPS_OPEN = "the_name";

    private Rules() {}

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().closerThan(event.getPos(), PORTAL_WITNESS_BLOCKS)) {
                    player.displayClientMessage(Component.translatable("wayfarer.first.portal").withStyle(ChatFormatting.GRAY), true);
                    Firsts.first(player, "portal");
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
        if (vehicle.getTags().contains(MOORED)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("wayfarer.shipyard.moored").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (vehicle.getType().is(SAILING_SHIPS) && player instanceof ServerPlayer sailor && !shipsOpen(sailor.server)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("wayfarer.unlock.sailing_ships").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static boolean shipsOpen(MinecraftServer server) {
        return !Engine.running(server) || Progress.of(server).done(SHIPS_OPEN);
    }
}
