package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Ground;
import io.github.roman16.wayfarer.story.People;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class Waves {
    private static final int RING_TURNS = 16;
    private static final int TICKS_PER_SECOND = 20;
    private static final int REACHED_BLOCKS = 16;
    private static final Map<UUID, Long> unseen = new HashMap<>();
    private static boolean reached;

    private Waves() {}

    public static int size(MinecraftServer server, int wave, long players) {
        Balance.Waves waves = Balance.get().waves();
        return (int) ((waves.first() + (long) waves.growth() * wave) * Math.max(1, players)) + Shine.of(server) / waves.shinePerCreature();
    }

    public static Optional<Vec3> round(ServerLevel level, BlockPos centre) {
        int ring = Balance.get().waves().ringBlocks();
        for (int turn = 0; turn < RING_TURNS; turn++) {
            double angle = Math.PI * 2 * (turn + level.random.nextDouble()) / RING_TURNS;
            BlockPos column = BlockPos.containing(centre.getX() + Math.cos(angle) * ring, centre.getY(), centre.getZ() + Math.sin(angle) * ring);
            if (!level.isLoaded(column) || Ground.wet(level, column.getX(), column.getZ())) {
                continue;
            }
            Vec3 feet = Ground.stand(level, Vec3.atBottomCenterOf(Ground.above(level, column)));
            if (!People.seen(level, feet)) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    public static void ring(ServerLevel level, Mob monster, BlockPos centre) {
        round(level, centre).ifPresent(spot -> {
            monster.moveTo(spot.x, spot.y, spot.z, monster.getYRot(), 0);
            monster.getNavigation().stop();
        });
    }

    static boolean reached() {
        boolean was = reached;
        reached = false;
        return was;
    }

    public static void watch(ServerPlayer player, List<Mob> wave) {
        long now = player.serverLevel().getGameTime();
        if (wave.stream().anyMatch(monster -> monster.distanceTo(player) <= REACHED_BLOCKS)) {
            reached = true;
        }
        if (wave.isEmpty() || wave.stream().anyMatch(monster -> monster.hasLineOfSight(player))) {
            unseen.remove(player.getUUID());
            return;
        }
        long since = unseen.computeIfAbsent(player.getUUID(), uuid -> now);
        if (now - since >= (long) Balance.get().waves().unseenSeconds() * TICKS_PER_SECOND) {
            wave.forEach(monster -> ring(player.serverLevel(), monster, player.blockPosition()));
            unseen.remove(player.getUUID());
        }
    }
}
