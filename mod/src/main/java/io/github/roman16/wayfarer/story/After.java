package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class After {
    static final String DOORS = "the_doors";
    static final String HOME = "home";
    private static final String BLIND = "blind";
    private static final String SIGHT = "sight";
    private static final String CLOSE = "close";
    private static final String SCRATCH = "after";
    private static final String BEGUN = "begun";
    private static final String PIP_LOST = "the_hollow/pip=lost";
    private static final String PIP_SKIN = "wayfarer:textures/entity/people/pip.png";
    private static final int CHECK_TICKS = 20;
    private static final Map<UUID, Vec3> ANCHORS = new HashMap<>();

    private After() {}

    public record Ending(String afterwards) implements CustomPacketPayload {
        public static final Type<Ending> TYPE = new Type<>(Wayfarer.id("ending"));
        public static final StreamCodec<ByteBuf, Ending> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Ending::new, Ending::afterwards);

        @Override
        public Type<Ending> type() {
            return TYPE;
        }
    }

    public record Skin(String player, String texture) implements CustomPacketPayload {
        public static final Type<Skin> TYPE = new Type<>(Wayfarer.id("skin"));
        public static final StreamCodec<ByteBuf, Skin> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Skin::player,
                ByteBufCodecs.STRING_UTF8, Skin::texture, Skin::new);

        @Override
        public Type<Skin> type() {
            return TYPE;
        }
    }

    static Optional<String> door(Progress progress) {
        return progress.done(DOORS) ? progress.choice(DOORS, DOORS) : Optional.empty();
    }

    public static boolean free(MinecraftServer server, BlockPos at) {
        if (!Engine.running(server)) {
            return false;
        }
        Progress progress = Progress.of(server);
        return door(progress).filter(door -> door.equals(BLIND) || door.equals(CLOSE) || door.equals(SIGHT) && home(progress, at)).isPresent();
    }

    public static boolean hunted(MinecraftServer server) {
        return Engine.running(server) && door(Progress.of(server)).filter(SIGHT::equals).isPresent();
    }

    public static boolean sheltered(ServerPlayer player) {
        return hunted(player.server) && home(Progress.of(player.server), player.blockPosition());
    }

    public static boolean gone(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return Engine.running(server) && door(progress).filter(CLOSE::equals).isPresent() && Engine.holds(progress, PIP_LOST);
    }

    private static boolean pip(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return Engine.running(server) && door(progress).filter(CLOSE::equals).isPresent() && !Engine.holds(progress, PIP_LOST);
    }

    private static boolean home(Progress progress, BlockPos at) {
        int reach = Balance.get().after().homeBlocks();
        return progress.place(HOME).filter(home -> Steps.horizontal(home, at) <= (double) reach * reach).isPresent();
    }

    private static void begin(MinecraftServer server, String door) {
        Progress progress = Progress.of(server);
        progress.scratch(SCRATCH).putBoolean(BEGUN, true);
        if (door.equals(SIGHT)) {
            progress.place(HOME, Caravan.last(server));
        }
        if (door.equals(CLOSE)) {
            Journey.of(server).end();
        }
        progress.setDirty();
        Journal.syncAll(server);
        server.getPlayerList().getPlayers().forEach(After::greet);
        if (!gone(server)) {
            server.getPlayerList().getPlayers().forEach(Afterwards::open);
        }
        Engine.open(server);
    }

    private static void greet(ServerPlayer player) {
        if (gone(player.server)) {
            PacketDistributor.sendToPlayer(player, new Ending(Afterwards.view(player.server).toString()));
        }
        if (pip(player.server)) {
            player.server.getPlayerList().getPlayers().forEach(member -> PacketDistributor.sendToPlayer(player, new Skin(member.getUUID().toString(), PIP_SKIN)));
            PacketDistributor.sendToAllPlayers(new Skin(player.getUUID().toString(), PIP_SKIN));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Engine.running(server)) {
            return;
        }
        Progress progress = Progress.of(server);
        door(progress).filter(door -> !progress.scratch(SCRATCH).getBoolean(BEGUN)).ifPresent(door -> begin(server, door));
        if (gone(server)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Vec3 anchor = ANCHORS.computeIfAbsent(player.getUUID(), any -> player.position());
                if (player.position().distanceToSqr(anchor) > 1) {
                    player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ANCHORS.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Engine.running(player.server) && door(Progress.of(player.server)).isPresent()) {
            greet(player);
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != Level.OVERWORLD || !Engine.running(player.server)) {
            return;
        }
        Engine.active(player.server).stream().filter(active -> active.step().kind().equals(HOME)).toList()
                .forEach(active -> Engine.complete(player.server, active));
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && gone(player.server)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && gone(player.server)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && gone(player.server)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && hunted(level.getServer()) && home(Progress.of(level.getServer()), event.getPos())) {
            event.setCanceled(true);
        }
    }
}
