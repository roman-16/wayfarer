package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class Locals {
    private static final String GAZEBOUND = "gazebound";
    private static final int SHOUT_GAP_TICKS = 320;
    private static final Map<UUID, Long> SPOKE = new HashMap<>();
    private static final Set<UUID> SHOUTED = new HashSet<>();
    private static int shoutedAt = -SHOUT_GAP_TICKS;

    private Locals() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager) || villager.isBaby()
                || !Engine.running(player.server) || Talk.busy()) {
            return;
        }
        long day = Math.floorDiv(Engine.overworld(player.server).getDayTime(), Clock.DAY);
        Long spoke = SPOKE.put(villager.getUUID(), day);
        if (spoke != null && spoke == day) {
            return;
        }
        ResourceLocation kind = BuiltInRegistries.ENTITY_TYPE.getKey(villager.getType());
        Optional<Story.Person> speaker = Story.get().people().values().stream().filter(person -> kind.equals(person.creature())).findFirst();
        List<String> lines = speaker.map(person -> person.acts().get(act(player.server))).orElse(null);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int pick = Math.floorMod(villager.getUUID().hashCode() + (int) day, lines.size());
        Talk.say(Talk.ROAD, villager.position(), new Story.Line(speaker.get().id(), lines.get(pick)));
    }

    @SubscribeEvent
    public static void onSpotted(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof AbstractIllager illager) || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player) || !illager.isAlive()
                || SHOUTED.contains(illager.getUUID()) || !Engine.running(player.server) || player.server.getTickCount() - shoutedAt < SHOUT_GAP_TICKS) {
            return;
        }
        SHOUTED.add(illager.getUUID());
        shoutedAt = player.server.getTickCount();
        List<String> shouts = Story.get().person(GAZEBOUND).shouts();
        Talk.say(Talk.ROAD, illager.position(), new Story.Line(GAZEBOUND, shouts.get(Math.floorMod(illager.getUUID().hashCode(), shouts.size()))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SPOKE.clear();
        SHOUTED.clear();
        shoutedAt = -SHOUT_GAP_TICKS;
    }

    private static String act(MinecraftServer server) {
        return Engine.main(server).map(active -> active.quest().act()).orElse("finale");
    }
}
