package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class SceneSpike {
    private static final int GAMEMASTER = 2;
    private static final int CHECK_TICKS = 20;
    private static final long RHYTHM_TICKS = 5 * 60 * 20;
    private static final long JITTER_TICKS = 60 * 20;
    private static final int HEADING_BLOCKS = 16;
    private static final int NEAREST = 64;
    private static final int FARTHEST = 128;
    private static final int STEP = 16;
    private static final int[] ANGLES = {0, 15, -15, 30, -30};
    private static final int GONE_BLOCKS = 64;
    private static final String BODY = "easy_npc:humanoid";
    private static final int TELEPORT_BLOCKS = 8;
    private static final Map<UUID, BlockPos> anchors = new HashMap<>();
    private static final Map<UUID, BlockPos> last = new HashMap<>();
    private static final Map<UUID, Vec3> headings = new HashMap<>();
    private static final Map<UUID, Integer> walkers = new HashMap<>();
    @Nullable
    private static Scene scene;
    private static long due = RHYTHM_TICKS;

    private SceneSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("scene")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("due").executes(context -> {
                            due = context.getSource().getServer().overworld().getGameTime();
                            context.getSource().sendSuccess(() -> Component.literal("scene: the next one is due now"), false);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("walk").then(Commands.argument("blocks", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    walkers.put(context.getSource().getPlayerOrException().getUUID(), IntegerArgumentType.getInteger(context, "blocks"));
                                    return 1;
                                })))));
    }

    private static int status(CommandSourceStack source) {
        long now = source.getServer().overworld().getGameTime();
        String text = scene == null ? "no scene; the next is due in %d s".formatted(Math.max(0, due - now) / 20)
                : "a scene at %d %d %d, %s".formatted(scene.centre.getX(), scene.centre.getY(), scene.centre.getZ(), scene.seen ? "seen" : "not seen yet");
        source.sendSuccess(() -> Component.literal("scene: " + text), false);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            source.sendSuccess(() -> Component.literal("scene: " + candidates(player)), false);
        }
        return 1;
    }

    private static String candidates(ServerPlayer player) {
        Vec3 heading = headings.get(player.getUUID());
        if (heading == null) {
            return "no heading for " + player.getScoreboardName();
        }
        ServerLevel level = player.serverLevel();
        int wet = 0;
        int blocked = 0;
        int seen = 0;
        int free = 0;
        for (int distance = NEAREST; distance <= FARTHEST; distance += STEP) {
            for (int angle : ANGLES) {
                Vec3 way = heading.yRot((float) Math.toRadians(angle));
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(player.getX() + way.x * distance, 0, player.getZ() + way.z * distance));
                if (!level.getFluidState(ground.below()).isEmpty()) {
                    wet++;
                } else if (!level.getBlockState(ground).canBeReplaced()) {
                    blocked++;
                } else if (sees(level, player, corners(ground))) {
                    seen++;
                } else {
                    free++;
                }
            }
        }
        return "heading %.2f %.2f; spots: %d in water, %d blocked, %d in sight, %d free".formatted(heading.x, heading.z, wet, blocked, seen, free);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        players.forEach(SceneSpike::walk);
        server.getPlayerList().getPlayers().stream().filter(player -> player.level() != level).forEach(player -> {
            last.remove(player.getUUID());
            headings.remove(player.getUUID());
        });
        players.forEach(SceneSpike::track);
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        if (scene != null) {
            watch(server, level, players);
        } else if (level.getGameTime() >= due) {
            for (ServerPlayer player : players) {
                if (headings.containsKey(player.getUUID()) && place(server, level, player, players)) {
                    break;
                }
            }
        }
    }

    private static void walk(ServerPlayer player) {
        Integer remaining = walkers.get(player.getUUID());
        if (remaining == null) {
            return;
        }
        Vec3 way = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos next = BlockPos.containing(player.getX() + way.x, 0, player.getZ() + way.z);
        int y = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, next.getX(), next.getZ());
        player.connection.teleport(player.getX() + way.x, y, player.getZ() + way.z, player.getYRot(), player.getXRot());
        if (remaining <= 1) {
            walkers.remove(player.getUUID());
            player.server.getPlayerList().broadcastSystemMessage(Component.literal("scene: %s walked to %d %d"
                    .formatted(player.getScoreboardName(), player.getBlockX(), player.getBlockZ())), false);
        } else {
            walkers.put(player.getUUID(), remaining - 1);
        }
    }

    private static void track(ServerPlayer player) {
        BlockPos at = player.blockPosition();
        BlockPos previous = last.put(player.getUUID(), at);
        if (previous == null || previous.distSqr(at) > TELEPORT_BLOCKS * TELEPORT_BLOCKS) {
            anchors.put(player.getUUID(), at);
            headings.remove(player.getUUID());
            return;
        }
        BlockPos anchor = anchors.get(player.getUUID());
        if (anchor == null) {
            anchors.put(player.getUUID(), at);
        } else if (anchor.distSqr(at) >= HEADING_BLOCKS * HEADING_BLOCKS) {
            headings.put(player.getUUID(), Vec3.atLowerCornerOf(at.subtract(anchor)).multiply(1, 0, 1).normalize());
            anchors.put(player.getUUID(), at);
        }
    }

    private static boolean place(MinecraftServer server, ServerLevel level, ServerPlayer leader, List<ServerPlayer> players) {
        Vec3 heading = headings.get(leader.getUUID());
        for (int distance = NEAREST; distance <= FARTHEST; distance += STEP) {
            for (int angle : ANGLES) {
                Vec3 way = heading.yRot((float) Math.toRadians(angle));
                int x = (int) Math.floor(leader.getX() + way.x * distance);
                int z = (int) Math.floor(leader.getZ() + way.z * distance);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!level.getFluidState(ground.below()).isEmpty() || !level.getBlockState(ground).canBeReplaced()) {
                    continue;
                }
                List<Vec3> corners = corners(ground);
                if (players.stream().noneMatch(player -> sees(level, player, corners))) {
                    build(server, level, ground, leader, distance, players);
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Vec3> corners(BlockPos centre) {
        List<Vec3> points = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                points.add(Vec3.atCenterOf(centre.offset(dx, 0, dz)));
                points.add(Vec3.atCenterOf(centre.offset(dx, 2, dz)));
            }
        }
        return points;
    }

    private static boolean sees(ServerLevel level, ServerPlayer player, List<Vec3> points) {
        Vec3 eye = player.getEyePosition();
        for (Vec3 point : points) {
            BlockHitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 2.25) {
                return true;
            }
        }
        return false;
    }

    private static void build(MinecraftServer server, ServerLevel level, BlockPos ground, ServerPlayer leader, int distance, List<ServerPlayer> players) {
        Scene built = new Scene(ground);
        for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, -1, -1), ground.offset(1, 2, 1))) {
            built.originals.put(pos.immutable(), level.getBlockState(pos));
        }
        set(level, ground.below(), Blocks.COARSE_DIRT.defaultBlockState());
        set(level, ground.east().below(), Blocks.COARSE_DIRT.defaultBlockState());
        set(level, ground.west(), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        set(level, ground.north(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        set(level, ground.south().east(), Blocks.HAY_BLOCK.defaultBlockState());
        Entity herder = EntityType.byString(BODY).map(type -> type.create(level)).orElse(null);
        if (herder != null) {
            herder.moveTo(ground.getX() + 1.5, ground.getY(), ground.getZ() + 0.5, 0, 0);
            herder.setCustomName(Component.literal("Herder"));
            if (herder instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            level.addFreshEntity(herder);
            built.entities.add(herder);
        }
        scene = built;
        String hidden = String.join(", ", players.stream().map(ServerPlayer::getScoreboardName).toList());
        server.getPlayerList().broadcastSystemMessage(Component.literal("scene: placed at %d %d %d, %d blocks ahead of %s, out of sight of %s"
                .formatted(ground.getX(), ground.getY(), ground.getZ(), distance, leader.getScoreboardName(), hidden)), false);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }

    private static void watch(MinecraftServer server, ServerLevel level, List<ServerPlayer> players) {
        List<Vec3> corners = corners(scene.centre);
        boolean visible = players.stream().anyMatch(player -> sees(level, player, corners));
        if (visible && !scene.seen) {
            scene.seen = true;
            server.getPlayerList().broadcastSystemMessage(Component.literal("scene: seen"), false);
        }
        boolean gone = players.stream().allMatch(player -> player.blockPosition().distSqr(scene.centre) >= GONE_BLOCKS * GONE_BLOCKS);
        if (!visible && gone && (scene.seen || players.stream().noneMatch(player -> ahead(player))) && level.isLoaded(scene.centre)) {
            scene.entities.forEach(Entity::discard);
            scene.originals.forEach((pos, state) -> level.setBlock(pos, state, 2));
            server.getPlayerList().broadcastSystemMessage(Component.literal("scene: taken away at %d %d %d, %d blocks put back"
                    .formatted(scene.centre.getX(), scene.centre.getY(), scene.centre.getZ(), scene.originals.size())), false);
            scene = null;
            due = level.getGameTime() + RHYTHM_TICKS - JITTER_TICKS + level.getRandom().nextInt((int) (2 * JITTER_TICKS));
        }
    }

    private static boolean ahead(ServerPlayer player) {
        Vec3 heading = headings.get(player.getUUID());
        return heading != null && Vec3.atCenterOf(scene.centre).subtract(player.position()).multiply(1, 0, 1).dot(heading) > 0;
    }

    private static final class Scene {
        private final BlockPos centre;
        private final Map<BlockPos, BlockState> originals = new HashMap<>();
        private final List<Entity> entities = new ArrayList<>();
        private boolean seen;

        private Scene(BlockPos centre) {
            this.centre = centre;
        }
    }
}
