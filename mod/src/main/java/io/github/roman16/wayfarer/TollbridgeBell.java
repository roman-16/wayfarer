package io.github.roman16.wayfarer;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TollbridgeBell extends HorizontalDirectionalBlock {
    public static final MapCodec<TollbridgeBell> CODEC = simpleCodec(TollbridgeBell::new);
    public static final BooleanProperty FALLEN = BooleanProperty.create("fallen");

    private static final VoxelShape UPRIGHT = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(5, 2, 5, 11, 10, 11));
    private static final VoxelShape LYING_X = Block.box(0, 0, 4, 10, 8, 12);
    private static final VoxelShape LYING_Z = Block.box(4, 0, 0, 12, 8, 10);

    public TollbridgeBell(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FALLEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FALLEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(FALLEN)) {
            return UPRIGHT;
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? LYING_X : LYING_Z;
    }
}
