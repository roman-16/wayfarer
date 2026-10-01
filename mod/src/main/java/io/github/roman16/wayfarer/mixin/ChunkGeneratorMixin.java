package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import io.github.roman16.wayfarer.Spread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @WrapMethod(method = "findNearestMapStructure")
    private Pair<BlockPos, Holder<Structure>> wayfarer$unspreadReach(
            ServerLevel level,
            HolderSet<Structure> structures,
            BlockPos pos,
            int rings,
            boolean skipKnownStructures,
            Operation<Pair<BlockPos, Holder<Structure>>> original) {
        return original.call(level, structures, pos, Spread.rings(level, structures, rings), skipKnownStructures);
    }
}
