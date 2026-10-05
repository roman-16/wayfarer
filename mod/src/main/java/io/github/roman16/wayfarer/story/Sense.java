package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Sense {
    public static final String KEY = "key.wayfarer.ember_sense";
    public static final int AMBER = 0xFFB040;
    private static final String TEAM = Wayfarer.MODID + "_sense";
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

    private record Sensing(List<Entity> glowing, long until) {}

    public static void sense(ServerPlayer player) {
        if (!Journey.awake(player.server) || sensing.containsKey(player.getUUID())) {
            return;
        }
        Tasks.did(player, "ember");
        Balance.Sense sense = Balance.INSTANCE.sense();
        List<Entity> near = player.serverLevel().getEntities((Entity) null, new AABB(player.blockPosition()).inflate(sense.reachBlocks()),
                entity -> entity.getTags().stream().anyMatch(tag -> tag.startsWith(Steps.CLUE)) && !entity.getTags().contains(Steps.CLUE_BOX));
        near.forEach(entity -> glow(player, entity, true));
        player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(near), true));
        sensing.put(player.getUUID(), new Sensing(near, player.serverLevel().getGameTime() + (long) sense.glowSeconds() * TICKS_PER_SECOND));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Sensing> entry : List.copyOf(sensing.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.serverLevel().getGameTime() >= entry.getValue().until()) {
                sensing.remove(entry.getKey());
                if (player != null) {
                    entry.getValue().glowing().forEach(entity -> glow(player, entity, false));
                    player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team(entry.getValue().glowing())));
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
