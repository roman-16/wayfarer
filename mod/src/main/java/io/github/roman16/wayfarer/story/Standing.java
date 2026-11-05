package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

final class Standing {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_standing", Comparator.comparingLong(ChunkPos::toLong));
    private static final int REACH = 3;
    private static final int LOAD_RADIUS = 1;
    private static final int BEYOND_VIEW_BLOCKS = 64;
    private static final String PLACE = "place";
    private static final Set<String> CHECKED = new HashSet<>();

    record Witness(BlockPos centre, List<String> blocks) {}

    private Standing() {}

    static void keep(MinecraftServer server, String place) {
        Progress progress = Progress.of(server);
        progress.place(place).ifPresent(centre -> {
            progress.witnesses.put(place, new Witness(centre, blocks(Engine.overworld(server), centre)));
            progress.setDirty();
        });
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        if (progress.witnesses.isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        int reach = server.getPlayerList().getViewDistance() * 16 + BEYOND_VIEW_BLOCKS;
        for (Map.Entry<String, Witness> entry : List.copyOf(progress.witnesses.entrySet())) {
            BlockPos centre = entry.getValue().centre();
            ChunkPos chunk = new ChunkPos(centre);
            if (behind(server, entry.getKey())) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
                progress.witnesses.remove(entry.getKey());
                progress.setDirty();
                continue;
            }
            if (CHECKED.contains(entry.getKey())) {
                continue;
            }
            if (Engine.players(server).stream().noneMatch(player -> near(player, level, centre, reach))) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
                continue;
            }
            level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
            if (!loaded(level, centre)) {
                continue;
            }
            level.getChunkSource().removeRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
            CHECKED.add(entry.getKey());
            List<String> now = blocks(level, centre);
            List<String> then = entry.getValue().blocks();
            long same = 0;
            for (int index = 0; index < then.size(); index++) {
                if (then.get(index).equals(now.get(index))) {
                    same++;
                }
            }
            if (same * 2 < then.size()) {
                LOGGER.warn("the story place {} at {} is gone, {} of {} of its blocks stand; it goes up again", entry.getKey(), centre, same, then.size());
                Places.rebuild(server, entry.getKey());
            }
        }
    }

    static void reset() {
        CHECKED.clear();
    }

    private static boolean behind(MinecraftServer server, String place) {
        Progress progress = Progress.of(server);
        List<Story.Quest> using = Story.get().quests().stream()
                .filter(quest -> quest.steps().stream().anyMatch(step -> step.spec().has(PLACE) && step.text(PLACE).equals(place))).toList();
        return !using.isEmpty() && using.stream().allMatch(quest -> progress.done(quest.id()));
    }

    private static boolean near(ServerPlayer player, ServerLevel level, BlockPos centre, int reach) {
        return player.level() == level && Steps.horizontal(player.blockPosition(), centre) <= (double) reach * reach;
    }

    private static boolean loaded(ServerLevel level, BlockPos centre) {
        for (int x = (centre.getX() - REACH) >> 4; x <= (centre.getX() + REACH) >> 4; x++) {
            for (int z = (centre.getZ() - REACH) >> 4; z <= (centre.getZ() + REACH) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> blocks(ServerLevel level, BlockPos centre) {
        List<String> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-REACH, -REACH, -REACH), centre.offset(REACH, REACH, REACH))) {
            blocks.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
        }
        return blocks;
    }
}
