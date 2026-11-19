package io.github.roman16.wayfarer.client;

import net.neoforged.neoforge.common.ModConfigSpec;

final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue AUTO_SORT = BUILDER.define("auto_sort", true);
    static final ModConfigSpec.EnumValue<HudSize> HUD_SIZE = BUILDER.defineEnum("hud_size", HudSize.SMALL);
    static final ModConfigSpec.ConfigValue<String> VOICE = BUILDER.define("voice", "");
    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}
