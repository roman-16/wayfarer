package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Shrines;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Steps {
    static final String CLUE = Wayfarer.MODID + "_clue_";
    static final String CLUE_BOX = Wayfarer.MODID + "_clue_box";
    static final String OWN_CAMP = "own_camp";
    static final String DAWN = "06:00";
    private static final String ENTERED = "entered";
    private static final String STARTED = "started";
    private static final String DECLINED = "declined";
    private static final String PEDESTAL = "pedestal";
    private static final String DAY = "day";
    static final String BURIED = "buried";
    private static final String BURIAL = "burial";
    static final String MUSIC = "music";
    static final String RESTED = "rested";
    static final String RESTED_AT = "rested_at";
    static final String RESTED_TICKS = "rested_ticks";
    private static final String UNTIL = "until";
    private static final int BLIND_TICKS = 40;
    private static final int TICKS_PER_SECOND = 20;
    private static final String HEARTHSTONE = "hearthstone";
    private static final String TOWARD = "toward";
    private static final String LEAVES = "leaves";
    private static final String ASKING = "asking";
    private static final String CALLS = "calls";

    private Steps() {}

    static boolean started(Engine.Active active) {
        return active.scratch().getBoolean(STARTED);
    }

    static boolean entered(Engine.Active active) {
        return active.scratch().getBoolean(ENTERED);
    }

    static void reset(MinecraftServer server, Engine.Active active) {
        List.copyOf(active.scratch().getAllKeys()).forEach(active.scratch()::remove);
        Progress.of(server).setDirty();
    }

    static void enter(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        active.scratch().putBoolean(ENTERED, true);
        Progress.of(server).setDirty();
        moves(server, step);
        music(server, step);
        if (step.spec().has(LEAVES)) {
            step.spec().getAsJsonArray(LEAVES).forEach(person -> People.leave(server, person.getAsString()));
        }
        switch (step.kind()) {
            case "talk" -> {}
            case "dusk" -> {
                Balance.Clock clock = Balance.get().clock();
                active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), clock.dusk(), clock.afternoonSeconds(), clock.night()));
                Places.build(server, step.text("place"));
            }
            case "hold" -> Hold.start(server, active);
            case "dawn" -> active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), DAWN, Balance.get().clock().dawnSeconds(), Balance.get().clock().road()));
            case "caravan" -> Caravan.headIn(server);
            case "reach" -> Places.build(server, step.text("place"));
            case "sleep" -> active.scratch().putLong(DAY, Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY));
            default -> {}
        }
        if (!step.kind().equals("talk") && !step.kind().equals("visit")) {
            Talk.say(active.quest().id(), null, step.lines(), Engine.fill(Progress.of(server)));
        }
        if (step.kind().equals("caravan")) {
            Engine.road(server, active);
        }
        Journal.syncAll(server);
    }

    static Optional<String> goal(Engine.Active active) {
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "reach" -> Optional.of(step.spec().has(TOWARD) ? step.text(TOWARD) : step.text("place"));
            case "caravan", "visit" -> Optional.of(Caravan.NAME);
            case "rest", "hold" -> Optional.of(step.text("at"));
            case "shard" -> Optional.of(step.text("at"));
            case "clues" -> step.spec().has("place") ? Optional.of(step.text("place")) : Optional.empty();
            case "memory" -> Optional.of(Story.get().memory(step.text("memory")).place());
            default -> Optional.empty();
        };
    }

    static boolean felt(Engine.Active active) {
        return active.step().spec().has("known") && active.step().text("known").equals("feeling");
    }

    static void music(MinecraftServer server, Story.Step step) {
        if (step.spec().has(MUSIC)) {
            Music.play(server, step.text(MUSIC), true);
        }
    }

    static void resume(MinecraftServer server, Engine.Active active) {
        music(server, active.step());
        Balance.Clock clock = Balance.get().clock();
        long until = active.scratch().getLong(UNTIL);
        switch (active.step().kind()) {
            case "dusk" -> {
                Clock.until(Engine.overworld(server), until, clock.afternoonSeconds(), clock.night());
                Places.build(server, active.step().text("place"));
            }
            case "hold" -> Hold.resume(server, active);
            case "dawn" -> Clock.until(Engine.overworld(server), until, clock.dawnSeconds(), clock.road());
            case "reach" -> Places.build(server, active.step().text("place"));
            case "bury" -> {
                if (active.scratch().contains(BURIED)) {
                    ask(active);
                }
            }
            default -> {}
        }
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        CompoundTag scratch = active.scratch();
        switch (step.kind()) {
            case "talk" -> {
                String with = step.with();
                if (!scratch.getBoolean(STARTED) && !scratch.getBoolean(DECLINED) && !Talk.busy() && with != null && Engine.pursued(server, active.quest())
                        && People.find(server, with).filter(body -> seen(server, body, step.spec().has(CALLS) ? step.number(CALLS)
                                : Balance.get().talk().startBlocks())).isPresent()) {
                    start(server, active);
                }
            }
            case "walk" -> {
                BlockPos from = Engine.at(server, step.text("from"));
                int away = Balance.get().prologue().awayBlocks();
                if (Engine.players(server).stream().anyMatch(player -> player.level().dimension() == Level.OVERWORLD
                        && horizontal(player.blockPosition(), from) >= (double) away * away)) {
                    Engine.complete(server, active);
                }
            }
            case "dusk" -> {
                if (Clock.passed() && Places.ready(server, step.text("place")) && !Talk.busy() && Engine.players(server).stream().anyMatch(Fire::resting)) {
                    Journey.of(server).wake();
                    Engine.complete(server, active);
                }
            }
            case "reach" -> {
                Optional<BlockPos> goal = Places.place(server, step.text("place"));
                int within = Places.arrival(step.text("place"));
                if (goal.isPresent() && Engine.players(server).stream()
                        .anyMatch(player -> player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(goal.get()) <= (double) within * within)) {
                    Engine.complete(server, active);
                }
            }
            case "horse" -> {
                Optional<BlockPos> fire = Places.place(server, Caravan.NAME);
                int near = Balance.get().story().nearBlocks();
                if (fire.isPresent()) {
                    Engine.overworld(server).getEntitiesOfClass(AbstractHorse.class, new AABB(fire.get()).inflate(near), AbstractHorse::isTamed).stream()
                            .findFirst().ifPresent(horse -> {
                                Caravan.adopt(server, horse, step.text("name"), step.text("keeper"));
                                Engine.complete(server, active);
                            });
                }
            }
            case "shard" -> {
                if (Journey.claimed(server).contains(Wayfarer.id("shrine/" + step.text("eye")))) {
                    if (step.spec().has("gift")) {
                        Progress progress = Progress.of(server);
                        progress.gifts.add(step.text("gift"));
                        progress.setDirty();
                    }
                    Engine.complete(server, active);
                }
            }
            case "hold" -> Hold.tick(server, active);
            case "visit" -> Visit.tick(server, active);
            case "dawn" -> {
                if (Clock.passed() && !Talk.busy()) {
                    Engine.complete(server, active);
                }
            }
            case "caravan" -> {
                if (Places.ready(server, Caravan.NAME) && Clock.evening()) {
                    Engine.complete(server, active);
                }
            }
            case "sleep" -> {
                if (Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY) > scratch.getLong(DAY)) {
                    Engine.complete(server, active);
                }
            }
            default -> {}
        }
    }

    static boolean interact(ServerPlayer player, Engine.Active active, Entity target) {
        Story.Step step = active.step();
        MinecraftServer server = player.server;
        switch (step.kind()) {
            case "talk" -> {
                if (!active.scratch().getBoolean(STARTED) && !Talk.busy() && People.who(target).filter(person -> person.equals(step.with())
                        || active.scratch().getBoolean(DECLINED) && step.lines().stream().anyMatch(line -> person.equals(line.by()))).isPresent()) {
                    Engine.track(player, active.quest().id());
                    start(server, active);
                    return true;
                }
            }
            case "bury" -> {
                if (!active.scratch().contains(BURIED) && People.who(target).filter(person -> person.equals(step.text("whom"))).isPresent()) {
                    bury(player, active, target);
                    return true;
                }
            }
            case "memory" -> {
                if (target.getTags().contains(CLUE + HEARTHSTONE)) {
                    return Memories.touch(player, FirstCamp.world(server, "hearth"), player.getMainHandItem());
                }
            }
            case "clues" -> {
                for (Story.Clue clue : step.clues()) {
                    if (target.getTags().contains(CLUE + clue.id()) && !Engine.found(active.scratch()).contains(clue.id()) && !waiting(active, clue)) {
                        Engine.find(active.scratch(), clue.id());
                        Progress.of(server).setDirty();
                        Tasks.did(player, "look");
                        Clues.found(player.serverLevel(), target, clue.id());
                        Talk.say(active.quest().id(), target.position(), new Story.Line(null, clue.text()));
                        if (Engine.found(active.scratch()).size() >= step.number("needed")) {
                            Talk.then(() -> Engine.complete(server, active));
                        }
                        Journal.syncAll(server);
                        return true;
                    }
                }
            }
            default -> {}
        }
        return false;
    }

    static void rested(ServerPlayer player, Engine.Active active, BlockPos fire) {
        Story.Step step = active.step();
        MinecraftServer server = player.server;
        switch (step.kind()) {
            case "camp" -> {
                if (Tents.pitchedNear(player.serverLevel(), fire, Balance.get().tent().fireBlocks())) {
                    Progress.of(server).place(OWN_CAMP, fire);
                    CompoundTag rested = active.scratch().getCompound(RESTED);
                    rested.putBoolean(player.getStringUUID(), true);
                    active.scratch().put(RESTED, rested);
                    Progress.of(server).setDirty();
                    if (Engine.most(server, Engine.players(server).stream().filter(member -> rested.contains(member.getStringUUID())).count())) {
                        Engine.complete(server, active);
                    }
                }
            }
            case "rest" -> {
                Optional<BlockPos> at = Places.place(server, step.text("at"));
                int within = Balance.get().story().nearBlocks();
                long now = player.serverLevel().getGameTime();
                if (at.isEmpty() || horizontal(fire, at.get()) > (double) within * within) {
                    return;
                }
                long last = active.scratch().getLong(RESTED_AT);
                long gained = last == 0 ? Fire.REST_CHECK_TICKS : Math.min(now - last, Fire.REST_CHECK_TICKS);
                active.scratch().putLong(RESTED_AT, now);
                int rested = (int) Math.min(active.scratch().getInt(RESTED_TICKS) + gained, (long) Balance.get().story().restSeconds() * TICKS_PER_SECOND);
                active.scratch().putInt(RESTED_TICKS, rested);
                Progress.of(server).setDirty();
                if (rested >= Balance.get().story().restSeconds() * TICKS_PER_SECOND && !Talk.busy()) {
                    Engine.complete(server, active);
                }
            }
            default -> {}
        }
    }

    static void start(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        active.scratch().putBoolean(STARTED, true);
        Progress.of(server).setDirty();
        if (step.spec().has("place")) {
            Places.build(server, step.text("place"));
        }
        List<Story.Line> lines = active.scratch().getBoolean(DECLINED) ? List.of(step.lines().getLast()) : step.lines();
        active.scratch().remove(DECLINED);
        Talk.say(active.quest().id(), anchor(server, step), lines, Engine.fill(Progress.of(server)));
        if (step.choices().isEmpty()) {
            Talk.then(() -> Engine.complete(server, active));
        } else {
            String asker = step.lines().stream().map(Story.Line::by).filter(by -> by != null).reduce((first, second) -> second).orElse(null);
            Vec3 at = asker == null ? null : People.find(server, asker).map(Entity::position).orElse(null);
            Progress progress = Progress.of(server);
            List<Story.Choice> offered = step.choices().stream().filter(choice -> choice.needs() == null || progress.steps.containsKey(choice.needs())).toList();
            Talk.ask(new Talk.Offer(active.quest().id(), asker, offered, at, (player, choice) -> Engine.chosen(player, active.quest().id(), step.id(), asker, choice)));
        }
    }

    private static void bury(ServerPlayer player, Engine.Active active, Entity body) {
        MinecraftServer server = player.server;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, false, false));
        body.discard();
        BlockPos sign = Prologue.grave(player.serverLevel());
        active.scratch().putLong(BURIED, sign.asLong());
        Progress.of(server).setDirty();
        Talk.say(active.quest().id(), Vec3.atCenterOf(sign), active.step().lines(BURIAL), Talk.Placeholders.NONE);
        ask(active);
        Journal.syncAll(server);
    }

    private static void ask(Engine.Active active) {
        Vec3 grave = Vec3.atCenterOf(BlockPos.of(active.scratch().getLong(BURIED)));
        Talk.ask(new Talk.Offer(active.quest().id(), null, active.step().choices(), grave,
                (player, choice) -> Engine.chosen(player, active.quest().id(), active.step().id(), null, choice)));
    }

    static void chosen(ServerPlayer player, Engine.Active active, Story.Choice choice) {
        MinecraftServer server = player.server;
        if (active.step().kind().equals("bury")) {
            inscribe(server, active, choice);
        } else if (choice.again()) {
            active.scratch().remove(STARTED);
            active.scratch().putBoolean(DECLINED, true);
            Progress.of(server).setDirty();
        } else if (choice.takes()) {
            Shrines.claim(player, BlockPos.of(active.scratch().getLong(PEDESTAL)));
        } else {
            Engine.complete(server, active);
        }
    }

    static void ask(MinecraftServer server, Engine.Active active, BlockPos pedestal) {
        if (active.scratch().contains(PEDESTAL) && Talk.busy()) {
            return;
        }
        Story.Step step = active.step();
        active.scratch().putLong(PEDESTAL, pedestal.asLong());
        Progress.of(server).setDirty();
        Talk.say(active.quest().id(), Vec3.atCenterOf(pedestal), step.lines(ASKING), Engine.fill(Progress.of(server)));
        Talk.ask(new Talk.Offer(active.quest().id(), null, step.choices(), Vec3.atCenterOf(pedestal),
                (player, choice) -> Engine.chosen(player, active.quest().id(), step.id(), null, choice), true));
    }

    private static void inscribe(MinecraftServer server, Engine.Active active, Story.Choice choice) {
        String words = choice.stone() == null ? "" : choice.stone();
        Prologue.engrave(Engine.overworld(server), BlockPos.of(active.scratch().getLong(BURIED)), words);
        Progress progress = Progress.of(server);
        progress.stone = words;
        progress.setDirty();
        Engine.complete(server, active);
    }

    @Nullable
    static Vec3 anchor(MinecraftServer server, Story.Step step) {
        if (step.with() != null) {
            Optional<Entity> body = People.find(server, step.with());
            if (body.isPresent()) {
                return body.get().position();
            }
        }
        for (String key : new String[] {"at", "place"}) {
            if (step.spec().has(key)) {
                Optional<BlockPos> place = Progress.of(server).place(step.text(key));
                if (place.isPresent()) {
                    return Vec3.atCenterOf(place.get());
                }
            }
        }
        return null;
    }

    static void moves(MinecraftServer server, Story.Step step) {
        if (!step.spec().has("moves")) {
            return;
        }
        step.spec().getAsJsonObject("moves").entrySet().forEach(entry -> {
            String place = entry.getValue().getAsJsonObject().get("place").getAsString();
            String pose = entry.getValue().getAsJsonObject().get("pose").getAsString();
            Places.seat(server, place).ifPresent(seat -> People.person(Engine.overworld(server), entry.getKey(), seat.at(), seat.yaw(), pose));
        });
    }

    static void poses(MinecraftServer server, Story.Step step) {
        if (!step.spec().has("poses")) {
            return;
        }
        step.spec().getAsJsonObject("poses").entrySet()
                .forEach(entry -> People.find(server, entry.getKey()).ifPresent(body -> People.pose(body, entry.getValue().getAsString())));
    }


    private static boolean seen(MinecraftServer server, Entity body, int blocks) {
        return Engine.players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) blocks * blocks
                && player.hasLineOfSight(body));
    }

    static boolean hidden(MinecraftServer server, Entity entity) {
        return Engine.active(server).stream().filter(active -> active.step().kind().equals("clues"))
                .anyMatch(active -> active.step().clues().stream().anyMatch(clue -> entity.getTags().contains(CLUE + clue.id()) && waiting(active, clue)));
    }

    private static boolean waiting(Engine.Active active, Story.Clue clue) {
        return clue.last() && Engine.found(active.scratch()).size() < active.step().number("needed") - 1;
    }

    static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
