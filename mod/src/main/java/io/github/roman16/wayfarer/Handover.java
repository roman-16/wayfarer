package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public final class Handover {
    private Handover() {}

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || worn(player, stack) || held(player, stack) || player.getInventory().add(stack)) {
            return;
        }
        player.drop(stack, false);
        player.displayClientMessage(Component.translatable("wayfarer.handover.feet").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    public static void give(ServerPlayer player, ItemStack stack, int slot) {
        Inventory inventory = player.getInventory();
        if (slot >= 0 && slot < inventory.items.size() && inventory.getItem(slot).isEmpty()) {
            inventory.setItem(slot, stack.copyAndClear());
            return;
        }
        give(player, stack);
    }

    public static void wear(ServerPlayer player, ItemStack stack) {
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (!slot.isArmor()) {
            give(player, stack);
            return;
        }
        ItemStack off = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack.copyAndClear());
        give(player, off);
    }

    private static boolean worn(ServerPlayer player, ItemStack stack) {
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (slot.isArmor()) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
            player.setItemSlot(slot, stack.copyAndClear());
            return true;
        }
        return CuriosApi.getCuriosInventory(player).map(curios -> CuriosApi.getItemStackSlots(stack, player).keySet().stream()
                .flatMap(id -> curios.getStacksHandler(id).stream())
                .anyMatch(handler -> {
                    for (int index = 0; index < handler.getSlots(); index++) {
                        if (handler.getStacks().getStackInSlot(index).isEmpty()) {
                            handler.getStacks().setStackInSlot(index, stack.copyAndClear());
                            return true;
                        }
                    }
                    return false;
                })).orElse(false);
    }

    private static boolean held(ServerPlayer player, ItemStack stack) {
        if (!stack.isDamageableItem() && !stack.has(DataComponents.FOOD)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getSelected().isEmpty()) {
            inventory.setItem(inventory.selected, stack.copyAndClear());
            return true;
        }
        for (int index = 0; index < Inventory.getSelectionSize(); index++) {
            if (inventory.getItem(index).isEmpty()) {
                inventory.setItem(index, stack.copyAndClear());
                return true;
            }
        }
        return false;
    }
}
