package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Story {
    public static final String VOICE = "voice";
    public static final String MAREN = "maren";

    private static final String LEAD = "{lead}";
    private static final String FIRST_WORDS = "first_words";
    private static final long TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_SECOND = 20;
    private static final List<Line> QUEUE = new ArrayList<>();

    private record Line(long tick, List<UUID> listeners, Component text, Runnable then) {}

    private Story() {}

    public static Optional<Library.Step> due(List<Library.Step> steps, Set<String> heard, Set<String> found, int shards, boolean slain) {
        return steps.stream()
                .filter(step -> !heard.contains(step.id())
                        && step.slain() == slain
                        && step.shards() <= shards
                        && heard.containsAll(step.after())
                        && found.containsAll(step.found()))
                .findFirst();
    }

    public static boolean speaksToday(long lastDay, long today) {
        return today > lastDay;
    }

    public static boolean voiceStarted(ServerPlayer player) {
        return Journey.tale(player).heard().contains(FIRST_WORDS);
    }

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / TICKS_PER_DAY;
    }

    public static void rest(ServerPlayer player, BlockPos fire) {
        Journey.Tale tale = Journey.tale(player);
        long today = day(player.server);
        if (!speaksToday(tale.day(), today)) {
            return;
        }
        due(Library.INSTANCE.steps(), tale.heard(), tale.found(), Journey.claimed(player).size(), Journey.slain(player))
                .ifPresent(step -> tell(player, fire, step, tale, today));
    }

    private static void tell(ServerPlayer player, BlockPos fire, Library.Step step, Journey.Tale tale, long today) {
        Optional<Component> direction = step.lead().isEmpty() ? Optional.empty() : Leads.give(player, step.lead());
        tale.hear(step.id(), today);
        Journal.complete(player, step.quest());
        Notes.trigger(player, "step:" + step.id());
        List<ServerPlayer> listeners = Fire.around(player, fire);
        Component unknown = Component.translatable("wayfarer.lead.unknown");
        List<Component> lines = step.lines().stream()
                .<Component>map(line -> line.contains(LEAD) && direction.isPresent()
                        ? fill(line, direction.get()).append(" ").append(Leads.markLink(step.lead()))
                        : fill(line, direction.orElse(unknown)))
                .toList();
        Runnable then = step.visit().map(items -> Maren.visit(player, fire, items)).orElse(() -> {});
        Component silence = Component.translatable("wayfarer.fire.silence", name(step.speaker(), tale.heard()));
        speak(listeners, step.speaker(), tale.heard(), lines, Optional.of(silence), then);
    }

    public static boolean scene(ServerPlayer player, String id, Collection<ServerPlayer> listeners, Runnable then) {
        Library.Scene scene = Library.INSTANCE.scenes().get(id);
        if (scene == null || !Journey.tale(player).say(id)) {
            return false;
        }
        Notes.trigger(player, "scene:" + id);
        if (!scene.book().isEmpty()) {
            Journal.read(player, scene.book());
        }
        speak(listeners, scene.speaker(), Journey.tale(player).heard(), scene.lines().stream().<Component>map(Component::literal).toList(), Optional.empty(), then);
        return true;
    }

    public static MutableComponent fill(String line, Component direction) {
        int at = line.indexOf(LEAD);
        if (at < 0) {
            return Component.literal(line);
        }
        return Component.literal(line.substring(0, at)).append(direction).append(line.substring(at + LEAD.length()));
    }

    public static String name(String speaker, Set<String> heard) {
        String name = "";
        for (Library.Name entry : Library.INSTANCE.speakers().get(speaker).names()) {
            if (entry.after().isEmpty() || heard.contains(entry.after())) {
                name = entry.name();
            }
        }
        return name;
    }

    private static void speak(
            Collection<ServerPlayer> listeners, String speaker, Set<String> heard, List<Component> lines, Optional<Component> silence, Runnable then) {
        if (listeners.isEmpty()) {
            then.run();
            return;
        }
        MinecraftServer server = listeners.iterator().next().server;
        List<UUID> ids = listeners.stream().map(ServerPlayer::getUUID).toList();
        ChatFormatting color = Library.INSTANCE.speakers().get(speaker).color();
        String name = name(speaker, new HashSet<>(heard));
        long gap = (long) Balance.INSTANCE.fire().lineSeconds() * TICKS_PER_SECOND;
        long tick = server.getTickCount();
        for (int index = 0; index < lines.size(); index++) {
            MutableComponent text = Component.literal("<").append(Component.literal(name).withStyle(color)).append("> ").append(lines.get(index));
            QUEUE.add(new Line(tick + index * gap, ids, text, null));
        }
        silence.ifPresent(text -> QUEUE.add(
                new Line(tick + lines.size() * gap, ids, text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), null)));
        QUEUE.add(new Line(tick + lines.size() * gap, ids, null, then));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        List<Line> due = new ArrayList<>();
        for (Iterator<Line> lines = QUEUE.iterator(); lines.hasNext(); ) {
            Line line = lines.next();
            if (line.tick() <= now) {
                due.add(line);
                lines.remove();
            }
        }
        for (Line line : due) {
            if (line.text() != null) {
                for (UUID id : line.listeners()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        player.sendSystemMessage(line.text());
                    }
                }
            }
            if (line.then() != null) {
                line.then().run();
            }
        }
    }
}
