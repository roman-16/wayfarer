package io.github.roman16.wayfarer.story;

import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

public final class Bell {
    static final String PLACE = "mauds_bell";
    private static final String DECIDED = "the_bell/the_bell";
    private static final String MELT = "melt";
    private static final String KEEP = "keep";
    private static final String TOLL = "bell_toll";
    private static final int REACH = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long tolled = -1;

    private Bell() {}

    public static boolean present(MinecraftServer server) {
        return !MELT.equals(Progress.of(server).choices.get(DECIDED));
    }

    public static boolean rings(MinecraftServer server) {
        return Engine.running(server) && KEEP.equals(Progress.of(server).choices.get(DECIDED));
    }

    public static Optional<BlockPos> camp(MinecraftServer server) {
        return rings(server) ? Caravan.fire(server) : Optional.empty();
    }

    public static int circle(MinecraftServer server, BlockPos fire) {
        int circle = Balance.get().fire().circleBlocks();
        return camp(server).filter(fire::equals).isPresent() ? circle * 2 : circle;
    }

    static void tick(MinecraftServer server) {
        Progress progress = Progress.of(server);
        Optional<BlockPos> bell = progress.place(PLACE);
        if (bell.isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        if (MELT.equals(progress.choices.get(DECIDED)) && level.isLoaded(bell.get())) {
            BlockPos.betweenClosedStream(bell.get().offset(-REACH, -REACH, -REACH), bell.get().offset(REACH, REACH, REACH))
                    .filter(pos -> level.getBlockState(pos).is(Wayfarer.TOLLBRIDGE_BELL)).map(BlockPos::immutable).toList().forEach(pos -> {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        LOGGER.info("Hob melted Maud's bell at {}", pos);
                    });
        }
        long day = Math.floorDiv(level.getDayTime(), Clock.DAY);
        boolean dusk = Math.floorMod(level.getDayTime(), Clock.DAY) >= Clock.ticks(Balance.get().clock().dusk());
        if (day != tolled && dusk && camp(server).isPresent() && level.isLoaded(bell.get())) {
            tolled = day;
            Sounds.play(level, bell.get().getCenter(), TOLL);
        }
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Enemy) || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        camp(level.getServer()).filter(fire -> fire.getCenter().distanceToSqr(event.getX(), event.getY(), event.getZ())
                < (double) circle(level.getServer(), fire) * circle(level.getServer(), fire)).ifPresent(fire -> event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        tolled = -1;
    }
}
