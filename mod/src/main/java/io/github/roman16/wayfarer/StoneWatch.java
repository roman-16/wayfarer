package io.github.roman16.wayfarer;

import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.corelib.death.PlayerDeathEvent;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class StoneWatch {
    private static final int CHECK_TICKS = 20;

    private StoneWatch() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGrave(PlayerDeathEvent event) {
        Death death = event.getDeath();
        ServerPlayer player = event.getPlayer();
        ServerLevel level = player.serverLevel();
        GraveStoneTileEntity grave = find(level, death.getBlockPos(), death.getId());
        if (grave == null || things(death) == 0) {
            return;
        }
        Stones stones = Stones.of(player.server);
        stones.all.put(death.getId(), new Stones.Stone(player.getUUID(), level.dimension(), grave.getBlockPos(), things(death)));
        stones.setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        Stones stones = Stones.of(server);
        for (Map.Entry<UUID, Stones.Stone> entry : List.copyOf(stones.all.entrySet())) {
            Stones.Stone stone = entry.getValue();
            ServerLevel level = server.getLevel(stone.dimension());
            if (level == null || !level.isLoaded(stone.pos())) {
                continue;
            }
            if (!(level.getBlockEntity(stone.pos()) instanceof GraveStoneTileEntity grave) || !grave.getDeath().getId().equals(entry.getKey())
                    || things(grave.getDeath()) == 0) {
                stones.all.remove(entry.getKey());
                stones.setDirty();
            } else if (things(grave.getDeath()) != stone.things()) {
                stones.all.put(entry.getKey(), new Stones.Stone(stone.player(), stone.dimension(), stone.pos(), things(grave.getDeath())));
                stones.setDirty();
            }
        }
    }

    private static GraveStoneTileEntity find(ServerLevel level, BlockPos near, UUID id) {
        ChunkPos centre = new ChunkPos(near);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (BlockEntity entity : level.getChunk(centre.x + dx, centre.z + dz).getBlockEntities().values()) {
                    if (entity instanceof GraveStoneTileEntity grave && grave.getDeath().getId().equals(id)) {
                        return grave;
                    }
                }
            }
        }
        return null;
    }

    private static int things(Death death) {
        return (int) death.getAllItems().stream().filter(stack -> !stack.isEmpty()).count();
    }
}
