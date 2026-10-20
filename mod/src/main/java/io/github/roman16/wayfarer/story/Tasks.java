package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.BeltLantern;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Ember;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Firsts;
import io.github.roman16.wayfarer.Heat;
import io.github.roman16.wayfarer.LastUse;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.OwnStone;
import io.github.roman16.wayfarer.Patterns;
import io.github.roman16.wayfarer.Record;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Stones;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Tasks {
    private static final String SAIL = "key.smallships.ship_sail";
    public static final Set<String> STEP_TASKS = Set.of("bed", "bury", "campfire", "ember", "enter", "fire", "horse", "hull", "look", "map", "marks", "pack", "rest", "sail", "search", "stone", "sweep",
            "tent", "tracks");
    private static final Set<String> TOLD_BY_CLIENT = Set.of("charms", "conversations", "gear", "journal", "map");
    private static final Set<String> QUIET = Set.of("evening", "waiting");
    static final String FIRST = "task.";
    static final String TALKED = "talked";
    private static final String TASK_TICKED = "task_ticked";
    private static final String INVENTORY_KEY = "key.inventory";
    public static final String KEY = "wayfarer:key";
    static final String JOURNAL_KEY = "key.wayfarer.journal";
    private static final String HOTBAR = "key.hotbar.";
    private static final int CHECK_TICKS = 20;
    private static final String DONE = "done";
    private static final int FADE_TICKS = 60;
    private static final int NEAR = 32;
    private static final int FOLLOWED = 64;
    private static final Map<UUID, Double> COMPASS_FROM = new HashMap<>();
    private static final Map<UUID, Panel> SENT = new HashMap<>();
    private static final Map<UUID, Map<String, Component>> OPEN = new HashMap<>();
    private static final Map<UUID, Map<String, Fading>> FADING = new HashMap<>();

    private Tasks() {}

    public record Panel(Component kind, Component title, Component objective, List<Component> open, List<Component> done) implements CustomPacketPayload {
        public static final Type<Panel> TYPE = new Type<>(Wayfarer.id("panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::kind,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::title,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::objective,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), Panel::open,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), Panel::done,
                Panel::new);
        public static final Panel EMPTY = new Panel(Component.empty(), Component.empty(), Component.empty(), List.of(), List.of());

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

    public static void done(ServerPlayer player, String task) {
        Engine.tracked(player).ifPresent(active -> {
            CompoundTag done = active.scratch().getCompound(DONE);
            if (done.getBoolean(task + "/" + player.getUUID())) {
                return;
            }
            done.putBoolean(task + "/" + player.getUUID(), true);
            active.scratch().put(DONE, done);
            Progress.of(player.server).setDirty();
            refresh(player);
        });
    }

    private static boolean done(ServerPlayer player, Engine.Active active, String task) {
        return active.scratch().getCompound(DONE).getBoolean(task + "/" + player.getUUID());
    }

    public static void did(ServerPlayer player, String task) {
        if (Firsts.first(player, FIRST + task)) {
            Record.event(player.server, "task", player, line -> line.addProperty("task", task));
            refresh(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_TICKS == 0) {
            refresh(player);
            if (ModList.get().isLoaded("nomadictents") && Engine.running(player.server)) {
                TentPitch.remind(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        SENT.remove(player);
        OPEN.remove(player);
        FADING.remove(player);
        COMPASS_FROM.remove(player);
    }

    public static void refresh(ServerPlayer player) {
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
        Optional<Panel> memory = Memories.panel(player);
        if (memory.isPresent()) {
            return memory.get();
        }
        Map<String, Component> open = new LinkedHashMap<>();
        if (Talk.answering(player) && !Firsts.seen(player, FIRST + "answer")) {
            int choices = Talk.open(player).map(offer -> offer.choices().size()).orElse(1);
            open.put("answer", choices == 1 ? task("answer.one", key(HOTBAR + 1))
                    : task("answer", key(HOTBAR + 1), key(HOTBAR + choices)));
        }
        if (Ember.carried(server) && !Firsts.seen(player, FIRST + "charms")) {
            open.put("charms", task("charms", key(INVENTORY_KEY)));
        }
        if (!Firsts.seen(player, FIRST + BeltLantern.SLOT)) {
            if (!BeltLantern.worn(player).isEmpty()) {
                Firsts.first(player, FIRST + BeltLantern.SLOT);
            } else if (player.getInventory().contains(BeltLantern.FITS)) {
                open.put(BeltLantern.SLOT, task(BeltLantern.SLOT, key(INVENTORY_KEY)));
            }
        }
        if (!Firsts.seen(player, FIRST + "journal")) {
            open.put("journal", task("journal", key(JOURNAL_KEY)));
        } else if (Firsts.seen(player, FIRST + TALKED) && !Firsts.seen(player, FIRST + "conversations") && !Talk.busy()) {
            open.put("conversations", task("conversations", key(JOURNAL_KEY)));
        }
        if (LastUse.lastUse(player.getMainHandItem()) && !Firsts.seen(player, FIRST + "mend")) {
            open.put("mend", task("mend", Balance.get().fire().restBlocks()));
        }
        Engine.marked(player).ifPresent(goal -> compass(player, goal, open));
        if (!Stones.mine(player).isEmpty()) {
            open.put("things", task("things"));
        }
        if (ModList.get().isLoaded("toughasnails")) {
            Heat.task(player).ifPresent(id -> open.put(id, task(id)));
        }
        if (ModList.get().isLoaded("silentgear")) {
            Patterns.task(player).ifPresent(id -> open.put(id, task(id)));
        }
        Gear.tasks(player, open);
        if (Caravan.moving(server) && Clock.morning(player.serverLevel()) && pitchedAway(player)) {
            open.put("pack", task("pack"));
        }
        Progress progress = Progress.of(server);
        for (Story.Reward reward : Engine.rewards()) {
            if (progress.rewards.contains(reward.key()) && !progress.claimed(reward.key(), player.getUUID()) && !Talk.unsaid(reward.key())) {
                open.put("claim/" + reward.key(), task("claim", reward.title(), key(JOURNAL_KEY)));
            }
        }
        Component kind = Component.empty();
        Component title = Component.empty();
        Component objective = Component.empty();
        Optional<Engine.Active> tracked = Engine.tracked(player);
        if (Caravan.homeward(player) && tracked.map(active -> !active.step().kind().equals("caravan")).orElse(true)) {
            kind = Component.translatable("wayfarer.journal.main");
            title = Component.translatable("wayfarer.caravan.title");
            Optional<String> turnedBack = Caravan.turnedBack(server);
            objective = tracked.map(active -> turnedBack
                            .map(name -> Component.translatable("wayfarer.caravan.turned_back.waits", name, Engine.title(Progress.of(server), active.quest())))
                            .orElse(Component.translatable("wayfarer.caravan.waits", Engine.title(Progress.of(server), active.quest()))))
                    .orElse(turnedBack.map(name -> Component.translatable("wayfarer.caravan.turned_back", name))
                            .orElse(Component.translatable("wayfarer.caravan.objective")));
            rest(player, "caravan", Clock::rested, open);
        } else if (tracked.isPresent()) {
            Engine.Active active = tracked.get();
            kind = Component.translatable("wayfarer.journal." + active.quest().kind());
            title = Component.literal(Engine.title(Progress.of(player.server), active.quest()));
            if (Steps.entered(active)) {
                objective = Component.literal(Journal.objective(player, active));
            }
            tasks(player, active, open);
        } else if (Engine.active(server).stream().anyMatch(active -> !active.quest().act().equals(Engine.PROLOGUE))) {
            open.put("choose", task("choose", key(JOURNAL_KEY)));
        }
        if (Clock.evening() && !Talk.busy() && Caravan.standing(server).isPresent() && !Tents.isTent(player.level().dimension()) && !player.isSleeping()
                && !Nights.sleepless(player)) {
            open.put("sleep", task(TentPitch.owns(player) ? "sleep" : "lent"));
        }
        if (OwnStone.carried(player) && !Firsts.seen(player, FIRST + "stone_set")) {
            open.put("stone_set", task("stone_set"));
        }
        Component pack = open.remove("pack");
        if (pack != null) {
            Map<String, Component> rest = new LinkedHashMap<>(open);
            open.clear();
            open.put("pack", pack);
            open.putAll(rest);
        }
        int now = server.getTickCount();
        Map<String, Fading> fading = FADING.computeIfAbsent(player.getUUID(), key -> new LinkedHashMap<>());
        OPEN.getOrDefault(player.getUUID(), Map.of()).forEach((id, text) -> {
            if (!open.containsKey(id)) {
                fading.put(id, new Fading(text, now));
            }
        });
        if (OPEN.getOrDefault(player.getUUID(), Map.of()).keySet().stream().anyMatch(id -> !open.containsKey(id) && !QUIET.contains(id))) {
            Sounds.play(player, TASK_TICKED);
        }
        open.keySet().forEach(fading::remove);
        fading.keySet().removeIf(QUIET::contains);
        fading.values().removeIf(done -> now - done.since() > FADE_TICKS);
        OPEN.put(player.getUUID(), open);
        return new Panel(kind, title, objective, List.copyOf(open.values()), fading.values().stream().map(Fading::text).toList());
    }

    private static void tasks(ServerPlayer player, Engine.Active active, Map<String, Component> open) {
        if (!active.step().spec().has("tasks") || !Steps.entered(active) || Talk.busy()) {
            return;
        }
        for (JsonElement task : active.step().spec().getAsJsonArray("tasks")) {
            if (task.getAsString().equals("rest") && active.step().kind().equals("rest")) {
                open.put("rest", task("rest_while"));
            } else if (task.getAsString().equals("rest")) {
                rest(player, active.step().kind(), rested(active), open);
            } else {
                step(player, active, task.getAsString()).ifPresent(text -> open.put(task.getAsString(), text));
            }
        }
    }

    private static Optional<Component> step(ServerPlayer player, Engine.Active active, String id) {
        ServerLevel level = player.serverLevel();
        boolean inTent = Tents.isTent(level.dimension());
        return switch (id) {
            case "bury" -> unless(active.scratch().contains(Steps.BURIED), task("bury"));
            case "campfire" -> unless(player.getInventory().contains(stack -> stack.is(Items.CAMPFIRE)) || Fire.nearest(level, player.position(), NEAR).isPresent(), task("campfire"));
            case "tent" -> unless(inTent || Tents.pitchedNear(level, player.blockPosition(), NEAR), task("tent"));
            case "fire" -> unless(inTent || campfire(level, player.blockPosition()), task("fire", Balance.get().tent().fireBlocks()));
            case "ember" -> unless(done(player, active, "ember"), task("ember", key(Sense.KEY)));
            case "look" -> unless(Firsts.seen(player, FIRST + "look"), task("look"));
            case "marks" -> unless(done(player, active, "ember"), task("marks", key(Sense.KEY)));
            case "sweep" -> unless(done(player, active, "sweep") || Firsts.seen(player, FIRST + "sweep"), task("sweep", key(Sense.KEY)));
            case "search" -> unless(done(player, active, "ember"), task("search", key(Sense.KEY)));
            case "tracks" -> unless(done(player, active, "ember"), task("tracks", key(Sense.KEY)));
            case "horse" -> Optional.of(task("horse"));
            case "hull" -> Optional.of(task("hull"));
            case "sail" -> Optional.of(player.getVehicle() instanceof Boat ? task("sail", key(SAIL)) : task("sail.board"));
            case "map" -> unless(Firsts.seen(player, FIRST + "map") || !ModList.get().isLoaded("journeymap"),
                    task("map", key("key.wayfarer.map")));
            case "pack" -> unless(!pitchedAway(player), task("pack"));
            case "enter" -> unless(inTent, task("enter"));
            case "bed" -> unless(!inTent, task("bed"));
            default -> throw new IllegalStateException(active.quest().id() + "/" + active.step().id() + " names the task " + id + ", which the panel does not know");
        };
    }

    private static boolean pitchedAway(ServerPlayer player) {
        return ModList.get().isLoaded("nomadictents") && TentPitch.pitched(player);
    }

    private static void compass(ServerPlayer player, BlockPos goal, Map<String, Component> open) {
        if (Firsts.seen(player, FIRST + "compass")) {
            return;
        }
        double distance = Math.sqrt(player.blockPosition().distToCenterSqr(goal.getX(), player.getY(), goal.getZ()));
        if (!COMPASS_FROM.containsKey(player.getUUID()) && distance <= FOLLOWED) {
            return;
        }
        double from = COMPASS_FROM.computeIfAbsent(player.getUUID(), key -> distance);
        if (distance <= from - FOLLOWED || distance <= Balance.get().story().arriveBlocks()) {
            Firsts.first(player, FIRST + "compass");
            COMPASS_FROM.remove(player.getUUID());
            return;
        }
        open.put("compass", task("compass"));
    }

    private static boolean campfire(ServerLevel level, BlockPos near) {
        return Fire.nearest(level, near.getCenter(), NEAR).filter(fire -> Tents.pitchedNear(level, fire, Balance.get().tent().fireBlocks())).isPresent()
                || Caravan.within(level.getServer(), level, near.getCenter()) && Tents.pitchedNear(level, near, NEAR);
    }

    private static Predicate<ServerPlayer> rested(Engine.Active active) {
        return switch (active.step().kind()) {
            case "camp" -> member -> active.scratch().getCompound(Steps.RESTED).contains(member.getStringUUID());
            case "caravan" -> Clock::rested;
            case "dusk" -> Fire::resting;
            default -> member -> false;
        };
    }

    private static void rest(ServerPlayer player, String kind, Predicate<ServerPlayer> rested, Map<String, Component> open) {
        if (kind.equals("caravan") && Clock.nightfall()) {
            open.put("evening", task("evening"));
            return;
        }
        if (kind.equals("caravan") && !(Clock.afternoon(player.serverLevel()) && Clock.atCaravan(player))) {
            return;
        }
        if (!rested.test(player)) {
            open.put("rest", task("rest"));
            return;
        }
        if (kind.equals("dusk")) {
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

    static Component key(String mapping) {
        return Component.keybind(mapping).withStyle(style -> style.withInsertion(KEY));
    }

    private static Component task(String id, Object... args) {
        return Component.translatable("wayfarer.task." + id, args);
    }
}
