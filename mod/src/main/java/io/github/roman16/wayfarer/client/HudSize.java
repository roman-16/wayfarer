package io.github.roman16.wayfarer.client;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public enum HudSize {
    SMALL(-1),
    NORMAL(0),
    LARGE(1);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.EnumValue<HudSize> CHOSEN = BUILDER.defineEnum("hud_size", SMALL);
    public static final ModConfigSpec SPEC = BUILDER.build();
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
        return SPEC.isLoaded() ? CHOSEN.get() : SMALL;
    }

    public static OptionInstance<HudSize> option() {
        return new OptionInstance<>(KEY, OptionInstance.noTooltip(),
                (caption, size) -> Component.translatable(KEY + "." + size.name().toLowerCase(Locale.ROOT)),
                new OptionInstance.Enum<>(Arrays.asList(values()), Codec.STRING.xmap(HudSize::valueOf, HudSize::name)),
                chosen(), size -> {
                    CHOSEN.set(size);
                    SPEC.save();
                });
    }
}
