package io.github.roman16.wayfarer.mixin;

import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    @Redirect(method = "init", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;realmsNotificationsScreen:Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;"))
    private void wayfarer$withoutRealms(TitleScreen title, RealmsNotificationsScreen realms) {
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/internal/BrandingControl;forEachLine(ZZLjava/util/function/BiConsumer;)V"))
    private void wayfarer$withoutBranding(boolean includeMinecraft, boolean reverse, BiConsumer<Integer, String> line) {
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/internal/BrandingControl;forEachAboveCopyrightLine(Ljava/util/function/BiConsumer;)V"))
    private void wayfarer$withoutNotes(BiConsumer<Integer, String> line) {
    }
}
