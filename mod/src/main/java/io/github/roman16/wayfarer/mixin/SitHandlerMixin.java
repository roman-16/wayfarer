package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.story.Caravan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "bl4ckscor3.mod.sit.SitHandler", remap = false)
abstract class SitHandlerMixin {
    @Unique
    private static final ResourceLocation SNOWY_SLAB = ResourceLocation.fromNamespaceAndPath("snowrealmagic", "slab");

    @Inject(method = "isValidBlock", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$seats(Level level, BlockPos pos, BlockState state, Block block, CallbackInfoReturnable<Boolean> info) {
        if (SNOWY_SLAB.equals(BuiltInRegistries.BLOCK.getKey(block)) || level instanceof ServerLevel server && Caravan.bench(server, pos)) {
            info.setReturnValue(true);
        }
    }
}
