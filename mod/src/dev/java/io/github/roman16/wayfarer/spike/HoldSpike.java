package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class HoldSpike {
    private static final int FOLD = 3;
    private static final int GAMEMASTER = 2;
    private static final int REACH = 96;
    private static final int SHEEP = 4;
    private static final int TICKS_PER_SECOND = 20;
    @Nullable
    private static Hold hold;

    private HoldSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("hold")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("retry").executes(context -> retry(context.getSource())))
                        .then(Commands.literal("start").then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))));
    }

    private static int start(CommandSourceStack source, int seconds) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (hold != null) {
            say(source.getServer(), "hold failed: the fold is already being held");
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(4, 0, 0));
        hold = new Hold(level, centre, seconds * TICKS_PER_SECOND, player.getScoreboardName());
        build(hold);
        command(source.getServer(), "hordes start %s %d".formatted(player.getScoreboardName(), seconds * TICKS_PER_SECOND));
        say(source.getServer(), "hold: the herder's fold at %d %d %d with %d sheep; keep it standing for %d s while the horde comes".formatted(
                centre.getX(), centre.getY(), centre.getZ(), SHEEP, seconds));
        return 1;
    }

    private static int retry(CommandSourceStack source) {
        if (hold == null || !hold.failed) {
            say(source.getServer(), "hold failed: there is no fallen fold to try again");
            return 0;
        }
        Hold again = new Hold(hold.level, hold.centre, hold.ticks, hold.player);
        hold.sheep.stream().map(hold.level::getEntity).filter(entity -> entity != null).forEach(Entity::discard);
        hold = again;
        build(hold);
        command(source.getServer(), "hordes start %s %d".formatted(hold.player, hold.ticks));
        say(source.getServer(), "hold: the fold is put right and the sheep are back; try again");
        return 1;
    }

    private static void build(Hold hold) {
        ServerLevel level = hold.level;
        for (int dx = -FOLD; dx <= FOLD; dx++) {
            for (int dz = -FOLD; dz <= FOLD; dz++) {
                if (Math.abs(dx) == FOLD || Math.abs(dz) == FOLD) {
                    BlockPos post = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hold.centre.offset(dx, 0, dz));
                    level.setBlockAndUpdate(post, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        }
        for (int count = 0; count < SHEEP; count++) {
            Sheep sheep = EntityType.SHEEP.create(level);
            sheep.moveTo(hold.centre.getX() + 0.5 + (count % 2) - 0.5, hold.centre.getY(), hold.centre.getZ() + 0.5 + (count / 2) - 0.5, 0, 0);
            sheep.setPersistenceRequired();
            level.addFreshEntity(sheep);
            hold.sheep.add(sheep.getUUID());
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (hold == null || event.loadedFromDisk() || !(event.getEntity() instanceof Monster monster) || event.getLevel() != hold.level
                || monster.distanceToSqr(hold.centre.getCenter()) > (double) REACH * REACH) {
            return;
        }
        monster.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(monster, Sheep.class, false,
                sheep -> hold != null && hold.sheep.contains(sheep.getUUID())));
        hold.drawn++;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (hold == null || hold.failed || hold.done) {
            return;
        }
        MinecraftServer server = event.getServer();
        hold.elapsed++;
        if (hold.elapsed % TICKS_PER_SECOND != 0) {
            return;
        }
        long alive = hold.sheep.stream().map(hold.level::getEntity).filter(entity -> entity != null && entity.isAlive()).count();
        int near = hold.level.getEntitiesOfClass(Monster.class, new AABB(hold.centre).inflate(REACH)).size();
        if (alive == 0) {
            hold.failed = true;
            say(server, "hold failed: the fold fell after %d s; %d monsters came for it".formatted(hold.elapsed / TICKS_PER_SECOND, hold.drawn));
        } else if (hold.elapsed >= hold.ticks) {
            hold.done = true;
            say(server, "hold: the fold stood for %d s with %d of %d sheep; %d monsters came for it, %d still near".formatted(
                    hold.elapsed / TICKS_PER_SECOND, alive, SHEEP, hold.drawn, near));
            hold = null;
        }
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    private static final class Hold {
        private final ServerLevel level;
        private final BlockPos centre;
        private final int ticks;
        private final String player;
        private final List<UUID> sheep = new ArrayList<>();
        private int drawn;
        private int elapsed;
        private boolean failed;
        private boolean done;

        private Hold(ServerLevel level, BlockPos centre, int ticks, String player) {
            this.level = level;
            this.centre = centre;
            this.ticks = ticks;
            this.player = player;
        }
    }
}
