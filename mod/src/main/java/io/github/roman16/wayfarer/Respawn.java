package io.github.roman16.wayfarer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

public final class Respawn {
    private static final Map<UUID, Component> TOLD = new HashMap<>();

    private Respawn() {}

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        Vec3 fell = player.position();
        Optional<ServerPlayer> friend = server.getPlayerList().getPlayers().stream()
                .filter(other -> other != player && !other.isSpectator() && other.isAlive())
                .min(Comparator.comparingDouble(other -> other.level() == player.level() ? other.distanceToSqr(fell) : Double.MAX_VALUE));
        Joining.Place at;
        Component told;
        if (friend.isPresent()) {
            at = Joining.ground(server, Joining.beside(friend.get()));
            told = Component.translatable("wayfarer.respawn.friend", friend.get().getDisplayName());
        } else if (player.hasData(Wayfarer.LAST_FIRE)) {
            GlobalPos fire = player.getData(Wayfarer.LAST_FIRE);
            server.getLevel(fire.dimension()).getChunk(fire.pos());
            at = Joining.ground(server, new Joining.Place(fire.dimension(), fire.pos()));
            told = Component.translatable("wayfarer.respawn.fire");
        } else {
            return;
        }
        ServerLevel level = server.getLevel(at.dimension());
        event.setDimensionTransition(new DimensionTransition(level, Vec3.atBottomCenterOf(at.pos()), Vec3.ZERO, player.getYRot(), 0, DimensionTransition.DO_NOTHING));
        TOLD.put(player.getUUID(), told);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Component told = TOLD.remove(event.getEntity().getUUID());
        if (told != null && event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(told.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }
}
