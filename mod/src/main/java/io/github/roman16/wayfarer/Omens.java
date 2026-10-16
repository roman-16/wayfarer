package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Talk;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class Omens {
    private static final int TICKS_PER_MINUTE = 1200;

    private Omens() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Raider captain && captain.isCaptain() && captain.getCurrentRaid() == null
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            killer.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, Balance.get().raids().omenMinutes() * TICKS_PER_MINUTE, 0));
            Talk.tell(killer, Component.translatable("wayfarer.omen.marked"));
        }
    }
}
