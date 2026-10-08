package io.github.roman16.wayfarer.client;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public enum TalkSize {
    SMALL(-1),
    NORMAL(0),
    LARGE(1);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.EnumValue<TalkSize> CHOSEN = BUILDER.defineEnum("talk_size", NORMAL);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private static final String KEY = "wayfarer.options.talk_size";

    private final int step;

    TalkSize(int step) {
        this.step = step;
    }

    static float scale(int guiScale) {
        int pixels = Math.max(1, guiScale + (SPEC.isLoaded() ? CHOSEN.get() : NORMAL).step);
        return pixels / (float) guiScale;
    }

    public static OptionInstance<TalkSize> option() {
        return new OptionInstance<>(KEY, OptionInstance.noTooltip(),
                (caption, size) -> Component.translatable(KEY + "." + size.name().toLowerCase(Locale.ROOT)),
                new OptionInstance.Enum<>(Arrays.asList(values()), Codec.STRING.xmap(TalkSize::valueOf, TalkSize::name)),
                SPEC.isLoaded() ? CHOSEN.get() : NORMAL, size -> {
                    CHOSEN.set(size);
                    SPEC.save();
                });
    }
}
