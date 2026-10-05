package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Tents;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;

final class Steps {
    static final String CLUE = Wayfarer.MODID + "_clue_";
    static final String CLUE_BOX = Wayfarer.MODID + "_clue_box";
    static final String OWN_CAMP = "own_camp";
    static final String DAWN = "06:00";
    private static final String ENTERED = "entered";
    private static final String STARTED = "started";
    private static final String DAY = "day";
    static final String BURIED = "buried";
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
        switch (step.kind()) {
            case "talk" -> {}
            case "dusk" -> {
                Balance.Clock clock = Balance.INSTANCE.clock();
                active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), clock.dusk(), clock.afternoonSeconds(), clock.night()));
                Places.build(server, step.text("place"));
            }
            case "hold" -> Hold.start(server, active);
            case "dawn" -> active.scratch().putLong(UNTIL, Clock.pass(Engine.overworld(server), DAWN, Balance.INSTANCE.clock().dawnSeconds(), Balance.INSTANCE.clock().road()));
            case "caravan" -> Places.build(server, "caravan");
            case "sleep" -> active.scratch().putLong(DAY, Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY));
            default -> {}
        }
        if (!step.kind().equals("talk")) {
            Talk.say(step.lines(), Talk.Placeholders.NONE);
        }
        Journal.syncAll(server);
    }

    static Optional<String> goal(Engine.Active active) {
        Story.Step step = active.step();
        return switch (step.kind()) {
            case "reach" -> Optional.of(step.text("place"));
            case "caravan" -> Optional.of(Caravan.NAME);
            case "rest", "hold" -> Optional.of(step.text("at"));
            default -> Optional.empty();
        };
    }

    static void resume(MinecraftServer server, Engine.Active active) {
        Balance.Clock clock = Balance.INSTANCE.clock();
        long until = active.scratch().getLong(UNTIL);
        switch (active.step().kind()) {
            case "dusk" -> {
                Clock.until(Engine.overworld(server), until, clock.afternoonSeconds(), clock.night());
                Places.build(server, active.step().text("place"));
            }
            case "hold" -> Hold.start(server, active);
            case "dawn" -> Clock.until(Engine.overworld(server), until, clock.dawnSeconds(), clock.road());
            case "caravan" -> Places.place(server, "caravan").ifPresent(camp -> Clock.caravan(Engine.overworld(server), camp));
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
                if (scratch.contains(BURIED)) {
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
                        .anyMatch(player -> player.level().dimension() == Level.OVERWORLD && horizontal(player.blockPosition(), goal.get()) <= (double) within * within)) {
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
                Optional<BlockPos> camp = Places.place(server, "caravan");
                if (camp.isPresent() && !scratch.getBoolean(STARTED)) {
                    scratch.putBoolean(STARTED, true);
                    Clock.caravan(Engine.overworld(server), camp.get());
                }
                if (camp.isPresent() && Clock.evening(Engine.overworld(server))) {
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
                        Talk.say(new Story.Line(null, clue.text()));
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
                    player.setRespawnPosition(player.level().dimension(), fire.above(), player.getYRot(), true, false);
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
        Talk.say(step.lines(), Talk.Placeholders.NONE);
        if (step.choices().isEmpty()) {
            Talk.then(() -> Engine.complete(server, active));
        } else {
            String asker = step.lines().stream().map(Story.Line::by).filter(by -> by != null).reduce((first, second) -> second).orElse(null);
            Talk.ask(new Talk.Offer(active.quest().id(), step.id(), asker, step.choices()));
        }
    }

    private static void bury(ServerPlayer player, Engine.Active active, Entity body) {
        MinecraftServer server = player.server;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, false, false));
        body.discard();
        BlockPos sign = Prologue.grave(player.serverLevel());
        active.scratch().putLong(BURIED, sign.asLong());
        Progress.of(server).setDirty();
        Talk.say(active.step().lines(), Talk.Placeholders.NONE);
        if (player.serverLevel().getBlockEntity(sign) instanceof SignBlockEntity entity) {
            entity.setAllowedPlayerEditor(player.getUUID());
            Talk.then(() -> {
                ServerPlayer writer = server.getPlayerList().getPlayer(player.getUUID());
                if (writer != null && writer.serverLevel().getBlockEntity(sign) instanceof SignBlockEntity stone) {
                    writer.openTextEdit(stone, true);
                }
            });
        }
    }

    private static void watchStone(MinecraftServer server, Engine.Active active) {
        BlockPos sign = BlockPos.of(active.scratch().getLong(BURIED));
        ServerLevel level = Engine.overworld(server);
        if (!(level.getBlockEntity(sign) instanceof SignBlockEntity entity) || entity.getPlayerWhoMayEdit() != null) {
            return;
        }
        String words = String.join(" ", java.util.Arrays.stream(entity.getFrontText().getMessages(false)).map(Component::getString)
                .map(String::strip).filter(line -> !line.isEmpty()).toList());
        if (words.isEmpty()) {
            level.removeBlock(sign, false);
        } else {
            entity.setWaxed(true);
            entity.setChanged();
            level.sendBlockUpdated(sign, entity.getBlockState(), entity.getBlockState(), 3);
        }
        Progress progress = Progress.of(server);
        progress.stone = words;
        progress.setDirty();
        Engine.complete(server, active);
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
