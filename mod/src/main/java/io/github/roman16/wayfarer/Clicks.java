package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Clicks {
    private Clicks() {}

    public static boolean answers(Player player, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !player.isSecondaryUseActive() && (state.getMenuProvider(level, pos) != null || level.getBlockEntity(pos) instanceof MenuProvider
                || state.is(BlockTags.BEDS) || state.is(BlockTags.BUTTONS) || state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.TRAPDOORS) || state.getBlock() instanceof BellBlock || state.getBlock() instanceof LeverBlock);
    }
}
