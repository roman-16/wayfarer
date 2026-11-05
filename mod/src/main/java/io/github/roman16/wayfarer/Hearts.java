package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Talk;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Hearts {
    private static final int CHECK_TICKS = 20;
    private static final Map<UUID, Boolean> FED = new HashMap<>();
    private static final ResourceLocation VARIETY = ResourceLocation.fromNamespaceAndPath("solonion", "add_value");

    private Hearts() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0) {
            return;
        }
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        boolean fed = health != null && health.getModifier(VARIETY) != null;
        Boolean before = FED.put(player.getUUID(), fed);
        if (before != null && before != fed && player.isAlive()) {
            Talk.tell(player, Component.translatable(fed ? "wayfarer.hearts.varied" : "wayfarer.hearts.same"));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        FED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FED.remove(event.getEntity().getUUID());
    }
}
