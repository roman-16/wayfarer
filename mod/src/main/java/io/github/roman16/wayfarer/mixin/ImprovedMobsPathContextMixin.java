package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Improved Mobs 1.16.0.b reads the search's context in SwimNodeEvaluator even when a mob asks for a path type outside a search, as a drowned's move control does, and crashes on null: https://github.com/Flemmli97/ImprovedMobs/issues/364
@Mixin(NodeEvaluator.class)
public abstract class ImprovedMobsPathContextMixin {
    @Shadow
    protected PathfindingContext currentContext;

    @Shadow
    protected Mob mob;

    @WrapMethod(method = "getPathType(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;")
    private PathType wayfarer$withContext(Mob asking, BlockPos pos, Operation<PathType> original) {
        if (currentContext != null) {
            return original.call(asking, pos);
        }
        currentContext = new PathfindingContext(asking.level(), asking);
        mob = asking;
        try {
            return original.call(asking, pos);
        } finally {
            currentContext = null;
            mob = null;
        }
    }
}
