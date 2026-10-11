package io.github.roman16.wayfarer.mixin;

import io.github.roman16.wayfarer.story.Memories;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.streamsreflowing.worldgen.StreamsWorldgen", remap = false)
public abstract class StreamsDreamMixin {
    @ModifyVariable(method = "engine", at = @At("HEAD"), argsOnly = true)
    private static WorldGenLevel wayfarer$waking(WorldGenLevel level) {
        return Memories.dream(level.getLevel()) ? level.getLevel().getServer().overworld() : level;
    }

    @ModifyVariable(method = "engineIfPresent", at = @At("HEAD"), argsOnly = true)
    private static ServerLevel wayfarer$wakingIfPresent(ServerLevel level) {
        return Memories.dream(level) ? level.getServer().overworld() : level;
    }
}
