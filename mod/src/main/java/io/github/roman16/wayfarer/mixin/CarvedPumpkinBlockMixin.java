package io.github.roman16.wayfarer.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CarvedPumpkinBlock.class, priority = 500)
public abstract class CarvedPumpkinBlockMixin {
    @Inject(method = "trySpawnGolem", at = @At("HEAD"), cancellable = true)
    private void wayfarer$noGolems(Level level, BlockPos pos, CallbackInfo info) {
        info.cancel();
    }
}
