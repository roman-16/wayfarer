package io.github.roman16.wayfarer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.api.WaystonesAPI;
import io.github.roman16.wayfarer.story.Deeds;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Waystone {
    private static final String NAMESPACE = "waystones";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<UUID, BlockPos> placing = new HashMap<>();
    private static final Map<UUID, Vec3> leaving = new HashMap<>();

    private Waystone() {}

    public static void listen() {
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, Waystone::onLeave);
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Post.class, Waystone::onArrive);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.is(BuiltInRegistries.ITEM.get(OwnStone.STONE)) && !Clicks.answers(player, event.getLevel(), event.getPos())) {
            if (!OwnStone.owns(player, held)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("wayfarer.stone.not_yours").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return;
            }
            if (Tents.isTent(player.level().dimension())) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("wayfarer.stone.tent").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return;
            }
            placing.put(player.getUUID(), event.getPos());
            return;
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            own(player, event.getPos()).ifPresent(waystone -> {
                event.setCanceled(true);
                takeUp(player, waystone);
            });
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getNamespace().equals(NAMESPACE)) {
            return;
        }
        event.setCanceled(true);
        own(player, event.getPos()).ifPresentOrElse(waystone -> takeUp(player, waystone),
                () -> player.displayClientMessage(Component.translatable("wayfarer.stone.world").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, BlockPos> entry : Map.copyOf(placing).entrySet()) {
            placing.remove(entry.getKey());
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            for (BlockPos pos : new BlockPos[] {entry.getValue().above(), entry.getValue(), entry.getValue().above(2)}) {
                Optional<net.blay09.mods.waystones.api.Waystone> placed = WaystonesAPI.getWaystoneAt(player.serverLevel(), pos);
                if (placed.isPresent() && mine(player, placed.get())) {
                    if (placed.get() instanceof MutableWaystone named) {
                        named.setName(Component.translatable("wayfarer.stone.named", player.getDisplayName()));
                    }
                    WaystonesAPI.activateWaystone(player, placed.get());
                    Firsts.first(player, "task.stone_set");
                    break;
                }
            }
        }
    }

    static boolean mine(ServerPlayer player, net.blay09.mods.waystones.api.Waystone waystone) {
        return waystone.getOrigin() == WaystoneOrigin.PLAYER && player.getUUID().equals(waystone.getOwnerUid());
    }

    private static Optional<net.blay09.mods.waystones.api.Waystone> own(ServerPlayer player, BlockPos pos) {
        for (BlockPos part : new BlockPos[] {pos, pos.below(), pos.above()}) {
            Optional<net.blay09.mods.waystones.api.Waystone> found = WaystonesAPI.getWaystoneAt(player.serverLevel(), part)
                    .filter(waystone -> mine(player, waystone));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static void onLeave(WaystoneTeleportEvent.Pre event) {
        if (event.getContext().getEntity() instanceof ServerPlayer player) {
            leaving.put(player.getUUID(), ground(player));
        }
    }

    private static Vec3 ground(ServerPlayer player) {
        return Tents.outside(player).map(Tents.Outside::at).orElse(player.position());
    }

    private static void onArrive(WaystoneTeleportEvent.Post event) {
        if (!(event.getContext().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Deeds.folded(player);
        Vec3 from = leaving.remove(player.getUUID());
        Vec3 to = ground(player);
        Record.event(player.server, "fold", player, line -> {
            if (from != null) {
                line.addProperty("blocks", (int) from.distanceTo(to));
            }
        });
        if (from != null) {
            int cost = (int) Math.round(from.distanceTo(to) / Balance.get().waystones().blocksPerFood());
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - cost));
            player.getFoodData().setSaturation(0);
            if (cost > 0) {
                player.displayClientMessage(Component.translatable("wayfarer.stone.hungry").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
        }
        net.blay09.mods.waystones.api.Waystone target = event.getContext().getTargetWaystone();
        if (mine(player, target)) {
            takeUp(player, target);
        }
    }

    static void takeUp(ServerPlayer player, net.blay09.mods.waystones.api.Waystone waystone) {
        ServerLevel level = player.server.getLevel(waystone.getDimension());
        if (level == null) {
            return;
        }
        BlockPos pos = waystone.getPos();
        for (BlockPos part : new BlockPos[] {pos.above(), pos, pos.below()}) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(part).getBlock()).getNamespace().equals(NAMESPACE)) {
                level.setBlock(part, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        WaystonesAPI.removeWaystoneFromDatabase(player.server, waystone);
        Handover.give(player, OwnStone.stone(player));
        player.displayClientMessage(Component.translatable("wayfarer.stone.taken_up").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }
}
