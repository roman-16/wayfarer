package io.github.roman16.wayfarer.client;

import fuzs.pickupnotifier.client.handler.AddEntriesHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

final class PickUpNotifierLink {
    private PickUpNotifierLink() {}

    static void add(Minecraft minecraft, ItemStack stack) {
        AddEntriesHandler.addItemEntry(minecraft, stack);
    }
}
