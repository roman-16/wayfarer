package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Record;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Frames {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int SLOWEST_SHARE = 100;
    private static final double WHOLE_FRAME_PERCENT = 100.0;
    private static final int FIRST_CAPACITY = 1024;
    private static final long MINUTE_NANOS = 60_000_000_000L;
    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;
    private static final String FAR_VIEW = "distanthorizons";
    private static final List<Count> counting = new ArrayList<>();
    private static final Count minute = new Count(true);
    private static long last;
    private static long minuteFrom = System.nanoTime();

    private Frames() {}

    public static Count count() {
        return new Count(false);
    }

    static void frame(RenderFrameEvent.Post event) {
        long now = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        boolean playing = minecraft.level != null && minecraft.screen == null;
        boolean focused = minecraft.isWindowActive();
        if (playing && last != 0) {
            double graphicsCardPercent = Math.min(minecraft.getGpuUtilization(), WHOLE_FRAME_PERCENT);
            for (Count count : counting) {
                if (focused || !count.focused) {
                    count.add(now - last, graphicsCardPercent);
                }
            }
        }
        last = playing ? now : 0;
    }

    static void report(ClientTickEvent.Post event) {
        long now = System.nanoTime();
        if (now - minuteFrom < MINUTE_NANOS) {
            return;
        }
        minuteFrom = now;
        Summary frames = minute.restart();
        boolean onBattery = Battery.powering();
        Battery.read();
        Minecraft minecraft = Minecraft.getInstance();
        if (frames.frames() == 0 || minecraft.getConnection() == null || !minecraft.getConnection().hasChannel(Record.Frames.TYPE)) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        PacketDistributor.sendToServer(new Record.Frames(
                (int) Math.round(frames.fps()),
                (int) Math.round(frames.low()),
                frames.graphicsCardPercent().map(percent -> (int) Math.round(percent)),
                onBattery,
                (int) Math.round(frames.worstMillis()),
                (int) Math.round(frames.seconds()),
                Graphics.current().name().toLowerCase(Locale.ROOT),
                Graphics.shaded(),
                ModList.get().isLoaded(FAR_VIEW) ? FarViewSetting.chunks() : 0,
                (int) ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE),
                (int) (runtime.maxMemory() / BYTES_PER_MEGABYTE)));
    }

    public record Summary(int frames, double seconds, double fps, double low, Optional<Double> graphicsCardPercent, double worstMillis) {}

    public static final class Count {
        private final boolean focused;
        private long[] took = new long[FIRST_CAPACITY];
        private int size;
        private double graphicsCardPercentSum;
        private int graphicsCardFrames;

        private Count(boolean focused) {
            this.focused = focused;
            counting.add(this);
        }

        private void add(long nanos, double graphicsCardPercent) {
            if (size == took.length) {
                took = Arrays.copyOf(took, size * 2);
            }
            took[size++] = nanos;
            if (graphicsCardPercent > 0) {
                graphicsCardPercentSum += graphicsCardPercent;
                graphicsCardFrames++;
            }
        }

        public Summary stop() {
            counting.remove(this);
            return summary();
        }

        private Summary restart() {
            Summary summary = summary();
            size = 0;
            graphicsCardPercentSum = 0;
            graphicsCardFrames = 0;
            return summary;
        }

        private Summary summary() {
            if (size == 0) {
                return new Summary(0, 0, 0, 0, Optional.empty(), 0);
            }
            long[] sorted = Arrays.copyOf(took, size);
            Arrays.sort(sorted);
            long total = Arrays.stream(sorted).sum();
            int slowest = Math.max(1, (size + SLOWEST_SHARE - 1) / SLOWEST_SHARE);
            long slow = Arrays.stream(sorted, size - slowest, size).sum();
            Optional<Double> graphicsCardPercent = graphicsCardFrames == 0 ? Optional.empty() : Optional.of(graphicsCardPercentSum / graphicsCardFrames);
            return new Summary(size, total / NANOS_PER_SECOND, size * NANOS_PER_SECOND / total, slowest * NANOS_PER_SECOND / slow, graphicsCardPercent,
                    sorted[size - 1] / NANOS_PER_MILLI);
        }
    }
}
