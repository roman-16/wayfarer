package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class Belongings {
    private static final int SPILL_BLOCKS = 2;

    private Belongings() {}

    public static void leaveTent(ServerPlayer player) {
        if (Tents.isTent(player.level().dimension())) {
            Tents.out(player);
        }
    }

    public static String pack(ServerPlayer player) {
        MinecraftServer server = player.server;
        List<String> done = new ArrayList<>();
        List<Joining.Pitched> pitched = TentPitch.mine(player);
        if (!pitched.isEmpty()) {
            TentPitch.recall(player, pitched, Joining.State.of(server).tents);
            done.add(count(pitched.size(), "tent") + " packed into the inventory");
        }
        int graves = 0;
        List<String> spilled = new ArrayList<>();
        for (Stones.Stone stone : Stones.mine(player)) {
            ServerLevel level = server.getLevel(stone.dimension());
            if (level == null) {
                continue;
            }
            BlockPos pos = stone.pos();
            level.getChunk(pos);
            if (!Graves.grave(level.getBlockState(pos))) {
                continue;
            }
            player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.gameMode.destroyBlock(pos);
            graves++;
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(SPILL_BLOCKS))
                    .forEach(item -> spilled.add(item.getItem().getCount() + " " + item.getItem().getHoverName().getString()));
        }
        if (graves > 0) {
            done.add(count(graves, "grave") + " taken back");
        }
        if (!spilled.isEmpty()) {
            done.add("left behind at a grave, with no room for them: " + String.join(", ", spilled));
        }
        List<net.blay09.mods.waystones.api.Waystone> own = WaystonesAPI.getAllWaystones(server).filter(waystone -> Waystone.mine(player, waystone)).toList();
        own.forEach(waystone -> Waystone.takeUp(player, waystone));
        if (!own.isEmpty()) {
            done.add(count(own.size(), "stone") + " taken up");
        }
        List.copyOf(WaystonesAPI.getActivatedWaystones(player)).forEach(waystone -> WaystonesAPI.deactivateWaystone(player, waystone));
        player.removeData(Wayfarer.LAST_FIRE);
        player.removeData(Wayfarer.STILLNESS);
        player.setRespawnPosition(Level.OVERWORLD, null, 0, false, false);
        return done.isEmpty() ? "nothing left standing" : String.join("; ", done);
    }

    private static String count(int count, String thing) {
        return count + " " + thing + (count == 1 ? "" : "s");
    }
}
