package io.github.roman16.wayfarer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import fuzs.pickupnotifier.client.gui.entry.DisplayEntry;
import fuzs.pickupnotifier.client.gui.entry.ItemDisplayEntry;
import io.github.roman16.wayfarer.client.PickUps;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fuzs.pickupnotifier.client.gui.entry.DisplayEntry", remap = false)
abstract class PickUpNotifierEntryMixin {
    private static final int PLATE_SIDE = 3;
    private static final int PLATE_HEIGHT = 18;

    @Shadow
    @Final
    private Rarity rarity;

    @Shadow
    public abstract int getEntryWidth(Minecraft minecraft);

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void wayfarer$plate(Minecraft minecraft, GuiGraphics graphics, int x, int y, float faded, CallbackInfo info) {
        graphics.flush();
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, 1 - faded);
        graphics.blitSprite(Stamp.PLATE, x - PLATE_SIDE, y - 1, getEntryWidth(minecraft) + PLATE_SIDE * 2, PLATE_HEIGHT);
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        info.cancel();
    }

    @Inject(method = "getComponentStyle", at = @At("HEAD"), cancellable = true)
    private void wayfarer$colour(CallbackInfoReturnable<Style> style) {
        if ((DisplayEntry) (Object) this instanceof ItemDisplayEntry item && PickUps.has(((PickUpNotifierItemAccessor) item).wayfarer$stack(), PickUps.GIVEN)) {
            style.setReturnValue(Style.EMPTY.withColor(TextColor.fromRgb(Stamp.rgb(Stamp.GOLD))));
        } else if (rarity == Rarity.COMMON) {
            style.setReturnValue(Style.EMPTY.withColor(TextColor.fromRgb(Stamp.rgb(Stamp.BONE))));
        }
    }
}
