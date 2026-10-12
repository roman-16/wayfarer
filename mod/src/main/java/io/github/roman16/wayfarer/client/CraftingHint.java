package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.Tags;

final class CraftingHint {
    private static final int GAP = 4;
    private static final String TOOLS = "tools";

    private CraftingHint() {}

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !metalTool(screen.getMenu())) {
            return;
        }
        Component hint = Component.translatable(JournalScreen.knows(TOOLS) ? "wayfarer.craft.patterns" : "wayfarer.craft.not_yet");
        event.getGuiGraphics().drawCenteredString(screen.getMinecraft().font, hint, screen.getGuiLeft() + screen.getXSize() / 2,
                screen.getGuiTop() - screen.getMinecraft().font.lineHeight - GAP, Stamp.BONE);
    }

    private static boolean metalTool(AbstractContainerMenu menu) {
        int grid = switch (menu) {
            case CraftingMenu crafting -> 9;
            case InventoryMenu inventory -> 4;
            default -> 0;
        };
        if (grid == 0 || !menu.getSlot(0).getItem().isEmpty()) {
            return false;
        }
        List<ItemStack> stacks = IntStream.rangeClosed(1, grid).mapToObj(slot -> menu.getSlot(slot).getItem()).toList();
        return stacks.stream().anyMatch(stack -> stack.is(Items.STICK)) && stacks.stream().anyMatch(stack -> stack.is(Tags.Items.INGOTS));
    }
}
