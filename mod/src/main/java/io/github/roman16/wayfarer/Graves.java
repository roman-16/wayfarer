package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import top.theillusivec4.curios.api.CuriosApi;

public final class Graves {
    private static final ResourceLocation GRAVESTONE = ResourceLocation.fromNamespaceAndPath("gravestone", "gravestone");
    private static final String SLOTS = "slots";
    private static final String SLOT = "slot";
    private static final String INDEX = "index";
    private static final String ITEM = "item";

    private Graves() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ListTag slots = new ListTag();
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> curios.getCurios().forEach((slot, handler) -> {
            if (slot.equals(Shards.SLOT)) {
                return;
            }
            var stacks = handler.getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                ItemStack stack = stacks.getStackInSlot(index);
                if (!stack.isEmpty()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString(SLOT, slot);
                    entry.putInt(INDEX, index);
                    entry.put(ITEM, stack.save(player.registryAccess()));
                    slots.add(entry);
                }
            }
        }));
        CompoundTag loadout = new CompoundTag();
        loadout.put(SLOTS, slots);
        player.setData(Wayfarer.GRAVE_CURIOS, loadout);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && event.getEntity() instanceof ServerPlayer player && Notes.first(player, "death")) {
            player.sendSystemMessage(Component.translatable("wayfarer.first.death").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).equals(GRAVESTONE)
                && !player.getData(Wayfarer.GRAVE_CURIOS).isEmpty()) {
            player.server.execute(() -> restore(player));
        }
    }

    private static void restore(ServerPlayer player) {
        ListTag slots = player.getData(Wayfarer.GRAVE_CURIOS).getList(SLOTS, Tag.TAG_COMPOUND);
        player.setData(Wayfarer.GRAVE_CURIOS, new CompoundTag());
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (Tag tag : slots) {
                CompoundTag entry = (CompoundTag) tag;
                ItemStack wanted = ItemStack.parseOptional(player.registryAccess(), entry.getCompound(ITEM));
                var handler = curios.getStacksHandler(entry.getString(SLOT));
                int index = entry.getInt(INDEX);
                int held = find(player.getInventory(), wanted);
                if (handler.isPresent() && held >= 0 && index < handler.get().getStacks().getSlots()
                        && handler.get().getStacks().getStackInSlot(index).isEmpty()) {
                    handler.get().getStacks().setStackInSlot(index, player.getInventory().removeItemNoUpdate(held));
                }
            }
        });
    }

    private static int find(Inventory inventory, ItemStack wanted) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ItemStack.matches(inventory.getItem(slot), wanted)) {
                return slot;
            }
        }
        return -1;
    }
}
