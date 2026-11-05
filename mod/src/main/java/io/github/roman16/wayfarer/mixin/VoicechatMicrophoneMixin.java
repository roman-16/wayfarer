package io.github.roman16.wayfarer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ChatUtils", remap = false)
abstract class VoicechatMicrophoneMixin {
    private static final String UNAVAILABLE = "message.voicechat.microphone_unavailable";

    @Inject(method = "sendModErrorMessage(Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$quietWithoutMicrophone(String key, String error, CallbackInfo info) {
        if (UNAVAILABLE.equals(key)) {
            info.cancel();
        }
    }
}
