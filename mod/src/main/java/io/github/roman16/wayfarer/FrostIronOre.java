package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class FrostIronOre extends Block {
    public FrostIronOre(Properties properties) {
        super(properties);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return player.hasCorrectToolForDrops(state, player.level(), pos) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && event.getEntity() instanceof ServerPlayer player
                && state.is(Wayfarer.FROST_IRON_ORE) && !player.isCreative() && !player.hasCorrectToolForDrops(state, player.level(), event.getPos())) {
            player.displayClientMessage(Component.translatable("wayfarer.ore.frost_iron.tool").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
