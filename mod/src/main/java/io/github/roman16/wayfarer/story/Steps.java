package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Steps {
    static final String CLUE = Wayfarer.MODID + "_clue_";
    static final String CLUE_BOX = Wayfarer.MODID + "_clue_box";
    static final String OWN_CAMP = "own_camp";
    static final String DAWN = "06:00";
    private static final String ENTERED = "entered";
    private static final String STARTED = "started";
    private static final String DAY = "day";
    static final String BURIED = "buried";
    static final String WRITING = "writing";
    private static final String BURIAL = "burial";
    static final String MUSIC = "music";
    static final String RESTED = "rested";
    private static final String UNTIL = "until";
    private static final int BLIND_TICKS = 40;

    private Steps() {}

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
        switch (step.kind()) {
            case "talk" -> {}
            case "dusk" -> {
                Balance.Clock clock = Balance.INSTANCE.clock();
                active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), clock.dusk(), clock.afternoonSeconds(), clock.night()));
                Places.build(server, step.text("place"));
            }
            case "hold" -> Hold.start(server, active);
            case "dawn" -> active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), DAWN, Balance.INSTANCE.clock().dawnSeconds(), Balance.INSTANCE.clock().road()));
            case "caravan" -> Caravan.headIn(server);
            case "reach" -> Places.build(server, step.text("place"));
            case "sleep" -> active.scratch().putLong(DAY, Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY));
            default -> {}
        }
        if (!step.kind().equals("talk")) {
            Talk.say(active.quest().id(), null, step.lines(), Engine.fill(Progress.of(server)));
        }
        Journal.syncAll(server);
    }

    static Optional<String> goal(Engine.Active active) {
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "reach" -> Optional.of(step.text("place"));
            case "caravan" -> Optional.of(Caravan.NAME);
            case "rest", "hold" -> Optional.of(step.text("at"));
            case "shard" -> Optional.of(step.text("at"));
            case "clues" -> step.spec().has("place") ? Optional.of(step.text("place")) : Optional.empty();
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
        Balance.Clock clock = Balance.INSTANCE.clock();
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
                    active.scratch().remove(WRITING);
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
                if (!scratch.getBoolean(STARTED) && !Talk.busy() && with != null
                        && People.find(server, with).filter(body -> near(server, body, Balance.INSTANCE.talk().startBlocks())).isPresent()) {
                    start(server, active);
                }
            }
            case "bury" -> {
                if (scratch.getBoolean(WRITING)) {
                    watchStone(server, active);
                }
            }
            case "walk" -> {
                BlockPos from = Engine.at(server, step.text("from"));
                int away = Balance.INSTANCE.prologue().awayBlocks();
                if (Engine.players(server).stream().anyMatch(player -> player.level().dimension() == Level.OVERWORLD
                        && horizontal(player.blockPosition(), from) >= (double) away * away)) {
                    Engine.complete(server, active);
                }
            }
            case "dusk" -> {
                if (Clock.passed() && Places.ready(server, step.text("place")) && !Talk.busy()) {
                    Journey.of(server).wake();
                    Engine.complete(server, active);
                }
            }
            case "reach" -> {
                Optional<BlockPos> goal = Places.place(server, step.text("place"));
                int within = Balance.INSTANCE.story().arriveBlocks();
                if (goal.isPresent() && Engine.players(server).stream()
                        .anyMatch(player -> player.level().dimension() == Level.OVERWORLD && player.blockPosition().distSqr(goal.get()) <= (double) within * within)) {
                    Engine.complete(server, active);
                }
            }
            case "horse" -> {
                Optional<BlockPos> fire = Places.place(server, Caravan.NAME);
                int near = Balance.INSTANCE.story().nearBlocks();
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
            case "dawn" -> {
                if (Clock.passed() && !Talk.busy()) {
                    Engine.complete(server, active);
                }
            }
            case "caravan" -> {
                if (Places.ready(server, Caravan.NAME) && Clock.evening(Engine.overworld(server))) {
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
                if (!active.scratch().getBoolean(STARTED) && !Talk.busy() && People.who(target).filter(person -> person.equals(step.with())).isPresent()) {
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
            case "clues" -> {
                for (Story.Clue clue : step.clues()) {
                    if (target.getTags().contains(CLUE + clue.id()) && !Engine.found(active.scratch()).contains(clue.id())) {
                        Engine.find(active.scratch(), clue.id());
                        Progress.of(server).setDirty();
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
                if (Tents.pitchedNear(player.serverLevel(), fire, Balance.INSTANCE.tent().fireBlocks())) {
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
                int within = Balance.INSTANCE.story().nearBlocks();
                if (at.isPresent() && horizontal(fire, at.get()) <= (double) within * within) {
                    Engine.complete(server, active);
                }
            }
            default -> {}
        }
    }

    private static void start(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        active.scratch().putBoolean(STARTED, true);
        Progress.of(server).setDirty();
        if (step.spec().has("place")) {
            Places.build(server, step.text("place"));
        }
        Talk.say(active.quest().id(), anchor(server, step), step.lines(), Engine.fill(Progress.of(server)));
        if (step.choices().isEmpty()) {
            Talk.then(() -> Engine.complete(server, active));
        } else {
            String asker = step.lines().stream().map(Story.Line::by).filter(by -> by != null).reduce((first, second) -> second).orElse(null);
            Vec3 at = asker == null ? null : People.find(server, asker).map(Entity::position).orElse(null);
            Talk.ask(new Talk.Offer(active.quest().id(), asker, step.choices(), at, (player, choice) -> Engine.chosen(player, active.quest().id(), step.id(), asker, choice)));
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
        if (active.step().kind().equals("bury")) {
            inscribe(player, active, choice);
        } else {
            Engine.complete(player.server, active);
        }
    }

    private static void inscribe(ServerPlayer player, Engine.Active active, Story.Choice choice) {
        MinecraftServer server = player.server;
        BlockPos sign = BlockPos.of(active.scratch().getLong(BURIED));
        if (!choice.write()) {
            String words = choice.stone() == null ? "" : choice.stone();
            Prologue.engrave(Engine.overworld(server), sign, words);
            engraved(server, active, words);
            return;
        }
        ServerPlayer writer = server.getPlayerList().getPlayer(player.getUUID());
        if (writer == null || !(writer.serverLevel().getBlockEntity(sign) instanceof SignBlockEntity stone)) {
            ask(active);
            return;
        }
        stone.setAllowedPlayerEditor(writer.getUUID());
        active.scratch().putBoolean(WRITING, true);
        Progress.of(server).setDirty();
        writer.openTextEdit(stone, true);
        Journal.syncAll(server);
    }

    private static void watchStone(MinecraftServer server, Engine.Active active) {
        BlockPos sign = BlockPos.of(active.scratch().getLong(BURIED));
        ServerLevel level = Engine.overworld(server);
        if (level.getBlockEntity(sign) instanceof SignBlockEntity entity && entity.getPlayerWhoMayEdit() != null) {
            return;
        }
        active.scratch().remove(WRITING);
        String words = Prologue.words(level, sign);
        Prologue.engrave(level, sign, words);
        engraved(server, active, words);
    }

    private static void engraved(MinecraftServer server, Engine.Active active, String words) {
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


    private static boolean near(MinecraftServer server, Entity body, int blocks) {
        return Engine.players(server).stream().anyMatch(player -> player.level() == body.level() && player.distanceToSqr(body) <= (double) blocks * blocks);
    }

    static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
