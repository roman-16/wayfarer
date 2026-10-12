package io.github.roman16.wayfarer;

import com.seibel.distanthorizons.core.config.Config;
import java.util.Collections;

final class FarViewServer {
    private FarViewServer() {}

    static void limit() {
        int farthest = Collections.max(Balance.get().graphics().farViewChunks());
        Config.Server.maxGenerationRequestDistance.set(farthest);
        Config.Server.maxSyncOnLoadRequestDistance.set(farthest);
    }
}
