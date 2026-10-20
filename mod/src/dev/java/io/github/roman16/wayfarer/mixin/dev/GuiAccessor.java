package io.github.roman16.wayfarer.mixin.dev;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageString")
    Component wayfarer$bar();

    @Accessor("overlayMessageTime")
    int wayfarer$barTime();
}
