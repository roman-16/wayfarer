package io.github.roman16.wayfarer.mixin;

import java.util.concurrent.locks.LockSupport;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.PlayerThread", remap = false)
abstract class ReactiveMusicIdleMixin {
    private static final long IDLE_NANOS = 16_000_000L;

    @Inject(method = "run", at = @At(value = "FIELD", target = "Lcircuitlord/reactivemusic/PlayerThread;kill:Z", opcode = Opcodes.GETFIELD))
    private void wayfarer$idleBetweenChecks(CallbackInfo info) {
        LockSupport.parkNanos(IDLE_NANOS);
    }
}
