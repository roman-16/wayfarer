package io.github.roman16.wayfarer.client;

import com.mojang.serialization.Codec;
import io.github.roman16.wayfarer.Narration;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public enum YourVoice {
    MAN,
    WOMAN,
    SILENT;

    private static final String KEY = "wayfarer.options.voice";

    public static YourVoice chosen() {
        if (answered()) {
            return valueOf(ClientConfig.VOICE.get().toUpperCase(Locale.ROOT));
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getSkinManager().getInsecureSkin(minecraft.getGameProfile()).model() == PlayerSkin.Model.SLIM ? WOMAN : MAN;
    }

    static boolean answered() {
        return ClientConfig.SPEC.isLoaded() && !ClientConfig.VOICE.get().isEmpty();
    }

    static void choose(YourVoice voice) {
        ClientConfig.VOICE.set(voice.id());
        ClientConfig.SPEC.save();
        tell();
    }

    public String id() {
        return this == SILENT ? Narration.SILENT : name().toLowerCase(Locale.ROOT);
    }

    Component label() {
        return Component.translatable(KEY + "." + id());
    }

    public static OptionInstance<YourVoice> option() {
        return new OptionInstance<>(KEY, OptionInstance.noTooltip(), (caption, voice) -> voice.label(),
                new OptionInstance.Enum<>(Arrays.asList(values()), Codec.STRING.xmap(YourVoice::valueOf, YourVoice::name)), chosen(), YourVoice::choose);
    }

    static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        tell();
    }

    private static void tell() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(Narration.Chosen.TYPE)) {
            PacketDistributor.sendToServer(new Narration.Chosen(chosen().id()));
        }
    }
}
