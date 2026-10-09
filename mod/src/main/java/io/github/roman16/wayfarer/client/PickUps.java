package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Notices;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;

public final class PickUps {
    public static final String GIVEN = Wayfarer.MODID + "_given";
    public static final String WRITTEN = Wayfarer.MODID + "_written";
    private static final String PICK_UP_NOTIFIER = "pickupnotifier";

    private PickUps() {}

    public static void given(Notices.Given given) {
        given.stacks().forEach(stack -> add(mark(stack, GIVEN)));
    }

    public static void written(Notices.Written written) {
        ItemStack icon = mark(written.icon(), WRITTEN);
        icon.setCount(1);
        icon.set(DataComponents.CUSTOM_NAME, written.text());
        add(icon);
    }

    public static boolean has(ItemStack stack, String mark) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(mark);
    }

    private static ItemStack mark(ItemStack stack, String mark) {
        ItemStack copy = stack.copy();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(mark, true);
        CustomData.update(DataComponents.CUSTOM_DATA, copy, data -> data.merge(tag));
        return copy;
    }

    private static void add(ItemStack stack) {
        if (ModList.get().isLoaded(PICK_UP_NOTIFIER)) {
            PickUpNotifierLink.add(Minecraft.getInstance(), stack);
        }
    }
}
