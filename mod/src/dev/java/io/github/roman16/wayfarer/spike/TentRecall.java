package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.spike.JoinSpike.Pitched;
import io.github.roman16.wayfarer.spike.JoinSpike.Place;
import io.github.roman16.wayfarer.spike.JoinSpike.State;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import nomadictents.NTSavedData;
import nomadictents.registries.NTDataComponents;
import nomadictents.structure.TentPlacer;
import nomadictents.tileentity.TentDoorBlockEntity;
import nomadictents.util.Tent;
import nomadictents.util.TentSize;
import nomadictents.util.TentType;

final class TentRecall {
    private static final int AHEAD = 3;
    private static final BlockPos INSIDE_DOOR = Tent.calculatePos(0);

    private TentRecall() {}

    static String pitch(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();
        BlockPos door = player.blockPosition().relative(facing, AHEAD);
        ItemStack carried = player.getInventory().items.stream().filter(stack -> stack.has(NTDataComponents.TENT_ID.get())).findFirst()
                .orElse(ItemStack.EMPTY);
        Tent tent = carried.isEmpty() ? new Tent(NTSavedData.get(player.server).getNextTentId(), TentType.YURT, TentSize.SMALL) : Tent.from(carried);
        TentSize size = TentPlacer.getOverworldSize(tent.getSize());
        if (!TentPlacer.getInstance().placeTent(level, door, tent.getType(), size, facing, null)
                || !(level.getBlockEntity(door) instanceof TentDoorBlockEntity entrance)) {
            return "join failed: no room for %s's tent at %d %d %d".formatted(player.getScoreboardName(), door.getX(), door.getY(), door.getZ());
        }
        entrance.setTent(tent);
        entrance.setDirection(facing);
        entrance.setOwner(player.getUUID());
        entrance.setChanged();
        carried.shrink(1);
        return "join: %s's tent %d is pitched at %d %d %d".formatted(player.getScoreboardName(), tent.getId(), door.getX(), door.getY(), door.getZ());
    }

    static String enter(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension().location().getNamespace().equals("nomadictents")) {
            if (level.getBlockEntity(INSIDE_DOOR) instanceof TentDoorBlockEntity inside) {
                player.setPortalCooldown(0);
                inside.onEnter(player);
                return player.level() == level ? "join failed: %s cannot step out of the tent".formatted(player.getScoreboardName())
                        : "join: %s steps out of the tent".formatted(player.getScoreboardName());
            }
            return "join failed: no door inside %s".formatted(level.dimension().location());
        }
        List<Pitched> own = State.of(player.server).tents.stream().filter(tent -> tent.owner().equals(player.getUUID())).toList();
        for (Pitched tent : own) {
            if (level.getBlockEntity(tent.place().pos()) instanceof TentDoorBlockEntity entrance) {
                player.setPortalCooldown(0);
                entrance.onEnter(player);
                return "join: %s enters tent %d, inside %s".formatted(player.getScoreboardName(), tent.id(), player.level().dimension().location());
            }
        }
        return "join failed: %s has no tent near".formatted(player.getScoreboardName());
    }

    static void track(ServerPlayer player, State state) {
        ServerLevel level = player.serverLevel();
        ChunkPos at = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(at.x + dx, at.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TentDoorBlockEntity door && player.getUUID().equals(door.getOwner())
                            && state.tents.stream().noneMatch(tent -> tent.id() == door.getTent().getId())) {
                        state.tents.add(new Pitched(player.getUUID(), new Place(level.dimension(), door.getBlockPos()), door.getTent().getId()));
                        state.setDirty();
                        BlockPos pos = door.getBlockPos();
                        JoinSpike.say(player.server, "join: noted %s's tent %d at %d %d %d".formatted(player.getScoreboardName(), door.getTent().getId(),
                                pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
            }
        }
    }

    static void recall(ServerPlayer player, State state) {
        MinecraftServer server = player.server;
        for (Pitched tent : List.copyOf(state.tents)) {
            if (!tent.owner().equals(player.getUUID())) {
                continue;
            }
            ServerLevel level = server.getLevel(tent.place().dimension());
            BlockPos pos = tent.place().pos();
            boolean far = level != player.level() || pos.distSqr(player.blockPosition()) > (double) JoinSpike.AWAY * JoinSpike.AWAY;
            if (level == null || !far) {
                continue;
            }
            long started = System.nanoTime();
            level.getChunk(pos);
            long loading = (System.nanoTime() - started) / 1_000_000;
            state.tents.remove(tent);
            state.setDirty();
            if (!(level.getBlockEntity(pos) instanceof TentDoorBlockEntity door) || !player.getUUID().equals(door.getOwner())) {
                JoinSpike.say(server, "join: %s's tent %d is no longer at %d %d %d".formatted(player.getScoreboardName(), tent.id(), pos.getX(), pos.getY(), pos.getZ()));
                continue;
            }
            Tent pitched = door.getTent();
            TentPlacer.getInstance().removeTent(level, pos, pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()), door.getDirection());
            ItemStack item = pitched.asItem();
            boolean carried = player.getInventory().add(item);
            if (!carried) {
                player.drop(item, false);
            }
            int away = (int) Math.sqrt(pos.distSqr(player.blockPosition()));
            JoinSpike.say(server, "join: %s's tent %d, %d blocks away, is packed up %s; its inside stays %s; chunk loaded in %d ms".formatted(
                    player.getScoreboardName(), pitched.getId(), away, carried ? "into their inventory" : "at their feet",
                    NTSavedData.get(server).getOrCreateKey(server, pitched.getId()).location(), loading));
        }
    }
}
