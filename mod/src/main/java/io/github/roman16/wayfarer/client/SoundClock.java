package io.github.roman16.wayfarer.client;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class SoundClock {
    private static final int TICK_MILLIS = 50;
    private static final int STALLED_MILLIS = TICK_MILLIS * 2;
    private static final AtomicBoolean asked = new AtomicBoolean();
    private static volatile long ticked = Util.getMillis();

    private SoundClock() {}

    static void start() {
        Thread watch = new Thread(SoundClock::watch, "Wayfarer sound clock");
        watch.setDaemon(true);
        watch.start();
    }

    static void ticked() {
        ticked = Util.getMillis();
    }

    public static void frame() {
        if (Util.getMillis() - ticked < TICK_MILLIS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().tick(minecraft.level != null && minecraft.isPaused());
        Speech.tick(minecraft);
        ticked();
    }

    private static void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(TICK_MILLIS);
            } catch (InterruptedException exception) {
                return;
            }
            if (Util.getMillis() - ticked >= STALLED_MILLIS && asked.compareAndSet(false, true)) {
                Minecraft.getInstance().tell(() -> {
                    asked.set(false);
                    frame();
                });
            }
        }
    }
}
