package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class SpikePayloads {
    private SpikePayloads() {}

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("spike");
        registrar.playToClient(EndingSpike.Ending.TYPE, EndingSpike.Ending.STREAM_CODEC, (payload, context) -> EndingClient.show(payload.lines()));
        registrar.playToClient(EndingSpike.Skin.TYPE, EndingSpike.Skin.STREAM_CODEC,
                (payload, context) -> EndingClient.skin(payload.player(), payload.texture()));
        registrar.playToServer(GiftSpike.Flare.TYPE, GiftSpike.Flare.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                GiftSpike.flare(player);
            }
        });
    }
}
