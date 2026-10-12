package io.github.roman16.wayfarer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.roman16.wayfarer.Tents;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerLevel.class)
abstract class ServerLevelSleepMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean wayfarer$teamSleeps(SleepStatus status, int percentage, Operation<Boolean> original) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (Tents.isTent(level.dimension())) {
            status.update(Tents.sleepers(level.getServer()));
        }
        return original.call(status, percentage);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"), index = 1)
    private List<ServerPlayer> wayfarer$teamDeepSleeps(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) (Object) this;
        return Tents.isTent(level.dimension()) ? Tents.sleepers(level.getServer()) : players;
    }

    @ModifyArg(method = "updateSleepingPlayerList", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;update(Ljava/util/List;)Z"))
    private List<ServerPlayer> wayfarer$teamCount(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) (Object) this;
        return Tents.isTent(level.dimension()) ? Tents.sleepers(level.getServer()) : players;
    }
}
