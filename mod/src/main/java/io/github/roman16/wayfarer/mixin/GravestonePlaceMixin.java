package io.github.roman16.wayfarer.mixin;

import de.maxhenkel.gravestone.GraveUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.maxhenkel.gravestone.GraveUtils", remap = false)
abstract class GravestonePlaceMixin {
    private static final int BESIDE = 8;
    private static final int DRY_RISE = 8;
    private static final int RISE = 2;

    @Inject(method = "getGraveStoneLocation", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$beside(Level level, BlockPos fell, CallbackInfoReturnable<BlockPos> place) {
        if (level.isOutsideBuildHeight(fell)) {
            return;
        }
        BlockPos.findClosestMatch(fell, BESIDE, DRY_RISE, at -> wayfarer$open(level, at) && level.getFluidState(at).isEmpty() && level.getFluidState(at.above()).isEmpty())
                .or(() -> BlockPos.findClosestMatch(fell, BESIDE, RISE, at -> wayfarer$open(level, at)))
                .ifPresent(at -> place.setReturnValue(at.immutable()));
    }

    @Unique
    private static boolean wayfarer$open(Level level, BlockPos at) {
        return GraveUtils.isReplaceable(level, at) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
    }
}
