package io.github.roman16.wayfarer.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "fuzs.pickupnotifier.client.gui.entry.ItemDisplayEntry", remap = false)
public interface PickUpNotifierItemAccessor {
    @Accessor("stack")
    ItemStack wayfarer$stack();
}
