package io.github.roman16.wayfarer.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mobius.seedrenderer.neoforge.NeoForgePlatform", remap = false)
public abstract class SeedGenModPacksMixin {
    private static final String PACK = "pack.mcmeta";
    private static final String OVERLAYS = "\"overlays\"";
    private static final String UNVERSIONED = "";

    @Inject(method = "modRoots", at = @At("RETURN"), cancellable = true)
    private void wayfarer$overlaysThroughNeoForge(CallbackInfoReturnable<List<Path>> info) {
        info.setReturnValue(info.getReturnValue().stream().filter(root -> !overlaid(root)).toList());
    }

    @Inject(method = "modVersion", at = @At("RETURN"), cancellable = true)
    private void wayfarer$datapackNamespacesMatch(String namespace, CallbackInfoReturnable<String> info) {
        if (info.getReturnValue() == null) {
            info.setReturnValue(UNVERSIONED);
        }
    }

    private static boolean overlaid(Path root) {
        Path pack = root.resolve(PACK);
        try {
            return Files.exists(pack) && Files.readString(pack).contains(OVERLAYS);
        } catch (IOException unreadable) {
            return false;
        }
    }
}
