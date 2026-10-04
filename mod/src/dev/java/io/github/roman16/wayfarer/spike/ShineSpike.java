package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Shrines;
import io.github.roman16.wayfarer.Wayfarer;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class ShineSpike {
    private static final int FAR = 48;
    private static final int GAMEMASTER = 2;
    private static final int LIMIT_SHINE = 4;
    private static final int LISTED = 6;
    private static final int NEAR = 24;
    private static final ResourceLocation NOTICE = Wayfarer.id("shine_notice");
    private static final Field TARGET_CONDITIONS = field(NearestAttackableTargetGoal.class, "targetConditions");
    private static final Field TARGET_SELECTOR = field(Mob.class, "targetSelector");
    private static final int TICKS_PER_SECOND = 20;
    private static final int WATCHED = 64;
    private static int shine;

    private ShineSpike() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("shine")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("set").then(Commands.argument("act", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("shards", IntegerArgumentType.integer(0, 12))
                                        .executes(context -> set(context.getSource(), IntegerArgumentType.getInteger(context, "act"),
                                                IntegerArgumentType.getInteger(context, "shards"), false))
                                        .then(Commands.literal("remedy").executes(context -> set(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "act"), IntegerArgumentType.getInteger(context, "shards"), true))))))
                        .then(Commands.literal("watch").executes(context -> watch(context.getSource())))));
    }

    private static int set(CommandSourceStack source, int act, int shards, boolean remedy) {
        Balance.Shine balance = Balance.INSTANCE.shine();
        shine = Math.max(0, balance.act().get(act - 1) + shards * balance.perShard() - (remedy ? balance.remedy() : 0));
        MinecraftServer server = source.getServer();
        server.getPlayerList().getPlayers().forEach(ShineSpike::difficulty);
        say(server, "shine: act %d, %d shards%s: %d; Improved Mobs' difficulty %d for %d players; monsters notice from %d blocks farther; %d more of them tried every %d s in the dark"
                .formatted(act, shards, remedy ? ", Agna's remedy" : "", shine, shine, server.getPlayerList().getPlayerCount(), notice(),
                        tries(), balance.spawnEverySeconds()));
        return 1;
    }

    private static int notice() {
        return (int) (shine * Balance.INSTANCE.shine().noticeBlocksPerShine());
    }

    private static int tries() {
        return shine / Balance.INSTANCE.shine().spawnShine();
    }

    private static void difficulty(ServerPlayer player) {
        MinecraftServer server = player.server;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "improvedmobs difficulty player %s set %d".formatted(player.getScoreboardName(), shine));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            difficulty(player);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Monster monster) || notice() == 0) {
            return;
        }
        AttributeInstance range = monster.getAttribute(Attributes.FOLLOW_RANGE);
        if (range == null) {
            return;
        }
        range.addOrReplacePermanentModifier(new AttributeModifier(NOTICE, notice(), AttributeModifier.Operation.ADD_VALUE));
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

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Balance.Shine balance = Balance.INSTANCE.shine();
        if (tries() == 0 || server.getTickCount() % (balance.spawnEverySeconds() * TICKS_PER_SECOND) != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || Shrines.inside(player)) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            int near = level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(WATCHED)).size();
            if (near >= balance.spawnLimit() + shine / LIMIT_SHINE) {
                continue;
            }
            int born = 0;
            for (int attempt = 0; attempt < tries(); attempt++) {
                born += spawn(level, player) ? 1 : 0;
            }
            say(server, "shine: %d of %d tried were born around %s, %d monsters within %d blocks before".formatted(born, tries(),
                    player.getScoreboardName(), near, WATCHED));
        }
    }

    private static boolean spawn(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = NEAR + random.nextInt(FAR - NEAR);
        BlockPos column = player.blockPosition().offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
        if (!level.isLoaded(column)) {
            return false;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (Fire.nearest(level, pos.getCenter(), Balance.INSTANCE.fire().circleBlocks()).isPresent()) {
            return false;
        }
        Optional<MobSpawnSettings.SpawnerData> picked = level.getBiome(pos).value().getMobSettings().getMobs(MobCategory.MONSTER).getRandom(random);
        if (picked.isEmpty()) {
            return false;
        }
        EntityType<?> type = picked.get().type;
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos) || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)) {
            return false;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        return level.addFreshEntity(mob);
    }

    private static int watch(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Monster> monsters = player.serverLevel().getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(WATCHED));
            long hunting = monsters.stream().filter(monster -> monster.getTarget() == player).count();
            List<String> nearest = monsters.stream().sorted((a, b) -> Double.compare(a.distanceTo(player), b.distanceTo(player))).limit(LISTED)
                    .map(monster -> "%s %d blocks away noticing from %.0f%s".formatted(monster.getType().getDescription().getString(),
                            (int) monster.distanceTo(player), monster.getAttributeValue(Attributes.FOLLOW_RANGE),
                            monster.getTarget() == null ? "" : " hunting " + monster.getTarget().getName().getString()))
                    .toList();
            say(server, "shine: %s has %d monsters within %d blocks, %d hunting them: %s".formatted(player.getScoreboardName(),
                    monsters.size(), WATCHED, hunting, String.join(", ", nearest)));
        }
        return 1;
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
