package io.github.roman16.wayfarer;

import com.teamremastered.endrem.block.AncientPortalFrameEntity;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        List<ResourceLocation> claimed = Journey.claimed(player);
        int needed = Shrines.count(level);
        if (claimed.size() < needed) {
            player.displayClientMessage(
                    Component.translatable("wayfarer.gate.incomplete", claimed.size(), needed).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Set<Item> set = new HashSet<>();
        Deque<BlockPos> empty = new ArrayDeque<>();
        BlockPos center = event.getPos();
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
        for (ResourceLocation shrine : claimed) {
            Item eye = Shrines.eye(shrine);
            if (empty.isEmpty() || set.contains(eye)) {
                continue;
            }
            BlockPos pos = empty.poll();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            eye.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(eye), hit));
            set.add(eye);
        }
    }

    private static boolean frame(BlockState state) {
        return state.is(Blocks.END_PORTAL_FRAME) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(FRAME);
    }
}
