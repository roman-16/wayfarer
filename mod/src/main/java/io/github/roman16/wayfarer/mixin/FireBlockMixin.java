package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.Burning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    private static final int HELD_TICKS = 32;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wayfarer$hold(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo info) {
        if (Burning.held(level, pos)) {
            level.scheduleTick(pos, (FireBlock) (Object) this, HELD_TICKS + random.nextInt(HELD_TICKS));
            info.cancel();
        }
    }
}
