package io.github.roman16.wayfarer.client;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public enum HudSize {
    SMALL(-1),
    NORMAL(0),
    LARGE(1);

    private static final String KEY = "wayfarer.options.hud_size";
    private static final int LEAST_PIXELS = 2;

    private final int step;

    HudSize(int step) {
        this.step = step;
    }

    public static float scale() {
        int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
        int pixels = Math.max(Math.min(guiScale, LEAST_PIXELS), guiScale + chosen().step);
        return pixels / (float) guiScale;
    }

    private static HudSize chosen() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.HUD_SIZE.get() : SMALL;
    }

    public static OptionInstance<HudSize> option() {
        return new OptionInstance<>(KEY, OptionInstance.noTooltip(),
                (caption, size) -> Component.translatable(KEY + "." + size.name().toLowerCase(Locale.ROOT)),
                new OptionInstance.Enum<>(Arrays.asList(values()), Codec.STRING.xmap(HudSize::valueOf, HudSize::name)),
                chosen(), size -> {
                    ClientConfig.HUD_SIZE.set(size);
                    ClientConfig.SPEC.save();
                });
    }
}
