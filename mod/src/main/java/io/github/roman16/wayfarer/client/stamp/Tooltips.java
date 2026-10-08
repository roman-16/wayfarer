package io.github.roman16.wayfarer.client.stamp;

import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

public final class Tooltips {
    private static final int MARGIN = 4;
    private static final int LAYER = 400;
    private static final int SINGLE_LINE = -2;

    private Tooltips() {
    }

    public static void color(RenderTooltipEvent.Color event) {
        int width = 0;
        int height = event.getComponents().size() == 1 ? SINGLE_LINE : 0;
        for (ClientTooltipComponent component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        boolean story = !event.getItemStack().isEmpty()
                && BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace().equals(Wayfarer.MODID);
        GuiGraphics graphics = event.getGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, LAYER);
        graphics.blitSprite(story ? Stamp.TOOLTIP_STORY : Stamp.TOOLTIP, event.getX() - MARGIN, event.getY() - MARGIN, width + MARGIN * 2,
                height + MARGIN * 2);
        graphics.pose().popPose();
        event.setBackground(0);
        event.setBorderStart(0);
        event.setBorderEnd(0);
    }
}
