package io.github.roman16.wayfarer.mixin;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Raid.RaiderType.class)
public interface RaiderTypeAccessor {
    @Accessor("entityTypeSupplier")
    Supplier<EntityType<? extends Raider>> wayfarer$type();
}
