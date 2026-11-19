package io.github.roman16.wayfarer.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiTitleAccessor {
    @Accessor("title")
    Component wayfarer$title();

    @Accessor("titleTime")
    int wayfarer$titleTime();
}
