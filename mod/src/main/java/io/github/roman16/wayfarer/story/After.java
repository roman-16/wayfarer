package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.RestEvent;
import io.github.roman16.wayfarer.Tents;
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
    static final String OWN_FIRE = "own_fire";
    private static final String HOME_STEP = "the_promised_home/the_camp";
    private static final String FINALE = "finale";
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

    public record Ending(String afterwards, String epilogue) implements CustomPacketPayload {
        public static final Type<Ending> TYPE = new Type<>(Wayfarer.id("ending"));
        public static final StreamCodec<ByteBuf, Ending> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Ending::afterwards,
                ByteBufCodecs.STRING_UTF8, Ending::epilogue, Ending::new);

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

    public record Credits(String epilogue) implements CustomPacketPayload {
        public static final Type<Credits> TYPE = new Type<>(Wayfarer.id("credits"));
        public static final StreamCodec<ByteBuf, Credits> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Credits::new, Credits::epilogue);

        @Override
        public Type<Credits> type() {
            return TYPE;
        }
    }

    static void done(MinecraftServer server, Story.Quest quest) {
        if (quest.act().equals(FINALE) && quest.after().contains(DOORS)) {
            Talk.then(quest.id(), () -> PacketDistributor.sendToAllPlayers(new Credits(Epilogue.view(server).toString())));
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

    public static boolean dark(ServerPlayer player) {
        return hunted(player.server) && dark(Progress.of(player.server), player.blockPosition());
    }

    public static boolean hunted(MinecraftServer server) {
        return Engine.running(server) && door(Progress.of(server)).filter(SIGHT::equals).isPresent();
    }

    public static boolean sheltered(ServerPlayer player) {
        Progress progress = Progress.of(player.server);
        return hunted(player.server) && (home(progress, player.blockPosition()) || dark(progress, player.blockPosition()));
    }

    public static boolean gone(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return Engine.running(server) && door(progress).filter(CLOSE::equals).isPresent() && Engine.holds(progress, PIP_LOST);
    }

    private static boolean pip(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return Engine.running(server) && door(progress).filter(CLOSE::equals).isPresent() && !Engine.holds(progress, PIP_LOST);
    }

    static boolean byOwnFire(Progress progress) {
        return OWN_FIRE.equals(progress.choices.get(HOME_STEP));
    }

    private static boolean home(Progress progress, BlockPos at) {
        int reach = Balance.get().after().homeBlocks();
        return progress.place(HOME).filter(home -> Steps.horizontal(home, at) <= (double) reach * reach).isPresent();
    }

    private static boolean dark(Progress progress, BlockPos at) {
        int reach = Balance.get().after().darkBlocks();
        return Afterwards.darkLands(progress).stream().anyMatch(land -> Steps.horizontal(land, at) <= (double) reach * reach);
    }

    private static void begin(MinecraftServer server, String door) {
        Progress progress = Progress.of(server);
        progress.scratch(SCRATCH).putBoolean(BEGUN, true);
        if (door.equals(SIGHT)) {
            Optional<BlockPos> fire = progress.place(OWN_FIRE);
            if (fire.isPresent() && Steps.alone(progress)) {
                progress.place(HOME, fire.get());
                progress.choices.put(HOME_STEP, OWN_FIRE);
            } else {
                progress.place(HOME, Caravan.last(server));
            }
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
            PacketDistributor.sendToPlayer(player, new Ending(Afterwards.view(player.server).toString(), Epilogue.view(player.server).toString()));
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
    public static void onRest(RestEvent event) {
        ServerPlayer player = event.player();
        MinecraftServer server = player.server;
        BlockPos fire = event.fire();
        if (!Engine.running(server) || player.level().dimension() != Level.OVERWORLD || !Tents.pitchedNear(player.serverLevel(), fire, Balance.get().tent().fireBlocks())
                || Caravan.fire(server).filter(caravan -> Steps.horizontal(caravan, fire) <= Math.pow(Balance.get().fire().circleBlocks(), 2)).isPresent()) {
            return;
        }
        Progress progress = Progress.of(server);
        if (progress.place(OWN_FIRE).filter(fire::equals).isEmpty()) {
            progress.place(OWN_FIRE, fire);
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
                && hunted(level.getServer()) && (home(Progress.of(level.getServer()), event.getPos()) || dark(Progress.of(level.getServer()), event.getPos()))) {
            event.setCanceled(true);
        }
    }
}
