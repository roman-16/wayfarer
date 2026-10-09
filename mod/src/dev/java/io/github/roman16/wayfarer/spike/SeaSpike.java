package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Nights;
import io.github.roman16.wayfarer.Shine;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class SeaSpike {
    private static final double ABOARD = 2;
    private static final String BEAST = Wayfarer.MODID + "_deep";
    private static final int BEAST_REACH = 128;
    private static final double BEAST_SCALE = 4;
    private static final int GAMEMASTER = 2;
    private static final int RAM_REACH = 4;
    private static final int RISE_DISTANCE = 16;
    private static final int RISE_DEPTH = 8;
    private static final int SAMPLE_STEP = 8;
    private static final int STEER_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<UUID, Long> rammed = new HashMap<>();

    private SeaSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("sea")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("rise").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> rise(EntityArgument.getPlayer(context, "player")) ? 1 : 0)))));
    }

    private static int status(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        say(player.server, "sea: %s is %s, on %s; %s; the beast comes one check in %d; %d beasts near".formatted(player.getScoreboardName(),
                afloat(player) ? "afloat" : "not afloat", open(level, player.blockPosition()) ? "open water" : "water near land",
                calling(level) ? "it is dark or raining" : "it is day and dry", oneIn(player.server), beasts(level, player).size()));
        return 1;
    }

    private static boolean afloat(ServerPlayer player) {
        return aboard(player) != null || player.isInWater();
    }

    private static Boat aboard(ServerPlayer player) {
        if (player.getVehicle() instanceof Boat boat) {
            return boat;
        }
        return player.level().getEntities(EntityTypeTest.forClass(Boat.class), player.getBoundingBox().inflate(ABOARD), Entity::isAlive).stream()
                .findFirst().orElse(null);
    }

    private static boolean calling(Level level) {
        return Nights.night(level.getServer().overworld()) || level.isRaining();
    }

    private static int oneIn(MinecraftServer server) {
        Balance.Sea sea = Balance.get().sea();
        return Math.max(sea.beastOneInLeast(), sea.beastOneIn() - Shine.of(server) / sea.shinePerStep());
    }

    static boolean open(ServerLevel level, BlockPos at) {
        int reach = Balance.get().sea().openWaterBlocks();
        for (int dx = -reach; dx <= reach; dx += SAMPLE_STEP) {
            for (int dz = -reach; dz <= reach; dz += SAMPLE_STEP) {
                int x = at.getX() + dx;
                int z = at.getZ() + dz;
                if (dx * dx + dz * dz > reach * reach || !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (level.getFluidState(top).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Guardian> beasts(ServerLevel level, Entity near) {
        return level.getEntities(EntityTypeTest.forClass(Guardian.class), new AABB(near.blockPosition()).inflate(BEAST_REACH),
                guardian -> guardian.getTags().contains(BEAST));
    }

    private static boolean rise(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + 90).scale(RISE_DISTANCE);
        BlockPos at = BlockPos.containing(player.position().add(side)).below(RISE_DEPTH);
        Guardian beast = EntityType.GUARDIAN.create(level);
        if (beast == null || !level.getFluidState(at).isSource()) {
            say(player.server, "sea: no deep water beside %s for the beast".formatted(player.getScoreboardName()));
            return false;
        }
        beast.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        beast.getAttribute(Attributes.SCALE).setBaseValue(BEAST_SCALE);
        beast.addTag(BEAST);
        beast.setPersistenceRequired();
        level.addFreshEntity(beast);
        say(player.server, "sea: something rises under %s's boat at %d %d %d".formatted(player.getScoreboardName(), at.getX(), at.getY(), at.getZ()));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Balance.Sea sea = Balance.get().sea();
        if (server.getTickCount() % (sea.checkSeconds() * TICKS_PER_SECOND) == 0) {
            for (ServerPlayer player : level.players()) {
                if (afloat(player) && calling(level) && beasts(level, player).isEmpty() && open(level, player.blockPosition())
                        && level.random.nextInt(oneIn(server)) == 0) {
                    rise(player);
                }
            }
        }
        if (server.getTickCount() % STEER_TICKS != 0) {
            return;
        }
        for (Guardian beast : level.getEntities(EntityTypeTest.forClass(Guardian.class), guardian -> guardian.getTags().contains(BEAST))) {
            if (!calling(level)) {
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, beast.getX(), beast.getY(), beast.getZ(), 64, 1, 1, 1, 0.1);
                beast.discard();
                say(server, "sea: the beast dives at dawn");
                continue;
            }
            ServerPlayer nearest = level.getNearestPlayer(beast, BEAST_REACH) instanceof ServerPlayer player ? player : null;
            if (nearest == null) {
                continue;
            }
            Entity boat = aboard(nearest) instanceof Boat hull ? hull : nearest;
            beast.getNavigation().moveTo(boat.getX(), boat.getY() - 2, boat.getZ(), 1.5);
            long now = server.getTickCount();
            boolean close = beast.position().subtract(boat.position()).horizontalDistance() < RAM_REACH + beast.getBbWidth() / 2;
            if (close && now - rammed.getOrDefault(beast.getUUID(), 0L) >= (long) sea.ramSeconds() * TICKS_PER_SECOND) {
                rammed.put(beast.getUUID(), now);
                boat.hurt(level.damageSources().mobAttack(beast), sea.ramDamage());
                say(server, boat.isRemoved() ? "sea: the beast breaks %s's %s".formatted(nearest.getScoreboardName(), name(boat))
                        : "sea: the beast rams %s's %s%s".formatted(nearest.getScoreboardName(), name(boat), boat instanceof Boat hull
                                ? ", damage %.0f".formatted(hull.getDamage()) : ""));
            }
        }
    }

    private static String name(Entity entity) {
        return EntityType.getKey(entity.getType()).getPath();
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
