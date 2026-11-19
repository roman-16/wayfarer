package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class Scribes {
    static final String NAME = "scribes";
    static final String STONE = "scribes_stone";
    private static final String BUILT = "scribes_built";
    private static final HallSite SITE = new HallSite(NAME, Wayfarer.id("hall/cryptic"), NAME, () -> Balance.get().scribes().aheadBlocks());
    private static final Logger LOGGER = LogUtils.getLogger();

    private Scribes() {}

    static void find(MinecraftServer server) {
        SITE.find(server, Caravan.last(server));
    }

    static void unbuild(MinecraftServer server) {
        Progress progress = Progress.of(server);
        SITE.forget(progress);
        progress.places.remove(BUILT);
        progress.setDirty();
    }

    static void reset() {
        SITE.reset();
    }

    static boolean ready(MinecraftServer server, String name) {
        return Progress.of(server).place(BUILT).isPresent();
    }

    static void tick(MinecraftServer server) {
        SITE.tick(server);
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.entrance(progress).filter(top -> progress.place(BUILT).isEmpty() && HallSearch.loaded(level, top)).ifPresent(top -> {
            BlockPos stone = HallSearch.stone(level, SITE.low(progress).orElseThrow(), SITE.high(progress).orElseThrow()).orElse(top);
            progress.place(STONE, stone);
            progress.place(BUILT, top);
            progress.setDirty();
            HallSearch.release(level, top);
            Journal.syncAll(server);
            LOGGER.info("the Scribes' stone is at {}", stone);
        });
    }
}
