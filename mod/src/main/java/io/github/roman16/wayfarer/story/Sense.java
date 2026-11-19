package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Stones;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Sense {
    public static final String KEY = "key.wayfarer.ember_sense";
    public static final int AMBER = 0xFFB040;
    public static final int SEEN = 0xF0F0F0;
    private static final String CLUES_GLOW = "clues_glow";
    private static final String TEAM = Wayfarer.MODID + "_sense";
    static final String TRAIL_SIGHT = "trail_sight";
    private static final String WAY = Wayfarer.MODID + "_way";
    private static final float WAY_SCALE = 1.02f;
    private static final int GLOWING = 1 << 6;
    private static final int TICKS_PER_SECOND = 20;
    private static final Field FLAGS = flags();
    private static final Map<UUID, Sensing> sensing = new HashMap<>();

    private Sense() {}

    public record Payload() implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Wayfarer.id("ember_sense"));
        public static final StreamCodec<ByteBuf, Payload> STREAM_CODEC = StreamCodec.unit(new Payload());

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

    private record Sensing(List<Entity> glowing, long until, boolean held) {}

    public static boolean held(ServerPlayer player) {
        Sensing now = sensing.get(player.getUUID());
        return now != null && now.held();
    }

    public static void sense(ServerPlayer player) {
        Sensing now = sensing.get(player.getUUID());
        if (!Journey.awake(player.server) || now != null && now.held()) {
            return;
        }
        Record.event(player.server, "ember", player);
        if (now != null) {
            stop(player, now);
        }
        Tasks.did(player, "ember");
        Tasks.done(player, "ember");
        quiet(player).ifPresent(why -> player.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.GRAY), true));
        glow(player, true);
    }

    private static void glow(ServerPlayer player, boolean held) {
        Balance.Sense sense = Balance.get().sense();
        List<Entity> near = new ArrayList<>(player.serverLevel().getEntities((Entity) null, new AABB(player.blockPosition()).inflate(sense.reachBlocks()),
                entity -> !entity.getTags().contains(Steps.CLUE_BOX) && (entity.getTags().contains(Clues.SEEN) || Steps.lookable(player.server, entity))));
        near.addAll(sought(player, sense.reachBlocks()));
        near.forEach(entity -> glow(player, entity, true));
        if (!held && near.stream().anyMatch(entity -> !entity.getTags().contains(WAY))) {
            Firsts.first(player, Tasks.FIRST + TRAIL_SIGHT);
        }
        if (held && !near.isEmpty()) {
            Sounds.play(player, CLUES_GLOW);
        }
        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(near), true));
        sensing.put(player.getUUID(), new Sensing(near, player.serverLevel().getGameTime() + (long) sense.glowSeconds() * TICKS_PER_SECOND, held));
    }

    private static Optional<String> quiet(ServerPlayer player) {
        if (Caravan.homeward(player)) {
            return Optional.empty();
        }
        Optional<Engine.Active> tracked = Engine.tracked(player);
        if (tracked.isEmpty()) {
            return Optional.of("wayfarer.sense.no_road");
        }
        Optional<String> goal = Steps.goal(tracked.get());
        if (goal.isPresent() && Places.aim(Progress.of(player.server), player, goal.get()).isEmpty()) {
            return Optional.of("wayfarer.sense.listening");
        }
        return Optional.empty();
    }

    private static List<Entity> sought(ServerPlayer player, int reach) {
        Stream<BlockPos> way = Engine.tracked(player).filter(active -> active.step().kind().equals("reach") && active.step().spec().has("place"))
                .flatMap(active -> Progress.of(player.server).place(active.step().text("place")))
                .filter(at -> player.level().dimension() == Level.OVERWORLD && Wards.near(player.serverLevel(), at)).map(BlockPos::below).stream();
        Stream<BlockPos> stones = Stones.mine(player).stream().filter(stone -> stone.dimension() == player.level().dimension()).map(Stones.Stone::pos);
        Stream<BlockPos> bed = Caravan.lentBed(player).stream();
        return Stream.of(way, stones, bed).flatMap(found -> found).filter(at -> at.closerThan(player.blockPosition(), reach)).map(at -> {
            Entity shown = Clues.display(player.serverLevel(), at, WAY_SCALE, WAY);
            shown.addTag(WAY);
            shown.setGlowingTag(true);
            return shown;
        }).toList();
    }

    private static void stop(ServerPlayer player, Sensing ended) {
        sensing.remove(player.getUUID());
        ended.glowing().forEach(entity -> glow(player, entity, false));
        ended.glowing().stream().filter(entity -> entity.getTags().contains(WAY)).forEach(Entity::discard);
        player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team(ended.glowing())));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Sensing> entry : List.copyOf(sensing.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                sensing.remove(entry.getKey());
            } else if (player.serverLevel().getGameTime() >= entry.getValue().until()) {
                stop(player, entry.getValue());
            }
        }
        if (Engine.running(server) && Progress.of(server).gift(TRAIL_SIGHT)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!sensing.containsKey(player.getUUID()) && Journey.awake(server)) {
                    glow(player, false);
                }
            }
        }
    }

    private static PlayerTeam team(List<Entity> glowing) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), TEAM);
        team.setColor(ChatFormatting.GOLD);
        glowing.forEach(entity -> team.getPlayers().add(entity.getStringUUID()));
        return team;
    }

    @SuppressWarnings("unchecked")
    private static void glow(ServerPlayer player, Entity entity, boolean on) {
        try {
            EntityDataAccessor<Byte> flags = (EntityDataAccessor<Byte>) FLAGS.get(null);
            byte real = entity.getEntityData().get(flags);
            byte shown = (byte) (on ? real | GLOWING : real);
            player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), List.of(SynchedEntityData.DataValue.create(flags, shown))));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Field flags() {
        try {
            Field field = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
