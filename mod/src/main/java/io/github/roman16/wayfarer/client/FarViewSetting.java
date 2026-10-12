package io.github.roman16.wayfarer.client;

import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.Config;

final class FarViewSetting {
    private FarViewSetting() {}

    static int chunks() {
        return Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DISABLED ? 0
                : Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get();
    }

    static void set(int chunks, String threads) {
        Config.Client.threadPresetSetting.set(EDhApiThreadPreset.valueOf(threads));
        if (chunks == 0) {
            Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.DISABLED);
            return;
        }
        Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.DEFAULT);
        Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.set(chunks);
    }
}
