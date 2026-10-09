package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Memories;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class Flash {
    private static final int MILLIS_PER_TICK = 50;
    private static final int OPAQUE = 255;
    private static final int BRIGHT = 4;
    private static volatile boolean on;
    private static volatile long from;
    private static volatile int millis = 1;

    private Flash() {}

    public static void show(Memories.Flash flash) {
        on = flash.on();
        from = Util.getMillis();
        millis = Math.max(1, flash.ticks() * MILLIS_PER_TICK);
        if (!flash.on()) {
            Conversation.forget();
        }
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        float passed = Math.min(1, (Util.getMillis() - from) / (float) millis);
        float strength = on ? passed : 1 - passed;
        if (strength <= 0) {
            return;
        }
        int colour = Minecraft.getInstance().options.hideLightningFlash().get() ? Stamp.ramp("ash", 0) : Stamp.ramp("ember", BRIGHT);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Stamp.withAlpha(colour, Math.round(strength * OPAQUE)));
    }
}
