package io.github.roman16.wayfarer;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class Herds {
    private Herds() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && mob.getType().getCategory() == MobCategory.CREATURE
                && mob.getSpawnType() == MobSpawnType.CHUNK_GENERATION && !Cuts.creature(mob.getType())) {
            event.setCanceled(false);
            mob.setPersistenceRequired();
        }
    }
}
