package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Sorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AutoSort {
    private static final String KEY = "wayfarer.options.auto_sort";

    private AutoSort() {}

    private static boolean chosen() {
        return !ClientConfig.SPEC.isLoaded() || ClientConfig.AUTO_SORT.get();
    }

    public static OptionInstance<Boolean> option() {
        return OptionInstance.createBoolean(KEY, chosen(), on -> {
            ClientConfig.AUTO_SORT.set(on);
            ClientConfig.SPEC.save();
            tell();
        });
    }

    static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        tell();
    }

    private static void tell() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(Sorting.Auto.TYPE)) {
            PacketDistributor.sendToServer(new Sorting.Auto(chosen()));
        }
    }
}
