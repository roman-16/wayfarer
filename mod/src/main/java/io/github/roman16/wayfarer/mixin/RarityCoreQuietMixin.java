package io.github.roman16.wayfarer.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.yanbwe.raritycore.calc.AutoRarityCalculator", remap = false)
public abstract class RarityCoreQuietMixin {
    private static final Logger WAYFARER$LOGGER = LogUtils.getLogger();

    @Inject(method = "sendToAllPlayers", at = @At("HEAD"), cancellable = true)
    private static void wayfarer$toLog(Component message, CallbackInfo info) {
        WAYFARER$LOGGER.info("RarityCore: {}", message.getString());
        info.cancel();
    }
}
