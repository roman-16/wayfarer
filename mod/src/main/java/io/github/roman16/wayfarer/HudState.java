package io.github.roman16.wayfarer;

import java.util.List;

public final class HudState {
    public static volatile CompassPayload compass = CompassPayload.hidden(List.of());
    public static volatile GazePayload gaze = GazePayload.CLOSED;
    public static volatile NightPayload night = NightPayload.CALM;

    private HudState() {}
}
