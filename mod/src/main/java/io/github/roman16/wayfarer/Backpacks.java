package io.github.roman16.wayfarer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

final class Backpacks {
    private Backpacks() {}

    static void sort(ServerPlayer closing, AbstractContainerMenu menu) {
        if (menu instanceof StorageContainerMenuBase<?> backpack && closing.server.getPlayerList().getPlayers().stream()
                .noneMatch(player -> player != closing && player.containerMenu instanceof StorageContainerMenuBase<?> other
                        && other.getStorageWrapper() == backpack.getStorageWrapper())) {
            backpack.sort();
        }
    }
}
