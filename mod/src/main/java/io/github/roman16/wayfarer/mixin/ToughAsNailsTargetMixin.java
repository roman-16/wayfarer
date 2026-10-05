package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.temperature.BuiltInTemperatureModifier;
import toughasnails.temperature.TemperatureHandler;
import toughasnails.temperature.TemperatureHelperImpl;

@Mixin(value = TemperatureHandler.class, remap = false)
public abstract class ToughAsNailsTargetMixin {
    @WrapOperation(method = "onPlayerTick", at = @At(value = "INVOKE",
            target = "Ltoughasnails/api/temperature/TemperatureHelper;getTemperatureAtPos(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ltoughasnails/api/temperature/TemperatureLevel;"))
    private static TemperatureLevel wayfarer$landOnly(Level level, BlockPos pos, Operation<TemperatureLevel> original) {
        return wayfarer$bound(level, pos, original.call(level, pos));
    }

    @WrapOperation(method = "onPlayerTick", at = @At(value = "INVOKE",
            target = "Ltoughasnails/temperature/BuiltInTemperatureModifier;apply(Lnet/minecraft/world/entity/player/Player;Ltoughasnails/api/temperature/TemperatureLevel;I)Lnet/minecraft/util/Tuple;"))
    private static Tuple<TemperatureLevel, Integer> wayfarer$landOnly(BuiltInTemperatureModifier modifier, Player player, TemperatureLevel current, int delay,
            Operation<Tuple<TemperatureLevel, Integer>> original) {
        Tuple<TemperatureLevel, Integer> applied = original.call(modifier, player, current, delay);
        return new Tuple<>(wayfarer$bound(player.level(), player.blockPosition(), applied.getA()), applied.getB());
    }

    private static TemperatureLevel wayfarer$bound(Level level, BlockPos pos, TemperatureLevel target) {
        TemperatureLevel land = TemperatureHelperImpl.getTemperatureAtPosWithoutProximity(level, pos);
        int lowest = Math.min(land.ordinal(), TemperatureLevel.COLD.ordinal());
        int highest = Math.max(land.ordinal(), TemperatureLevel.WARM.ordinal());
        return TemperatureLevel.values()[Mth.clamp(target.ordinal(), lowest, highest)];
    }
}
