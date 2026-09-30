package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public final class CompassEvent extends Event {
    private final ServerPlayer player;
    private final List<CompassPayload.Marker> markers;
    private boolean shown;
    private Compass.Aim aim;

    CompassEvent(ServerPlayer player, boolean shown, Compass.Aim aim, List<CompassPayload.Marker> markers) {
        this.player = player;
        this.shown = shown;
        this.aim = aim;
        this.markers = markers;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean shown() {
        return shown;
    }

    public void show() {
        shown = true;
    }

    public Compass.Aim aim() {
        return aim;
    }

    public void aim(Compass.Aim aim) {
        this.aim = aim;
    }

    public List<CompassPayload.Marker> markers() {
        return markers;
    }
}
