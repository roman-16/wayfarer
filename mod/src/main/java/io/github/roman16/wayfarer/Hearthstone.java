package io.github.roman16.wayfarer;

import com.mojang.serialization.MapCodec;
import io.github.roman16.wayfarer.story.Memories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class Hearthstone extends Block {
    public static final MapCodec<Hearthstone> CODEC = simpleCodec(Hearthstone::new);
    public static final BooleanProperty MARKED = BooleanProperty.create("marked");

    public Hearthstone(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MARKED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MARKED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer server && Memories.touch(server, pos, stack)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return player instanceof ServerPlayer server && Memories.touch(server, pos, ItemStack.EMPTY) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
