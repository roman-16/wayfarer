package io.github.roman16.wayfarer;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.internal.events.HudIconRenderEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import io.github.roman16.wayfarer.client.VoiceHud;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@ForgeVoicechatPlugin
public final class Voices implements VoicechatPlugin {
    private static final Map<UUID, OpusDecoder> DECODERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> HEARD = new ConcurrentHashMap<>();
    @Nullable
    private static VoicechatApi api;

    @Override
    public String getPluginId() {
        return Wayfarer.MODID;
    }

    @Override
    public void initialize(VoicechatApi voicechat) {
        api = voicechat;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, Voices::heard);
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> forget(event.getPlayerUuid()));
        if (FMLEnvironment.dist.isClient()) {
            registration.registerEvent(HudIconRenderEvent.class, VoiceHud::render);
        }
    }

    private static void heard(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        byte[] sound = event.getPacket().getOpusEncodedData();
        if (api == null || connection == null || sound.length == 0 || event.getPacket().isWhispering() || heardOnlyByGroup(connection.getGroup())
                || !(connection.getPlayer().getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        Balance.Voice balance = Balance.get().voice();
        int now = player.server.getTickCount();
        Integer last = HEARD.get(player.getUUID());
        short[] samples = DECODERS.computeIfAbsent(player.getUUID(), any -> api.createDecoder()).decode(sound);
        if (last != null && now - last < balance.vibrationTicks() || decibels(samples) < balance.quietestDecibels()) {
            return;
        }
        HEARD.put(player.getUUID(), now);
        player.server.execute(() -> {
            if (!player.isRemoved() && !player.isSpectator()) {
                player.level().gameEvent(Wayfarer.VOICE, player.position(), GameEvent.Context.of(player));
            }
        });
    }

    private static boolean heardOnlyByGroup(@Nullable Group group) {
        return group != null && group.getType() != Group.Type.OPEN;
    }

    private static double decibels(short[] samples) {
        if (samples.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double sum = 0;
        for (short sample : samples) {
            double level = sample / (double) Short.MAX_VALUE;
            sum += level * level;
        }
        return 10 * Math.log10(sum / samples.length);
    }

    private static void forget(UUID player) {
        OpusDecoder decoder = DECODERS.remove(player);
        if (decoder != null) {
            decoder.close();
        }
        HEARD.remove(player);
    }
}
