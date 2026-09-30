package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.BookPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

public final class Books {
    private Books() {}

    public static void open(BookPayload payload) {
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(payload.pages())));
    }
}
