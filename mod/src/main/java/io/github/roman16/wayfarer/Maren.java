package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class Maren {
    private static final String TAG = "wayfarer_maren";
    private static final String AT_GATE = "wayfarer_maren_gate";
    private static final String TURNED = "wayfarer_maren_turned";
    static final String BAND = "wayfarer_band";
    private static final String TEAM = "wayfarer_team";
    private static final String SIDE = "wayfarer_maren";
    private static final String GATE_CLEARED = "gate_cleared";
    private static final ResourceLocation GATE_ADVANCEMENT = Wayfarer.id("gate");
    private static final List<EntityType<? extends Raider>> BAND_TYPES = List.of(
            EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.EVOKER);
    private static final int CHECK_TICKS = 20;
    private static final int FIRE_EDGE = 2;
    private static final int SPREAD = 12;
    private static final int STEP_HEIGHT = 3;
    private static final int KEEP_AWAY = 5;
    private static final int BESIDE = 4;

    private Maren() {}

    public static Runnable visit(ServerPlayer player, BlockPos fire, List<Library.Item> gifts) {
        ServerLevel level = player.serverLevel();
        Vec3 centre = Vec3.atCenterOf(fire);
        Vec3 away = centre.subtract(player.position()).multiply(1, 0, 1);
        Vec3 direction = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 at = centre.add(direction.scale(Balance.INSTANCE.fire().circleBlocks() - FIRE_EDGE));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        Evoker maren = arrive(level, new Vec3(at.x, y, at.z), player);
        maren.setNoAi(true);
        maren.lookAt(EntityAnchorArgument.Anchor.EYES, centre);
        return () -> {
            smoke(level, maren.position());
            maren.discard();
            Vec3 drop = centre.add(direction.scale(1.5));
            for (Library.Item gift : gifts) {
                level.addFreshEntity(new ItemEntity(level, drop.x, fire.getY() + 0.5, drop.z, gift.stack()));
            }
        };
    }

    private static Evoker arrive(ServerLevel level, Vec3 at, ServerPlayer player) {
        Evoker maren = EntityType.EVOKER.create(level);
        maren.moveTo(at.x, at.y, at.z, 0, 0);
        maren.setInvulnerable(true);
        maren.setPersistenceRequired();
        maren.setCustomName(Component.literal(Story.name(Story.MAREN, Journey.tale(player).heard())));
        maren.addTag(TAG);
        maren.getPersistentData().putUUID(TEAM, Teams.id(player));
        level.addFreshEntity(maren);
        smoke(level, maren.position());
        return maren;
    }

    private static void smoke(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1, at.z, 6, 0.3, 0.6, 0.3, 0.01);
    }

    public static boolean bandNear(ServerLevel level, BlockPos frame) {
        return !level.getEntitiesOfClass(Raider.class, new AABB(frame).inflate(Balance.INSTANCE.gate().reachBlocks()),
                raider -> raider.isAlive() && raider.getTags().contains(BAND)).isEmpty();
    }

    public static void gather(ServerPlayer player, BlockPos frame) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> floor = floor(level, frame, player.blockPosition());
        Collections.shuffle(floor, new Random(level.random.nextLong()));
        for (int index = 0; index < Balance.INSTANCE.gate().bandSize(); index++) {
            BlockPos pos = floor.isEmpty() ? player.blockPosition().relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.random), KEEP_AWAY) : floor.get(index % floor.size());
            Raider raider = BAND_TYPES.get(index % BAND_TYPES.size()).create(level);
            raider.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            raider.setPersistenceRequired();
            raider.addTag(BAND);
            raider.setTarget(player);
            level.addFreshEntity(raider);
        }
        Vec3 beside = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(BESIDE));
        Evoker maren = arrive(level, new Vec3(beside.x, player.getY(), beside.z), player);
        maren.addTag(AT_GATE);
        List<ServerPlayer> listeners = listeners(level, frame);
        Story.scene(player, "gate_arrival", listeners, () -> Story.scene(player, "gate_letter", listeners, () -> turn(maren)));
    }

    private static List<BlockPos> floor(ServerLevel level, BlockPos frame, BlockPos player) {
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(frame.offset(-SPREAD, -STEP_HEIGHT, -SPREAD), frame.offset(SPREAD, STEP_HEIGHT, SPREAD))) {
            if (pos.distSqr(player) >= KEEP_AWAY * KEEP_AWAY
                    && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                floor.add(pos.immutable());
            }
        }
        return floor;
    }

    private static void turn(Evoker maren) {
        if (!maren.isAlive()) {
            return;
        }
        Scoreboard scoreboard = maren.level().getScoreboard();
        PlayerTeam side = scoreboard.getPlayerTeam(SIDE);
        if (side == null) {
            side = scoreboard.addPlayerTeam(SIDE);
        }
        scoreboard.addPlayerToTeam(maren.getStringUUID(), side);
        maren.targetSelector.removeAllGoals(goal -> true);
        maren.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(maren, Raider.class, 10, true, false, raider -> raider.getTags().contains(BAND)));
        maren.addTag(TURNED);
    }

    private static List<ServerPlayer> listeners(ServerLevel level, BlockPos frame) {
        int reach = Balance.INSTANCE.gate().reachBlocks();
        return level.players().stream().filter(player -> player.blockPosition().distSqr(frame) <= (double) reach * reach).toList();
    }

    private static boolean hers(Entity entity) {
        return entity != null && (entity.getTags().contains(TAG) || entity instanceof Vex vex && vex.getOwner() != null && vex.getOwner().getTags().contains(TAG));
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (hers(event.getEntity()) && event.getNewAboutToBeSetTarget() instanceof Player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player && hers(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        List<Evoker> waiting = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Evoker maren && maren.getTags().contains(TURNED)) {
                waiting.add(maren);
            }
        }
        for (Evoker maren : waiting) {
            if (bandNear(level, maren.blockPosition())) {
                continue;
            }
            UUID team = maren.getPersistentData().getUUID(TEAM);
            clear(level.getServer(), team, listeners(level, maren.blockPosition()));
            smoke(level, maren.position());
            maren.discard();
        }
    }

    private static void clear(MinecraftServer server, UUID team, List<ServerPlayer> listeners) {
        List<ServerPlayer> members = server.getPlayerList().getPlayers().stream().filter(player -> Teams.id(player).equals(team)).toList();
        AdvancementHolder advancement = server.getAdvancements().get(GATE_ADVANCEMENT);
        for (ServerPlayer member : members) {
            if (advancement != null) {
                member.getAdvancements().award(advancement, "cleared");
            }
        }
        if (!members.isEmpty()) {
            Story.scene(members.getFirst(), GATE_CLEARED, listeners, () -> {});
        } else {
            Journey.of(server).tale(team).say(GATE_CLEARED);
        }
    }
}
