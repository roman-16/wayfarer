package io.github.roman16.wayfarer;

public final class HudState {
    public static volatile CompassPayload compass = CompassPayload.silent(0);
    public static volatile GazePayload gaze = GazePayload.CLOSED;

    private HudState() {}
}
