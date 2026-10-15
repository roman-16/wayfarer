package io.github.roman16.wayfarer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "bl4ckscor3.mod.sit.SitEntity", remap = false)
abstract class SitEntityMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void wayfarer$onTop(Level level, BlockPos pos, CallbackInfo info) {
        double top = level.getBlockState(pos).getCollisionShape(level, pos).max(Direction.Axis.Y);
        if (top > 0) {
            Entity seat = (Entity) (Object) this;
            seat.setPos(seat.getX(), pos.getY() + top, seat.getZ());
        }
    }
}
