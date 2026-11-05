package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.LastUse;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

final class Worn implements IItemDecorator {
    private static final int MARK = 4;
    private static final int RAISED = 200;

    private Worn() {}

    static void register(RegisterItemDecorationsEvent event) {
        Worn worn = new Worn();
        BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).isDamageableItem()).forEach(item -> event.register(item, worn));
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!LastUse.lastUse(stack)) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, RAISED);
        Stamp.tinted(graphics, Stamp.compass("worn"), x, y, MARK, MARK, Stamp.DANGER);
        graphics.pose().popPose();
        return true;
    }
}
