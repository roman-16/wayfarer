package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

final class HallSite {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FAILED = "_failed";
    private final String prefix;
    private final ResourceLocation shrine;
    private final String goal;
    private final int act;
    @Nullable
    private CompletableFuture<Optional<BlockPos>> searching;

    HallSite(String prefix, ResourceLocation shrine, String goal, int act) {
        this.prefix = prefix;
        this.shrine = shrine;
        this.goal = goal;
        this.act = act;
    }

    Optional<BlockPos> entrance(Progress progress) {
        return progress.place(prefix + "_entrance");
    }

    Optional<BlockPos> low(Progress progress) {
        return progress.place(prefix + "_box_low");
    }

    Optional<BlockPos> high(Progress progress) {
        return progress.place(prefix + "_box_high");
    }

    void find(MinecraftServer server, BlockPos from) {
        Progress progress = Progress.of(server);
        if (entrance(progress).isPresent() || searching != null || progress.place(prefix + "_raise").isPresent()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Optional<Halls.Found> near = Halls.nearest(level, shrine, from);
        if (near.isPresent()) {
            record(server, near.get());
            return;
        }
        LOGGER.info("no {} grows within reach of {}; the quest looks for fitting ground to raise one", shrine, from.toShortString());
        Balance.World world = Balance.get().world();
        searching = Halls.ground(level, shrine, from, act == 2 ? world.homeBlocks() + world.seaBlocks() - world.shoreBlocks() : Balance.get().shrines().actThreeFromBlocks(),
                act == 2 ? Balance.get().shrines().actThreeFromBlocks() : Balance.get().strongholds().radiusBlocks() + world.lastSeaPastRingBlocks(),
                progress.place(prefix + FAILED));
    }

    void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        if (searching != null && searching.isDone()) {
            Optional<BlockPos> site;
            try {
                site = searching.join();
            } catch (CompletionException failure) {
                LOGGER.error("the search for ground to raise {} failed: {}", shrine, failure.getCause().toString());
                searching = null;
                return;
            }
            searching = null;
            if (site.isEmpty()) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("the search for ground to raise " + shrine + " failed: no fitting land within reach"),
                        false);
                return;
            }
            progress.place(prefix + "_raise", site.get());
            progress.place(goal, site.get());
            progress.setDirty();
            LOGGER.info("{} will be raised at {}", shrine, site.get());
        }
        progress.place(prefix + "_raise").filter(site -> entrance(progress).isEmpty() && Halls.loaded(level, site)).ifPresent(site -> {
            Halls.release(level, site);
            try {
                record(server, Halls.raise(level, shrine, site));
            } catch (IllegalStateException failure) {
                LOGGER.warn("{}; the quest looks for other ground", failure.getMessage());
                progress.place(prefix + FAILED, site);
                progress.places.remove(prefix + "_raise");
                progress.setDirty();
                find(server, site);
            }
        });
    }

    void reset() {
        searching = null;
    }

    private void record(MinecraftServer server, Halls.Found found) {
        Progress progress = Progress.of(server);
        BoundingBox box = found.box();
        progress.place(prefix + "_entrance", found.entrance());
        progress.place(goal, found.entrance());
        progress.place(prefix + "_box_low", new BlockPos(box.minX(), box.minY(), box.minZ()));
        progress.place(prefix + "_box_high", new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
        progress.setDirty();
        LOGGER.info("{} is at {}, its way down at {}", shrine, box, found.entrance());
        Journal.syncAll(server);
    }
}
