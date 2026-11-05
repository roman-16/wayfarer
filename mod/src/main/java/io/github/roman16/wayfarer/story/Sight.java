package io.github.roman16.wayfarer.story;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class Sight {
    private static final double FARTHEST = 128;

    private Sight() {}

    public static boolean clear(Level level, Vec3 from, Vec3 to) {
        return from.distanceToSqr(to) <= FARTHEST * FARTHEST && hit(level, from, to).isEmpty();
    }

    public static Optional<Vec3> hit(Level level, Vec3 from, Vec3 to) {
        return Optional.ofNullable(BlockGetter.traverseBlocks(from, to, level, (world, pos) -> blocks(world, from, to, pos), world -> null));
    }

    public static boolean sees(ServerPlayer player, Vec3 at) {
        return clear(player.level(), player.getEyePosition(), at);
    }

    public static boolean sees(ServerPlayer player, Entity body) {
        return player.level() == body.level() && (sees(player, body.getEyePosition()) || sees(player, body.position().add(0, body.getBbHeight() / 2, 0)));
    }

    @Nullable
    private static Vec3 blocks(Level level, Vec3 from, Vec3 to, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.POWDER_SNOW)) {
            return Vec3.atCenterOf(pos);
        }
        BlockHitResult hit = level.clipWithInteractionOverride(from, to, pos, state.getVisualShape(level, pos, CollisionContext.empty()), state);
        return hit == null ? null : hit.getLocation();
    }
}
