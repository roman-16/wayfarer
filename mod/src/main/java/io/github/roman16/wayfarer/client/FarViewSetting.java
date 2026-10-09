package io.github.roman16.wayfarer.client;

import com.seibel.distanthorizons.core.config.Config;

final class FarViewSetting {
    private FarViewSetting() {}

    static int chunks() {
        return Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get();
    }

    static void set(int chunks) {
        Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.set(chunks);
    }
}
