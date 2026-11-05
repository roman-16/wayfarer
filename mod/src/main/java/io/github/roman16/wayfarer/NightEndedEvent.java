package io.github.roman16.wayfarer;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

public final class NightEndedEvent extends Event {
    private final MinecraftServer server;
    private final boolean reached;

    NightEndedEvent(MinecraftServer server, boolean reached) {
        this.server = server;
        this.reached = reached;
    }

    public MinecraftServer server() {
        return server;
    }

    public boolean reached() {
        return reached;
    }
}
