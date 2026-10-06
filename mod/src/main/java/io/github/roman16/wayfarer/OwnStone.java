package io.github.roman16.wayfarer;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class OwnStone {
    static final String OWNER = Wayfarer.MODID + "_own_stone";
    static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");

    private OwnStone() {}

    public static ItemStack stone(ServerPlayer player) {
        ItemStack stone = new ItemStack(BuiltInRegistries.ITEM.get(STONE));
        CustomData.update(DataComponents.CUSTOM_DATA, stone, tag -> tag.putUUID(OWNER, player.getUUID()));
        stone.set(DataComponents.CUSTOM_NAME, Component.translatable("wayfarer.stone.name").withStyle(style -> style.withItalic(false)));
        stone.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("wayfarer.stone.lore").withStyle(ChatFormatting.GRAY))));
        return stone;
    }

    public static boolean owns(ServerPlayer player, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(OWNER) && data.copyTag().getUUID(OWNER).equals(player.getUUID());
    }

    public static boolean carried(ServerPlayer player) {
        return player.getInventory().items.stream().anyMatch(stack -> owns(player, stack));
    }
}
