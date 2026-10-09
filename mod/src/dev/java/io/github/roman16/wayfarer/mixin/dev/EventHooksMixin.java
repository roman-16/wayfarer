package io.github.roman16.wayfarer.mixin.dev;

import io.github.roman16.wayfarer.Opening;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventHooks.class, remap = false)
abstract class EventHooksMixin {
    @Inject(method = "fireServerTickPre", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$stillServerBefore(BooleanSupplier haveTime, MinecraftServer server, CallbackInfo info) {
        if (Opening.held(server)) {
            info.cancel();
        }
    }

    @Inject(method = "fireServerTickPost", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$stillServerAfter(BooleanSupplier haveTime, MinecraftServer server, CallbackInfo info) {
        if (Opening.ticked(server)) {
            info.cancel();
        }
    }

    @Inject(method = {"fireLevelTickPre", "fireLevelTickPost"}, at = @At("HEAD"), cancellable = true)
    private static void wayfarer$stillLevel(Level level, BooleanSupplier haveTime, CallbackInfo info) {
        if (level instanceof ServerLevel server && Opening.held(server.getServer())) {
            info.cancel();
        }
    }

    @Inject(method = {"firePlayerTickPre", "firePlayerTickPost"}, at = @At("HEAD"), cancellable = true)
    private static void wayfarer$stillPlayer(Player player, CallbackInfo info) {
        if (player instanceof ServerPlayer server && Opening.held(server.server)) {
            info.cancel();
        }
    }
}
