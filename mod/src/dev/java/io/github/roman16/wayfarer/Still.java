package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.mixin.dev.ParticleEngineAccessor;
import io.github.roman16.wayfarer.mixin.dev.TextureAtlasAccessor;
import io.github.roman16.wayfarer.mixin.dev.TextureManagerAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.server.IntegratedServer;

public final class Still {
    private Still() {}

    public static boolean held() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.tickRateManager().isFrozen();
    }

    static boolean standing(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        return held() && !minecraft.level.tickRateManager().isSteppingForward() && (server == null || !server.tickRateManager().isSteppingForward());
    }

    static String hold(Minecraft minecraft) {
        List<TextureAtlas> atlases = ((TextureManagerAccessor) minecraft.getTextureManager()).wayfarer$textures().values().stream()
                .filter(TextureAtlas.class::isInstance).map(TextureAtlas.class::cast).toList();
        int rewound = atlases.stream().mapToInt(Still::rewind).sum();
        ((ParticleEngineAccessor) minecraft.particleEngine).wayfarer$clear();
        return "still: " + rewound + " animated textures on their first frame, no particles";
    }

    private static int rewind(TextureAtlas atlas) {
        TextureAtlasAccessor access = (TextureAtlasAccessor) atlas;
        access.wayfarer$animated().forEach(TextureAtlasSprite.Ticker::close);
        atlas.bind();
        List<TextureAtlasSprite.Ticker> tickers = new ArrayList<>();
        for (TextureAtlasSprite sprite : access.wayfarer$sprites().values()) {
            TextureAtlasSprite.Ticker ticker = sprite.createTicker();
            if (ticker != null) {
                sprite.uploadFirstFrame();
                tickers.add(ticker);
            }
        }
        access.wayfarer$animated(List.copyOf(tickers));
        return tickers.size();
    }
}
