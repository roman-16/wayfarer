package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.ExactRingPlacement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin {
    @Shadow
    @Final
    private long concentricRingsSeed;

    @Shadow
    @Final
    private BiomeSource biomeSource;

    @Shadow
    @Final
    private RandomState randomState;

    @Inject(method = "generateRingPositions", at = @At("HEAD"), cancellable = true)
    private void wayfarer$exactRing(
            Holder<StructureSet> structureSet,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> result) {
        if (placement instanceof ExactRingPlacement ring) {
            result.setReturnValue(ring.positions(concentricRingsSeed, biomeSource, randomState.sampler()));
        }
    }
}
