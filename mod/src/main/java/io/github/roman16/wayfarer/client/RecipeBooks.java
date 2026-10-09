package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.mixin.ImageButtonAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class RecipeBooks {
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("recipe_book/button");

    private RecipeBooks() {
    }

    public static void close(ScreenEvent.Init.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (RecipeBookType type : RecipeBookType.values()) {
                player.getRecipeBook().setOpen(type, false);
            }
        }
    }

    public static void hide(ScreenEvent.Init.Post event) {
        event.getListenersList().stream().filter(RecipeBooks::button).toList().forEach(event::removeListener);
    }

    private static boolean button(GuiEventListener listener) {
        return listener instanceof ImageButton button && ((ImageButtonAccessor) button).wayfarer$sprites().enabled().equals(BUTTON);
    }
}
