package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.NetherSwap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TemplateStructurePiece.class)
abstract class TemplateStructurePieceMixin {
    @Shadow
    protected StructurePlaceSettings placeSettings;

    @Inject(method = "postProcess", at = @At("HEAD"))
    private void wayfarer$swap(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunk,
            BlockPos pos, CallbackInfo info) {
        NetherSwap.addTo(placeSettings);
    }
}
