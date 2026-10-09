package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class StoryChecks {
    private static final int CHECK_TICKS = 20;
    private static final int STALL_SECONDS = 60;
    private static final int TICKS_PER_SECOND = 20;
    private static final double CLUE_REACH = 1.5;
    private static final Map<String, Long> waiting = new HashMap<>();
    private static final List<Entity> clues = new ArrayList<>();
    private static String lastSpeaker = "";

    private StoryChecks() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0 || !Engine.running(server)) {
            return;
        }
        clues(server);
        speaker(server);
        long now = server.getTickCount();
        for (Engine.Active active : Engine.active(server)) {
            Optional<String> stall = stalled(server, active);
            if (stall.isEmpty()) {
                waiting.remove(active.key());
                continue;
            }
            long since = waiting.computeIfAbsent(active.key(), key -> now);
            if (now - since >= (long) STALL_SECONDS * TICKS_PER_SECOND) {
                waiting.put(active.key(), now);
                fail(server, active.key() + " stands still for " + STALL_SECONDS + " s though " + stall.get());
            }
        }
    }

    private static Optional<String> stalled(MinecraftServer server, Engine.Active active) {
        if (!Steps.entered(active) || Talk.busy()) {
            return Optional.empty();
        }
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "caravan" -> Clock.evening() && Places.ready(server, Caravan.NAME) ? Optional.of("the evening has come at the caravan") : Optional.empty();
            case "dawn" -> Clock.passed() ? Optional.of("the night has passed") : Optional.empty();
            case "dusk" -> Clock.passed() && Engine.players(server).stream().anyMatch(Fire::resting) ? Optional.of("the dusk has come and someone rests")
                    : Optional.empty();
            case "talk" -> !Steps.started(active) && step.with() != null && Engine.pursued(server, active.quest())
                    && People.find(server, step.with()).filter(body -> Engine.players(server).stream().anyMatch(player -> player.level() == body.level()
                    && player.distanceToSqr(body) <= (double) Balance.get().talk().startBlocks() * Balance.get().talk().startBlocks()
                    && player.hasLineOfSight(body))).isPresent()
                    ? Optional.of("a player stands with " + step.with()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static void speaker(MinecraftServer server) {
        Optional<String> speaker = Talk.speaker();
        if (speaker.isEmpty()) {
            lastSpeaker = "";
            return;
        }
        if (speaker.get().equals(lastSpeaker)) {
            return;
        }
        lastSpeaker = speaker.get();
        int reach = Balance.get().talk().reachBlocks();
        People.find(server, speaker.get()).ifPresent(body -> {
            List<ServerPlayer> near = Engine.players(server).stream()
                    .filter(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) reach * reach).toList();
            if (!near.isEmpty() && near.stream().noneMatch(player -> player.hasLineOfSight(body))) {
                fail(server, speaker.get() + " speaks out of sight of " + near.getFirst().getScoreboardName());
            }
        });
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getEntity() instanceof Display.BlockDisplay
                && event.getEntity().getTags().stream().anyMatch(tag -> tag.startsWith(Steps.CLUE))) {
            clues.add(event.getEntity());
        }
    }

    private static void clues(MinecraftServer server) {
        for (Entity display : List.copyOf(clues)) {
            clues.remove(display);
            if (display.isRemoved() || !(display.level() instanceof ServerLevel level)) {
                continue;
            }
            String clue = display.getTags().stream().filter(tag -> tag.startsWith(Steps.CLUE)).findFirst().orElseThrow();
            boolean asked = Story.get().quests().stream().flatMap(quest -> quest.steps().stream()).flatMap(step -> step.clues().stream())
                    .anyMatch(step -> clue.equals(Steps.CLUE + step.id()));
            if (asked && level.getEntitiesOfClass(Interaction.class, display.getBoundingBox().inflate(CLUE_REACH), box -> box.getTags().contains(clue)).isEmpty()) {
                fail(server, "the clue " + clue.substring(Steps.CLUE.length()) + " at " + display.blockPosition().toShortString() + " cannot be clicked");
            }
        }
    }

    private static void fail(MinecraftServer server, String why) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("story check failed: " + why).withStyle(ChatFormatting.RED), false);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        waiting.clear();
        clues.clear();
        lastSpeaker = "";
    }
}
