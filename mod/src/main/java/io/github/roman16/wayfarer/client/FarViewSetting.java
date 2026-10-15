package io.github.roman16.wayfarer.client;

import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiQualityPreset;
import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;

final class FarViewSetting {
    private FarViewSetting() {}

    static int chunks() {
        return Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DISABLED ? 0
                : Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get();
    }

    static String detail() {
        return Config.Client.qualityPresetSetting.get().name();
    }

    static void set(int chunks, String detail, String threads) {
        Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.DEFAULT);
        Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.set(chunks);
        Config.Client.qualityPresetSetting.set(EDhApiQualityPreset.valueOf(detail));
        Config.Client.threadPresetSetting.set(EDhApiThreadPreset.valueOf(threads));
    }
}
