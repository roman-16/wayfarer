package io.github.roman16.wayfarer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import noobanidus.mods.lootr.common.block.entity.BlockEntityTicker;

public final class Loot {
    private static final int CHECK_TICKS = 20;
    private static final int REACH_CHUNKS = 3;

    private Loot() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ChunkPos centre = player.chunkPosition();
        for (int x = centre.x - REACH_CHUNKS; x <= centre.x + REACH_CHUNKS; x++) {
            for (int z = centre.z - REACH_CHUNKS; z <= centre.z + REACH_CHUNKS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity instanceof RandomizableContainer container && container.getLootTable() != null) {
                            BlockEntityTicker.addEntity(entity, level, chunk.getPos());
                        }
                    }
                }
            }
        }
    }
}
