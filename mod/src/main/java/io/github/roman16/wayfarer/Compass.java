package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Progress;
import io.github.roman16.wayfarer.story.Sense;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Compass {
    private static final String STONE_SIGHT = "stone_sight";
    private static final String COMPASS_MARK = "compass_mark";
    private static final String EMBER_FLARES = "ember_flares";
    private static final String EMBER_GUTTERS = "ember_gutters";
    private static final String FIRE_CIRCLE = "fire_circle";
    private static final String TENTS = "nomadictents";
    private static final Map<UUID, Bearing> BEARINGS = new HashMap<>();
    private static final Map<UUID, CompassPayload> SENT = new HashMap<>();

    private Compass() {}

    public record Aim(@Nullable BlockPos pos) {
        public static final Aim NONE = new Aim(null);
    }

    private static final class Bearing {
        Aim aim = Aim.NONE;
        BlockPos trailStart;
        int heat;

        void aim(Aim next) {
            if (!next.equals(aim)) {
                heat = 0;
                trailStart = null;
            }
            aim = next;
        }
    }

    public static void forget(ServerPlayer player) {
        BEARINGS.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BEARINGS.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % Balance.get().compass().refreshTicks() == 0) {
            CompassPayload reading = reading(player);
            CompassPayload before = SENT.put(player.getUUID(), reading);
            if (before != null) {
                heard(player, before, reading);
            }
            PacketDistributor.sendToPlayer(player, reading);
        }
    }

    private static void heard(ServerPlayer player, CompassPayload before, CompassPayload now) {
        if (before.mode() == CompassPayload.Mode.FAR && now.mode() == CompassPayload.Mode.FAR && now.heat() != before.heat()) {
            Sounds.play(player, now.heat() > before.heat() ? EMBER_FLARES : EMBER_GUTTERS);
        }
        if (leads(now).stream().anyMatch(lead -> !leads(before).contains(lead))) {
            Sounds.play(player, COMPASS_MARK);
        }
        if (before.shelter() != CompassPayload.Shelter.FIRE && now.shelter() == CompassPayload.Shelter.FIRE) {
            Sounds.play(player, FIRE_CIRCLE);
        }
    }

    private static List<CompassPayload.Marker> leads(CompassPayload reading) {
        return reading.markers().stream()
                .filter(marker -> marker.kind() == CompassPayload.Kind.GOAL || marker.kind() == CompassPayload.Kind.LEAD).toList();
    }

    private static CompassPayload reading(ServerPlayer player) {
        List<ResourceLocation> claimed = Journey.claimed(player.server);
        Balance.Compass balance = Balance.get().compass();
        Bearing bearing = BEARINGS.computeIfAbsent(player.getUUID(), key -> new Bearing());
        BlockPos pos = player.blockPosition();
        boolean shown = player.level().dimension() == Level.OVERWORLD;
        CompassEvent event = NeoForge.EVENT_BUS.post(new CompassEvent(player, shown, Journey.awake(player.server), Aim.NONE, markers(player)));
        if (!event.shown()) {
            return CompassPayload.hidden(claimed);
        }
        List<CompassPayload.Marker> markers = List.copyOf(event.markers());
        CompassPayload.Shelter shelter = shelter(player);
        if (!event.awake()) {
            return still(event.named() ? CompassPayload.Mode.COLD : CompassPayload.Mode.BARE, claimed, markers, shelter, false);
        }
        bearing.aim(event.aim());
        boolean sight = Engine.running(player.server) && Progress.of(player.server).gift(STONE_SIGHT) && !Journey.over(player.server);
        if (Gaze.airborne(player)) {
            return still(CompassPayload.Mode.AIR, claimed, markers, shelter, sight);
        }
        if (Stillness.found(player)) {
            return still(CompassPayload.Mode.DIM, claimed, markers, shelter, sight);
        }
        Aim aim = bearing.aim;
        if (aim.pos() == null) {
            return still(CompassPayload.Mode.LOST, claimed, markers, shelter, sight);
        }
        if (bearing.trailStart == null) {
            bearing.trailStart = pos;
        }
        double moved = horizontal(bearing.trailStart, pos);
        if (Sense.held(player)) {
            Vec3 look = player.getLookAngle();
            Vec3 towards = new Vec3(aim.pos().getX() + 0.5 - player.getX(), 0, aim.pos().getZ() + 0.5 - player.getZ()).normalize();
            bearing.heat = facing(new Vec3(look.x, 0, look.z).normalize().dot(towards));
            bearing.trailStart = pos;
        } else if (moved >= balance.trailBlocks()) {
            bearing.heat = heat(horizontal(bearing.trailStart, aim.pos()) - horizontal(pos, aim.pos()), moved);
            bearing.trailStart = pos;
        }
        return new CompassPayload(CompassPayload.Mode.FAR, bearing.heat, claimed, markers, shelter, sight);
    }

    private static CompassPayload still(
            CompassPayload.Mode mode, List<ResourceLocation> claimed, List<CompassPayload.Marker> markers, CompassPayload.Shelter shelter, boolean sight) {
        return new CompassPayload(mode, 0, claimed, markers, shelter, sight);
    }

    private static CompassPayload.Shelter shelter(ServerPlayer player) {
        if (Shrines.inside(player)) {
            return CompassPayload.Shelter.SHRINE;
        }
        return Fire.sheltered(player) ? CompassPayload.Shelter.FIRE : CompassPayload.Shelter.NONE;
    }

    private static List<CompassPayload.Marker> markers(ServerPlayer player) {
        List<CompassPayload.Marker> markers = new ArrayList<>();
        for (ServerPlayer member : player.server.getPlayerList().getPlayers()) {
            if (member != player && member.level() == player.level()) {
                markers.add(new CompassPayload.Marker(
                        CompassPayload.Kind.TEAMMATE, member.getGameProfile().getName(), member.getBlockX(), member.getBlockZ()));
            }
        }
        for (Stones.Stone stone : Stones.mine(player)) {
            if (stone.dimension() == player.level().dimension()) {
                markers.add(new CompassPayload.Marker(CompassPayload.Kind.GRAVE, String.valueOf(stone.things()), stone.pos().getX(), stone.pos().getZ()));
            }
        }
        if (ModList.get().isLoaded(TENTS)) {
            for (Joining.Pitched tent : TentPitch.mine(player)) {
                if (tent.place().dimension() == player.level().dimension()) {
                    markers.add(new CompassPayload.Marker(CompassPayload.Kind.TENT, "", tent.place().pos().getX(), tent.place().pos().getZ()));
                }
            }
        }
        return markers;
    }

    public static int facing(double alignment) {
        return heat(alignment, 1);
    }

    public static int heat(double closerBy, double moved) {
        double ratio = closerBy / moved;
        if (ratio > 0.7) {
            return 2;
        }
        if (ratio >= 0) {
            return 1;
        }
        if (ratio > -0.7) {
            return -1;
        }
        return -2;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
