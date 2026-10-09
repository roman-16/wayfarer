package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Sorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.client.ICuriosScreen;

final class OwnInventory {
    private static boolean open;

    private OwnInventory() {}

    static void tick(Minecraft minecraft) {
        boolean looking = minecraft.screen instanceof InventoryScreen || minecraft.screen instanceof CreativeModeInventoryScreen || minecraft.screen instanceof ICuriosScreen;
        if (looking != open && minecraft.getConnection() != null) {
            open = looking;
            PacketDistributor.sendToServer(new Sorting.Looking(looking));
        }
    }

    static void forget() {
        open = false;
    }
}
