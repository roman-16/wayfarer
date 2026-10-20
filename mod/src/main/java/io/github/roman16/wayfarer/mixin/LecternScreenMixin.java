package io.github.roman16.wayfarer.mixin;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternScreen.class)
abstract class LecternScreenMixin {
    @Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
    private void wayfarer$turnBackAlone(CallbackInfo info) {
        wayfarer$turn(-1);
        info.cancel();
    }

    @Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
    private void wayfarer$turnForwardAlone(CallbackInfo info) {
        wayfarer$turn(1);
        info.cancel();
    }

    @Inject(method = "forcePage", at = @At("HEAD"), cancellable = true)
    private void wayfarer$openPageAlone(int page, CallbackInfoReturnable<Boolean> info) {
        info.setReturnValue(((BookViewScreen) (Object) this).setPage(page));
    }

    @Unique
    private void wayfarer$turn(int pages) {
        BookViewScreen book = (BookViewScreen) (Object) this;
        book.setPage(((BookViewScreenAccessor) book).wayfarer$page() + pages);
    }
}
