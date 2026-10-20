package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.CallingStoneBlockEntity;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class DryCity {
    static final String NAME = "dry";
    static final String STONE = "dry_stone";
    private static final String SEEN = "vision_seen";
    private static final String UNTIL = "vision_until";
    private static final HallSite SITE = new HallSite(NAME, Wayfarer.id("hall/exotic"), NAME, 2);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double AROUND = 1.5;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SPARKS = 24;

    private DryCity() {}

    static void find(MinecraftServer server) {
        Progress progress = Progress.of(server);
        SITE.find(server, Caravan.last(server));
    }

    static void reset() {
        SITE.reset();
    }

    static boolean ready(MinecraftServer server, String name) {
        Progress progress = Progress.of(server);
        return name.equals(STONE) ? progress.place(STONE).isPresent() : SITE.entrance(progress).isPresent();
    }

    static void tick(MinecraftServer server) {
        SITE.tick(server);
        Progress progress = Progress.of(server);
        ServerLevel level = Engine.overworld(server);
        SITE.entrance(progress).filter(top -> progress.place(STONE).isEmpty() && HallSearch.loaded(level, top)).ifPresent(top -> {
            BlockPos stone = HallSearch.stone(level, SITE.low(progress).orElseThrow(), SITE.high(progress).orElseThrow()).orElse(top);
            if (level.getBlockEntity(stone) instanceof CallingStoneBlockEntity calling) {
                calling.hold(Optional.empty());
            }
            progress.place(STONE, stone);
            progress.setDirty();
            HallSearch.release(level, top);
            LOGGER.info("the Drowned City's empty stone is at {}", stone);
        });
    }

    static boolean touch(ServerPlayer player, BlockPos stone) {
        MinecraftServer server = player.server;
        for (Engine.Active active : Engine.active(server)) {
            Story.Step step = active.step();
            if (!step.kind().equals("vision") || !Steps.entered(active) || active.scratch().getBoolean(SEEN)
                    || !Places.place(server, step.text("at")).filter(stone::equals).isPresent()) {
                continue;
            }
            see(server, active, stone);
            return true;
        }
        return false;
    }

    private static void see(MinecraftServer server, Engine.Active active, BlockPos stone) {
        ServerLevel level = Engine.overworld(server);
        Story.Step step = active.step();
        List<String> figures = step.spec().getAsJsonArray("figures").asList().stream().map(element -> element.getAsString()).toList();
        Vec3 centre = Vec3.atBottomCenterOf(stone);
        for (int index = 0; index < figures.size(); index++) {
            double angle = Math.PI * 2 * index / figures.size();
            Vec3 at = centre.add(Math.cos(angle) * AROUND, 0, Math.sin(angle) * AROUND);
            Entity body = People.person(level, figures.get(index), at, Frost.yaw(centre.subtract(at)), "standing");
            spark(level, body);
        }
        CompoundTag scratch = active.scratch();
        scratch.putBoolean(SEEN, true);
        Progress.of(server).setDirty();
        Talk.say(active.quest().id(), centre, step.lines("seen"), Engine.fill(Progress.of(server)));
        Talk.then(active.quest().id(), () -> {
            Vec3 out = Vec3.atBottomCenterOf(Places.place(server, step.text("toward")).orElse(stone));
            figures.forEach(figure -> People.walk(server, figure, out, People.WALKING));
            scratch.putLong(UNTIL, level.getGameTime() + (long) step.number("fades") * TICKS_PER_SECOND);
            Progress.of(server).setDirty();
        });
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        CompoundTag scratch = active.scratch();
        ServerLevel level = Engine.overworld(server);
        if (!scratch.contains(UNTIL) || level.getGameTime() < scratch.getLong(UNTIL)) {
            return;
        }
        scratch.remove(UNTIL);
        for (var figure : active.step().spec().getAsJsonArray("figures")) {
            People.find(server, figure.getAsString()).ifPresent(body -> {
                spark(level, body);
                body.discard();
            });
        }
        Engine.complete(server, active);
    }

    private static void spark(ServerLevel level, Entity body) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, body.getX(), body.getY() + 1, body.getZ(), SPARKS, 0.3, 0.6, 0.3, 0.01);
    }
}
