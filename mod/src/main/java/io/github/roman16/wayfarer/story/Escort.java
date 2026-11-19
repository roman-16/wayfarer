package io.github.roman16.wayfarer.story;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

final class Escort {
    static final String ARRIVED = "arrived";
    static final String LATE = "late";
    private static final String WHO = "who";
    private static final String DAWNS = "dawns";
    private static final String DAY = "escort_day";
    private static final String TOLD = "escort_told";
    private static final String EACH = "each";
    private static final int RETARGET = 20;
    private static final Logger LOGGER = LogUtils.getLogger();

    private Escort() {}

    static void enter(MinecraftServer server, Engine.Active active) {
        CompoundTag scratch = active.scratch();
        if (!scratch.contains(DAY)) {
            scratch.putLong(DAY, Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY));
            Progress.of(server).setDirty();
        }
        Places.build(server, active.step().text("place"));
        ServerLevel level = Engine.overworld(server);
        int reach = Balance.get().talk().reachBlocks();
        Engine.players(server).stream().filter(player -> player.level() == level).findFirst().ifPresent(player -> who(active.step()).stream()
                .filter(who -> People.find(server, who).filter(body -> body.level() == level && body.distanceTo(player) <= reach).isEmpty())
                .forEach(who -> People.catchUp(level, who, player.position(), player.getYRot())));
    }

    private static List<String> who(Story.Step step) {
        return step.spec().getAsJsonArray(WHO).asList().stream().map(JsonElement::getAsString).toList();
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        List<String> escorted = who(step);
        ServerLevel level = Engine.overworld(server);
        Balance.Escort balance = Balance.get().escort();
        long day = Math.floorDiv(level.getDayTime(), Clock.DAY);
        long passed = day - active.scratch().getLong(DAY);
        if (step.spec().has(DAWNS)) {
            int dawns = step.number(DAWNS);
            if (passed >= dawns) {
                escorted.forEach(who -> People.find(server, who).ifPresent(Entity::discard));
                end(server, active, LATE);
                return;
            }
            if (passed > active.scratch().getLong(TOLD) && step.spec().has(EACH)) {
                active.scratch().putLong(TOLD, passed);
                Progress.of(server).setDirty();
                Talk.notice(Component.literal(step.text(EACH).replace("{left}", String.valueOf(dawns - passed))).withStyle(ChatFormatting.GRAY));
            }
        }
        Optional<BlockPos> goal = Places.place(server, step.text("place"));
        int within = Places.arrival(step.text("place"));
        boolean arrived = true;
        for (String who : escorted) {
            Optional<Entity> walker = follow(server, level, balance, who);
            arrived &= walker.isPresent() && goal.filter(at -> walker.get().blockPosition().distSqr(at) <= (double) within * within).isPresent();
        }
        if (arrived) {
            end(server, active, ARRIVED);
        }
    }

    private static Optional<Entity> follow(MinecraftServer server, ServerLevel level, Balance.Escort balance, String who) {
        Optional<ServerPlayer> nearest = Engine.players(server).stream().filter(player -> player.isAlive() && player.level().dimension() == Level.OVERWORLD)
                .min(Comparator.comparingDouble(player -> People.find(server, who).map(player::distanceToSqr).orElse(0.0)));
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        ServerPlayer player = nearest.get();
        Optional<Entity> body = People.find(server, who).filter(found -> found.level() == level);
        if (body.isEmpty() || body.get().distanceTo(player) > balance.leashBlocks()) {
            People.catchUp(level, who, player.position().subtract(player.getLookAngle().multiply(1, 0, 1).normalize().scale(balance.followBlocks())), player.getYRot());
        }
        if (body.isEmpty()) {
            return Optional.empty();
        }
        Entity walker = body.get();
        double behind = walker.distanceTo(player);
        if (behind > balance.followBlocks() && (!People.walking(who) || level.getGameTime() % RETARGET == 0)) {
            People.walk(server, who, player.position(), behind > balance.jogBlocks() ? People.JOGGING : People.WALKING);
        }
        return body;
    }

    private static void end(MinecraftServer server, Engine.Active active, String outcome) {
        Progress progress = Progress.of(server);
        String key = active.quest().id() + "/" + active.step().id();
        if (progress.choices.containsKey(key)) {
            return;
        }
        progress.choices.put(key, outcome);
        progress.setDirty();
        Talk.say(active.quest().id(), null, active.step().lines(outcome), Engine.fill(progress));
        LOGGER.info("the escort of {} ends: {}", String.join(", ", who(active.step())), outcome);
        Talk.then(active.quest().id(), () -> Engine.complete(server, active));
    }
}
