package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Banners;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;

public final class HudState {
    public static final Queue<Banners.Payload> banners = new ConcurrentLinkedQueue<>();
    public static volatile CompassPayload compass = CompassPayload.hidden(List.of());
    public static volatile GazePayload gaze = GazePayload.CLOSED;
    public static volatile NightPayload night = NightPayload.CALM;
    public static volatile Tasks.Panel panel = Tasks.Panel.EMPTY;
    public static volatile Optional<BlockPos> smoke = Optional.empty();

    private HudState() {}
}
