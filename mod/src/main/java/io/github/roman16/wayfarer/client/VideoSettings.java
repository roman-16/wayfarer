package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.util.EnumSet;
import java.util.Locale;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class VideoSettings implements ConfigEntryPoint {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int BLOCKS_PER_KILOMETRE = 1000;
    private static final String KEY = "wayfarer.video.";

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setNonTintedIcon(Wayfarer.id("textures/item/ember_lit.png"))
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(Stamp.rgb(Stamp.GOLD)))
                .registerFlagHook((flags, state) -> Minecraft.getInstance().tell(ConfigManager.CONFIG::resetAllOptionsFromBindings),
                        OptionFlag.REQUIRES_RENDERER_RELOAD.getId(), OptionFlag.REQUIRES_RENDERER_UPDATE.getId())
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable(KEY + "page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(builder.createEnumOption(Wayfarer.id("graphics"), Graphics.class)
                                        .setName(Component.translatable(KEY + "graphics"))
                                        .setTooltip(VideoSettings::explain)
                                        .setElementNameProvider(step -> Component.translatable(KEY + "graphics." + name(step)))
                                        .setAllowedValues(EnumSet.allOf(Graphics.class))
                                        .setDefaultValue(Graphics.HIGH)
                                        .setImpact(OptionImpact.HIGH)
                                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                                        .setStorageHandler(() -> Minecraft.getInstance().options.save())
                                        .setBinding(Graphics::choose, Graphics::current))
                                .addOption(builder.createBooleanOption(Wayfarer.id("shaders"))
                                        .setName(Component.translatable(KEY + "shaders"))
                                        .setTooltip(Component.translatable(KEY + "shaders.tooltip"))
                                        .setDefaultValue(true)
                                        .setImpact(OptionImpact.HIGH)
                                        .setFlags(OptionFlag.REQUIRES_RENDERER_UPDATE)
                                        .setStorageHandler(() -> Minecraft.getInstance().options.save())
                                        .setBinding(Graphics::shaders, Graphics::shaders))));
    }

    private static Component explain(Graphics step) {
        if (step == Graphics.CUSTOM) {
            return Component.translatable(KEY + "graphics.custom.tooltip");
        }
        Balance.Graphics steps = Balance.get().graphics();
        int far = steps.farViewChunks().get(step.ordinal()) * BLOCKS_PER_CHUNK;
        return Component.translatable(KEY + "graphics.tooltip", Component.translatable(KEY + "graphics." + name(step)),
                steps.renderChunks().get(step.ordinal()), Math.round(far / (float) BLOCKS_PER_KILOMETRE));
    }

    private static String name(Graphics step) {
        return step.name().toLowerCase(Locale.ROOT);
    }
}
