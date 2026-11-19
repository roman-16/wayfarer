package io.github.roman16.wayfarer;

import com.teamremastered.endrem.block.AncientPortalFrameEntity;
import io.github.roman16.wayfarer.story.Doors;
import io.github.roman16.wayfarer.story.Engine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Gate {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("endrem", "ancient_portal_frame");
    private static final int PORTAL_REACH = 4;

    private Gate() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !frame(event.getLevel().getBlockState(event.getPos()))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServerLevel level = player.serverLevel();
        List<ResourceLocation> claimed = Doors.frame(player.server);
        int needed = Shards.TWELVE;
        boolean blinding = Doors.blinding(player.server);
        if (claimed.size() < needed && !blinding) {
            player.displayClientMessage(
                    Component.translatable(Engine.running(player.server) ? "wayfarer.gate.closed" : "wayfarer.gate.incomplete", claimed.size(), needed)
                            .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        BlockPos center = event.getPos();
        if (guarded(level, center)) {
            ward(player);
            return;
        }
        Set<Item> set = new HashSet<>();
        Deque<BlockPos> empty = new ArrayDeque<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-PORTAL_REACH, 0, -PORTAL_REACH), center.offset(PORTAL_REACH, 0, PORTAL_REACH))) {
            BlockState state = level.getBlockState(pos);
            if (!frame(state)) {
                continue;
            }
            if (!state.getValue(BlockStateProperties.EYE)) {
                empty.add(pos.immutable());
            } else if (level.getBlockEntity(pos) instanceof AncientPortalFrameEntity entity && !entity.isEmpty()) {
                set.add(entity.getEyeItem());
            }
        }
        List<Item> brought = claimed.stream().map(Shards::item).filter(eye -> !set.contains(eye)).toList();
        if (blinding) {
            while (empty.size() > brought.size()) {
                BlockPos pos = empty.poll();
                level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.EYE, true), Block.UPDATE_ALL);
            }
        }
        for (Item eye : brought) {
            if (empty.isEmpty()) {
                break;
            }
            BlockPos pos = empty.poll();
            if (level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) {
                level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.EYE, true), Block.UPDATE_ALL);
            } else {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                eye.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(eye), hit));
            }
            set.add(eye);
        }
        open(level, center);
    }

    private static void open(ServerLevel level, BlockPos frame) {
        BlockPattern.BlockPatternMatch match = EndPortalFrameBlock.getOrCreatePortalShape().find(level, frame);
        if (match == null) {
            return;
        }
        BlockPos origin = match.getFrontTopLeft().offset(-3, 0, -3);
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                level.setBlock(origin.offset(dx, 0, dz), Blocks.END_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.globalLevelEvent(LevelEvent.SOUND_END_PORTAL_SPAWN, origin.offset(1, 0, 1), 0);
    }

    public static boolean guarded(ServerLevel level, BlockPos frame) {
        return !level.getEntitiesOfClass(Raider.class, new AABB(frame).inflate(Balance.get().gate().reachBlocks()), Raider::isAlive).isEmpty();
    }

    private static void ward(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("wayfarer.gate.ward").withStyle(ChatFormatting.RED), true);
    }

    private static boolean frame(BlockState state) {
        return state.is(Blocks.END_PORTAL_FRAME) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(FRAME);
    }
}
