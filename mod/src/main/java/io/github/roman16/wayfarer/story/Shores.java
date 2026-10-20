package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Journey;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class Shores {
    private static final String SAID = "sea_said";
    private static final String SEA = "sea";
    private static final String STRANGER = "stranger";
    private static final int STEP_BLOCKS = 16;

    private Shores() {}

    static void tick(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        if (!step.spec().has("known") || !step.text("known").equals("feeling") || active.scratch().getBoolean(SAID) || Journey.claimed(server).isEmpty()) {
            return;
        }
        Optional<BlockPos> goal = Places.place(server, step.text("place"));
        if (goal.isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        for (ServerPlayer player : Engine.players(server)) {
            if (player.level().dimension() == Level.OVERWORLD && Engine.tracked(player).filter(tracked -> tracked.quest() == active.quest()).isPresent()
                    && sea(level, player.position(), Vec3.atCenterOf(goal.get()))) {
                active.scratch().putBoolean(SAID, true);
                Progress.of(server).setDirty();
                Talk.say(active.quest().id(), null, new Story.Line(STRANGER, Scenes.voice(SEA)));
                return;
            }
        }
    }

    private static boolean sea(ServerLevel level, Vec3 from, Vec3 to) {
        int wanted = Balance.get().sense().seaBlocks();
        Vec3 way = to.subtract(from).multiply(1, 0, 1);
        int steps = (int) (way.length() / STEP_BLOCKS);
        int run = 0;
        for (int index = 1; index <= steps; index++) {
            Vec3 at = from.add(way.scale((double) index / steps));
            boolean ocean = level.getUncachedNoiseBiome(QuartPos.fromBlock((int) at.x), QuartPos.fromBlock(level.getChunkSource().getGenerator().getSeaLevel()), QuartPos.fromBlock((int) at.z))
                    .is(BiomeTags.IS_OCEAN);
            run = ocean ? run + STEP_BLOCKS : 0;
            if (run >= wanted) {
                return true;
            }
        }
        return false;
    }
}
