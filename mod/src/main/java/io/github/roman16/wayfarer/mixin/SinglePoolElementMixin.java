package io.github.roman16.wayfarer.mixin;

import com.mojang.datafixers.util.Either;
import io.github.roman16.wayfarer.NetherSwap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SinglePoolElement.class)
abstract class SinglePoolElementMixin {
    @Shadow
    @Final
    protected Either<ResourceLocation, StructureTemplate> template;

    @Inject(method = "getSettings", at = @At("RETURN"))
    private void wayfarer$swap(Rotation rotation, BoundingBox box, LiquidSettings liquids, boolean offset, CallbackInfoReturnable<StructurePlaceSettings> result) {
        if (template.left().map(NetherSwap::swaps).orElse(true)) {
            NetherSwap.addTo(result.getReturnValue());
        }
    }
}
