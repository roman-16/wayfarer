package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Progress;
import io.github.roman16.wayfarer.story.Story;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Shine {
    private static final String IMPROVED_MOBS = "improvedmobs";
    private static final int FAR = 48;
    private static final int LIMIT_SHINE = 4;
    private static final int NEAR = 24;
    private static final ResourceLocation NOTICE = Wayfarer.id("shine_notice");
    private static final Field TARGET_CONDITIONS = field(NearestAttackableTargetGoal.class, "targetConditions");
    private static final Field TARGET_SELECTOR = field(Mob.class, "targetSelector");
    private static final int TICKS_PER_SECOND = 20;
    private static final int WATCHED = 64;
    private static int applied = -1;

    private Shine() {}

    public static int of(MinecraftServer server) {
        if (Journey.slain(server)) {
            return 0;
        }
        Balance.Shine balance = Balance.get().shine();
        int act = Math.min(act(server), balance.act().size()) - 1;
        return balance.act().get(act) + Journey.claimed(server).size() * balance.perShard();
    }

    private static int act(MinecraftServer server) {
        Progress progress = Progress.of(server);
        return Story.get().quests().stream().filter(quest -> progress.step(quest.id()) >= 0)
                .mapToInt(quest -> quest.act().chars().allMatch(Character::isDigit) ? Integer.parseInt(quest.act()) : 1).max().orElse(1);
    }

    private static int notice(MinecraftServer server) {
        return (int) (of(server) * Balance.get().shine().noticeBlocksPerShine());
    }

    private static int tries(MinecraftServer server) {
        return of(server) / Balance.get().shine().spawnShine();
    }

    private static void difficulty(ServerPlayer player, int shine) {
        if (!ModList.get().isLoaded(IMPROVED_MOBS)) {
            return;
        }
        MinecraftServer server = player.server;
        String name = player.getScoreboardName();
        String command = Journey.slain(server) ? "improvedmobs difficulty pause player " + name : "improvedmobs difficulty player %s set %d".formatted(name, shine);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applied = -1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            difficulty(player, of(player.server));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Monster monster) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        int notice = notice(level.getServer());
        AttributeInstance range = monster.getAttribute(Attributes.FOLLOW_RANGE);
        if (notice == 0 || range == null) {
            return;
        }
        range.addOrReplacePermanentModifier(new AttributeModifier(NOTICE, notice, AttributeModifier.Operation.ADD_VALUE));
        try {
            GoalSelector targets = (GoalSelector) TARGET_SELECTOR.get(monster);
            for (WrappedGoal wrapped : targets.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> goal) {
                    ((TargetingConditions) TARGET_CONDITIONS.get(goal)).range(range.getValue());
                }
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Balance.Shine balance = Balance.get().shine();
        if (server.getTickCount() % (balance.spawnEverySeconds() * TICKS_PER_SECOND) != 0 || !server.getGameRules().getBoolean(Wayfarer.STORY)) {
            return;
        }
        int shine = of(server);
        if (shine != applied) {
            applied = shine;
            server.getPlayerList().getPlayers().forEach(player -> difficulty(player, shine));
        }
        int tries = tries(server);
        if (tries == 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || Shrines.inside(player)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(WATCHED)).size() >= balance.spawnLimit() + shine / LIMIT_SHINE) {
                continue;
            }
            for (int attempt = 0; attempt < tries; attempt++) {
                spawn(level, player);
            }
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = NEAR + random.nextInt(FAR - NEAR);
        BlockPos column = player.blockPosition().offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
        if (!level.isLoaded(column)) {
            return;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (Fire.nearest(level, pos.getCenter(), Balance.get().fire().circleBlocks()).isPresent()) {
            return;
        }
        Optional<MobSpawnSettings.SpawnerData> picked = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER).getRandom(random);
        if (picked.isEmpty()) {
            return;
        }
        EntityType<?> type = picked.get().type;
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos) || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)) {
            return;
        }
        Entity entity = type.create(level);
        if (entity instanceof Mob mob) {
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            level.addFreshEntity(mob);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
