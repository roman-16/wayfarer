package io.github.roman16.wayfarer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public final class RestEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos fire;

    RestEvent(ServerPlayer player, BlockPos fire) {
        this.player = player;
        this.fire = fire.immutable();
    }

    public ServerPlayer player() {
        return player;
    }

    public BlockPos fire() {
        return fire;
    }
}
