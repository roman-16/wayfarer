package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Caravan;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
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
        } else {
            Optional<GlobalPos> last = player.hasData(Wayfarer.LAST_FIRE) ? Optional.of(player.getData(Wayfarer.LAST_FIRE)) : Optional.empty();
            Optional<GlobalPos> caravan = Caravan.standing(server).map(camp -> GlobalPos.of(Level.OVERWORLD, camp));
            boolean toCaravan = caravan.isPresent() && last.map(fire -> distance(fire, player) > distance(caravan.get(), player)).orElse(true);
            Optional<GlobalPos> fire = toCaravan ? caravan : last;
            if (fire.isEmpty()) {
                return;
            }
            server.getLevel(fire.get().dimension()).getChunk(fire.get().pos());
            at = Joining.ground(server, new Joining.Place(fire.get().dimension(), fire.get().pos()));
            told = Component.translatable(toCaravan ? "wayfarer.respawn.caravan" : "wayfarer.respawn.fire");
        }
        ServerLevel level = server.getLevel(at.dimension());
        event.setDimensionTransition(new DimensionTransition(level, Vec3.atBottomCenterOf(at.pos()), Vec3.ZERO, player.getYRot(), 0, DimensionTransition.DO_NOTHING));
        TOLD.put(player.getUUID(), told);
    }

    private static double distance(GlobalPos fire, ServerPlayer player) {
        return fire.dimension() == player.level().dimension() ? fire.pos().distToCenterSqr(player.position()) : Double.MAX_VALUE;
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        MobEffectInstance warm = event.getOriginal().getEffect(Wayfarer.WARM);
        if (event.isWasDeath() && warm != null) {
            event.getEntity().addEffect(new MobEffectInstance(warm));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Component told = TOLD.remove(event.getEntity().getUUID());
        if (told != null && event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(told.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }
}
