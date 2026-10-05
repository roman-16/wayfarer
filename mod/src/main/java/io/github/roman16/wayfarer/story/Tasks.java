package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.TentPitch;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Tasks {
    public static final Set<String> STEP_TASKS = Set.of("bed", "bury", "ember", "enter", "fire", "map", "pack", "rest", "stone", "tent");
    private static final Set<String> TOLD_BY_CLIENT = Set.of("journal", "map");
    private static final Set<String> QUIET = Set.of("evening", "waiting");
    private static final String FIRST = "task.";
    private static final String JOURNAL_KEY = "key.wayfarer.journal";
    private static final int CHECK_TICKS = 20;
    private static final int FADE_TICKS = 60;
    private static final int NEAR = 32;
    private static final Map<UUID, Panel> SENT = new HashMap<>();
    private static final Map<UUID, Map<String, Component>> OPEN = new HashMap<>();
    private static final Map<UUID, Map<String, Fading>> FADING = new HashMap<>();

    private Tasks() {}

    public record Panel(Component heading, Component objective, List<Component> open, List<Component> done) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = new Type<>(Wayfarer.id("panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::heading,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::objective,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), Panel::open,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), Panel::done,
                Panel::new);
        public static final Panel EMPTY = new Panel(Component.empty(), Component.empty(), List.of(), List.of());

        @Override
        public Type<Panel> type() {
            return TYPE;
        }
    }

    public record Did(String task) implements CustomPacketPayload {
        public static final Type<Did> TYPE = new Type<>(Wayfarer.id("did"));
        public static final StreamCodec<ByteBuf, Did> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Did::new, Did::task);

        @Override
        public Type<Did> type() {
            return TYPE;
        }
    }

    private record Fading(Component text, int since) {}

    public static void told(ServerPlayer player, String task) {
        if (TOLD_BY_CLIENT.contains(task)) {
            did(player, task);
        }
    }

    public static void did(ServerPlayer player, String task) {
        if (Firsts.first(player, FIRST + task)) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_TICKS == 0) {
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        SENT.remove(player);
        OPEN.remove(player);
        FADING.remove(player);
    }

    static void refresh(ServerPlayer player) {
        Panel panel = panel(player);
        if (!panel.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), panel);
            PacketDistributor.sendToPlayer(player, panel);
        }
    }

    private static Panel panel(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!Engine.running(server)) {
            return Panel.EMPTY;
        }
        Map<String, Component> open = new LinkedHashMap<>();
        if (!Firsts.seen(player, FIRST + "journal")) {
            open.put("journal", task("journal", Component.keybind(JOURNAL_KEY)));
        }
        if (Talk.open().isPresent() && !Firsts.seen(player, FIRST + "answer")) {
            open.put("answer", task("answer", Component.keybind("key.chat")));
        }
        Progress progress = Progress.of(server);
        for (Story.Reward reward : Engine.rewards()) {
            if (progress.rewards.contains(reward.key()) && !progress.claimed(reward.key(), player.getUUID())) {
                open.put("claim/" + reward.key(), task("claim", reward.title(), Component.keybind(JOURNAL_KEY)));
            }
        }
        Component heading = Component.empty();
        Component objective = Component.empty();
        Optional<Engine.Active> tracked = Engine.tracked(server);
        if (tracked.isPresent()) {
            Engine.Active active = tracked.get();
            heading = Component.translatable("wayfarer.journal." + active.quest().kind()).append(" ").append(active.quest().title());
            objective = Component.literal(Journal.objective(player, active));
            if (active.step().spec().has("tasks")) {
                for (JsonElement task : active.step().spec().getAsJsonArray("tasks")) {
                    if (task.getAsString().equals("rest")) {
                        rest(player, active, open);
                    } else {
                        step(player, active, task.getAsString()).ifPresent(text -> open.put(task.getAsString(), text));
                    }
                }
            }
        }
        int now = server.getTickCount();
        Map<String, Fading> fading = FADING.computeIfAbsent(player.getUUID(), key -> new LinkedHashMap<>());
        OPEN.getOrDefault(player.getUUID(), Map.of()).forEach((id, text) -> {
            if (!open.containsKey(id)) {
                fading.put(id, new Fading(text, now));
            }
        });
        open.keySet().forEach(fading::remove);
        fading.keySet().removeIf(QUIET::contains);
        fading.values().removeIf(done -> now - done.since() > FADE_TICKS);
        OPEN.put(player.getUUID(), open);
        return new Panel(heading, objective, List.copyOf(open.values()), fading.values().stream().map(Fading::text).toList());
    }

    private static Optional<Component> step(ServerPlayer player, Engine.Active active, String id) {
        ServerLevel level = player.serverLevel();
        boolean inTent = Tents.isTent(level.dimension());
        return switch (id) {
            case "bury" -> unless(active.scratch().contains(Steps.BURIED), task("bury"));
            case "stone" -> unless(!active.scratch().contains(Steps.BURIED), task("stone"));
            case "tent" -> unless(inTent || Tents.pitchedNear(level, player.blockPosition(), NEAR), task("tent"));
            case "fire" -> unless(inTent || campfire(level, player.blockPosition()), task("fire", Balance.INSTANCE.tent().fireBlocks()));
            case "ember" -> unless(Firsts.seen(player, FIRST + "ember"), task("ember", Component.keybind(Sense.KEY)));
            case "map" -> unless(Firsts.seen(player, FIRST + "map") || !ModList.get().isLoaded("journeymap"),
                    task("map", Component.keybind("key.wayfarer.map")));
            case "pack" -> unless(inTent || !ModList.get().isLoaded("nomadictents") || TentPitch.carries(player), task("pack"));
            case "enter" -> unless(inTent, task("enter"));
            case "bed" -> unless(!inTent, task("bed"));
            default -> throw new IllegalStateException(active.quest().id() + "/" + active.step().id() + " names the task " + id + ", which the panel does not know");
        };
    }

    private static boolean campfire(ServerLevel level, BlockPos near) {
        return Fire.nearest(level, near.getCenter(), NEAR).filter(fire -> Tents.pitchedNear(level, fire, Balance.INSTANCE.tent().fireBlocks())).isPresent();
    }

    private static void rest(ServerPlayer player, Engine.Active active, Map<String, Component> open) {
        Predicate<ServerPlayer> rested = switch (active.step().kind()) {
            case "camp" -> member -> active.scratch().getCompound(Steps.RESTED).contains(member.getStringUUID());
            case "caravan" -> Clock::rested;
            default -> member -> false;
        };
        if (active.step().kind().equals("caravan") && Clock.nightfall()) {
            open.put("evening", task("evening"));
            return;
        }
        if (active.step().kind().equals("caravan") && !Clock.afternoon(player.serverLevel())) {
            return;
        }
        if (!rested.test(player)) {
            open.put("rest", task("rest"));
            return;
        }
        List<String> waiting = new ArrayList<>();
        Engine.players(player.server).stream().filter(member -> member != player && !rested.test(member))
                .forEach(member -> waiting.add(member.getGameProfile().getName()));
        if (!waiting.isEmpty()) {
            open.put("waiting", task("waiting", String.join(", ", waiting)));
        }
    }

    private static Optional<Component> unless(boolean done, Component text) {
        return done ? Optional.empty() : Optional.of(text);
    }

    private static Component task(String id, Object... args) {
        return Component.translatable("wayfarer.task." + id, args);
    }
}
