package io.github.roman16.wayfarer;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.story.Talk;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Pretend {
    private static final ClientInformation DEFAULT = ClientInformation.createDefault();
    private static final ClientInformation SEEN = new ClientInformation(DEFAULT.language(), DEFAULT.viewDistance(), DEFAULT.chatVisibility(),
            DEFAULT.chatColors(), Arrays.stream(PlayerModelPart.values()).mapToInt(PlayerModelPart::getMask).reduce(0, (a, b) -> a | b),
            DEFAULT.mainHand(), DEFAULT.textFilteringEnabled(), DEFAULT.allowsListing());
    private static final String ANSWERS = " sees the answers: ";
    private static final String CHAT = " hears: ";
    private static final String OVERLAY = " sees above the hotbar: ";
    private static final String SUBTITLE = " sees the subtitle: ";
    private static final int OWNER = 4;
    private static final List<Silent> connections = new ArrayList<>();

    private Pretend() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("pretend")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.literal("join").then(name(Pretend::join)))
                        .then(Commands.literal("leave").then(name(Pretend::leave)))
                        .then(Commands.literal("respawn").then(name(Pretend::respawn)))
                        .then(Commands.literal("answer").then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("answer", IntegerArgumentType.integer(1))
                                        .executes(context -> answer(context.getSource(), StringArgumentType.getString(context, "name"),
                                                IntegerArgumentType.getInteger(context, "answer"))))))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        connections.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Silent connection : List.copyOf(connections)) {
            connection.tick();
            if (!connection.isConnected()) {
                connections.remove(connection);
            }
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.connection.getConnection() instanceof Silent) {
                player.serverLevel().getChunkSource().move(player);
                if (player.isChangingDimension()) {
                    player.hasChangedDimension();
                }
            }
        }
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name(Action action) {
        return Commands.argument("name", StringArgumentType.word())
                .executes(context -> action.run(context.getSource(), StringArgumentType.getString(context, "name")));
    }

    private static int join(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        if (!StringUtil.isValidPlayerName(name)) {
            source.sendFailure(Component.literal("pretend failed: " + name + " is not a valid player name: up to 16 letters, digits and underscores"));
            return 0;
        }
        if (server.getPlayerList().getPlayerByName(name) != null) {
            source.sendFailure(Component.literal("pretend failed: " + name + " is already online"));
            return 0;
        }
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, SEEN);
        Silent connection = new Silent(server, name);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        connections.add(connection);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, SEEN, false, ConnectionType.NEOFORGE);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        String dead = player.isDeadOrDying() ? ", dead since they left; `wayfarer pretend respawn %s` brings them back".formatted(name) : "";
        source.sendSuccess(() -> Component.literal("%s is online at %.1f %.1f %.1f in %s%s"
                .formatted(name, player.getX(), player.getY(), player.getZ(), player.level().dimension().location(), dead)), false);
        return 1;
    }

    private static int leave(CommandSourceStack source, String name) {
        ServerPlayer player = pretender(source, name);
        if (player == null) {
            return 0;
        }
        player.connection.disconnect(Component.literal("left"));
        source.sendSuccess(() -> Component.literal(name + " has left"), false);
        return 1;
    }

    private static int respawn(CommandSourceStack source, String name) {
        ServerPlayer player = pretender(source, name);
        if (player == null) {
            return 0;
        }
        if (player.isAlive()) {
            source.sendFailure(Component.literal("pretend failed: " + name + " is alive"));
            return 0;
        }
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        ServerPlayer respawned = source.getServer().getPlayerList().getPlayerByName(name);
        source.sendSuccess(() -> Component.literal("%s respawned at %.1f %.1f %.1f in %s"
                .formatted(name, respawned.getX(), respawned.getY(), respawned.getZ(), respawned.level().dimension().location())), false);
        return 1;
    }

    private static int answer(CommandSourceStack source, String name, int answer) {
        ServerPlayer player = pretender(source, name);
        if (player == null) {
            return 0;
        }
        if (Talk.open().filter(offer -> answer <= offer.choices().size()).isEmpty() || !Talk.answering(player)) {
            source.sendFailure(Component.literal("pretend failed: " + name + " has no answer " + answer + " to give"));
            return 0;
        }
        Talk.pick(player, answer - 1);
        source.sendSuccess(() -> Component.literal(name + " answered " + answer), false);
        return 1;
    }

    @Nullable
    private static ServerPlayer pretender(CommandSourceStack source, String name) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (player == null || !(player.connection.getConnection() instanceof Silent)) {
            source.sendFailure(Component.literal("pretend failed: " + name + " is not a pretend player online"));
            return null;
        }
        return player;
    }

    @FunctionalInterface
    private interface Action {
        int run(CommandSourceStack source, String name);
    }

    private static final class Silent extends Connection {
        private final MinecraftServer server;
        private final String name;

        private Silent(MinecraftServer server, String name) {
            super(PacketFlow.SERVERBOUND);
            this.server = server;
            this.name = name;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
            if (packet instanceof ClientboundKeepAlivePacket alive && getPacketListener() instanceof ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(new ServerboundKeepAlivePacket(alive.getId()));
            } else if (packet instanceof ClientboundSystemChatPacket chat) {
                hears(chat.content(), chat.overlay());
            } else if (packet instanceof ClientboundPlayerChatPacket chat) {
                Component content = chat.unsignedContent() != null ? chat.unsignedContent() : Component.literal(chat.body().content());
                hears(chat.chatType().decorate(content), false);
            } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
                hears(chat.chatType().decorate(chat.message()), false);
            } else if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof Talk.Subtitle line) {
                sees(SUBTITLE, line.by().isEmpty() ? line.text() : Component.literal("<" + line.by() + "> ").append(line.text()));
            } else if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof Talk.Answers offered
                    && !offered.choices().isEmpty()) {
                sees(ANSWERS, Component.literal(String.join(" | ", offered.choices())));
            }
            if (listener != null) {
                listener.onSuccess();
            }
        }

        private void hears(Component message, boolean overlay) {
            sees(overlay ? OVERLAY : CHAT, message);
        }

        private void sees(String where, Component message) {
            server.sendSystemMessage(Component.literal(name + where + message.getString()));
        }
    }
}
