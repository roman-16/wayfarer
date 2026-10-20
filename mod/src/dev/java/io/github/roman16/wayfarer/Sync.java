package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Sync {
    private Sync() {}

    public record Ask(int id) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Wayfarer.id("driver_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Ask::new, Ask::id);

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    public record Done(int id) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Wayfarer.id("driver_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Done::new, Done::id);

        @Override
        public Type<Done> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (payload, context) -> context.reply(new Done(payload.id())));
        registrar.playToClient(Done.TYPE, Done.STREAM_CODEC, (payload, context) -> Driver.answered(payload.id()));
    }
}
