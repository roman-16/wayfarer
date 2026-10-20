package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Hold;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Raids {
    private static final int EVERY_TICKS = 20;
    private static final Set<Raider> outlined = new HashSet<>();

    private Raids() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % EVERY_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            Raid raid = level.getRaidAt(player.blockPosition());
            if (raid != null && raid.isActive()) {
                raid.getAllRaiders().stream().filter(raider -> raider.isAlive() && !raider.hasGlowingTag()).forEach(raider -> {
                    Hold.outline(raider, true);
                    outlined.add(raider);
                });
            }
        }
        outlined.removeIf(raider -> {
            boolean raiding = raider.level() == level ? raider.isAlive() && raider.getCurrentRaid() != null && raider.getCurrentRaid().isActive() : true;
            if (!raiding) {
                Hold.outline(raider, false);
            }
            return !raiding;
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        outlined.forEach(raider -> Hold.outline(raider, false));
        outlined.clear();
    }
}
