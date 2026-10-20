package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.SmokeHole;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "nomadictents.structure.TentPlacer", remap = false)
abstract class NomadicTentsBarrierMixin {
    @ModifyArg(method = {"placeTent", "removeTent"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z"),
            index = 3, remap = false)
    private StructurePlaceSettings wayfarer$smokeHoleOnly(StructurePlaceSettings settings) {
        settings.getProcessors().addFirst(SmokeHole.INSTANCE);
        return settings;
    }
}
