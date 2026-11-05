package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Cheat {
    private static final int OPERATOR = 4;

    private Cheat() {}

    public record Run(String command, boolean kept) implements CustomPacketPayload {
        public static final Type<Run> TYPE = new Type<>(Wayfarer.id("driver_cheat"));
        public static final StreamCodec<ByteBuf, Run> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Run::command, ByteBufCodecs.BOOL, Run::kept,
                Run::new);

        @Override
        public Type<Run> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Run.TYPE, Run.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (payload.kept()) {
                Record.event(player.server, "workaround", player, line -> line.addProperty("command", payload.command()));
            }
            player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(OPERATOR), payload.command());
        });
    }
}
