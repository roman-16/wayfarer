package io.github.roman16.wayfarer.story;

import de.markusbordihn.easynpc.data.attribute.EntityAttributes;
import de.markusbordihn.easynpc.entity.easynpc.data.AttributeDataCapable;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Daylight;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Sounds;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Hold {
    private static final String KEEP = Wayfarer.MODID + "_keep";
    private static final String DOOR = "door";
    private static final String DOOR_BLOW = "door_blow";
    private static final String DOOR_STATE = "door_state";
    private static final String WAVE = "wave";
    private static final String FAILED = "failed";
    private static final String RETRY = "retry";
    private static final String TABLE = Wayfarer.MODID + ":gaze";
    private static final String TABLE_KEY = "table";
    private static final String KEEP_KEY = "keep";
    private static final String FROM = "from";
    private static final String FOES = "foes";
    private static final String CROSSED = "crossed";
    private static final String HORN = "gazebound_horn";
    private static final String RETREAT = Wayfarer.MODID + "_retreat";
    private static final String WAVE_SPAWN = Wayfarer.MODID + "_wave";
    private static final int SPREAD = 3;
    private static final int BESIEGE_PRIORITY = 4;
    private static final int CRACK_STAGES = 10;
    private static final int CRACKS = -Wayfarer.MODID.hashCode();
    private static final int DOOR_SEARCH = 16;
    private static final int REACH = 96;
    private static final String OUTLINED = Wayfarer.MODID + "_besieging";
    private static final int REPATH_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    @Nullable
    private static Running running;
    private static final Map<UUID, Long> retreating = new HashMap<>();

    private Hold() {}

    private static final class Running {
        private final String quest;
        private final BlockPos at;
        private final boolean across;
        @Nullable
        private final BlockPos door;
        private final Map<BlockPos, Long> blows = new HashMap<>();
        private final Set<UUID> monsters = new HashSet<>();
        private final long started;
        private boolean spawned;
        private int strain;
        private long waveStarted = -1;

        private Running(String quest, BlockPos at, @Nullable BlockPos door, boolean across, long started) {
            this.quest = quest;
            this.at = at;
            this.across = across;
            this.door = door;
            this.started = started;
        }
    }

    private static final class Kept extends NearestAttackableTargetGoal<Mob> {
        private Kept(Mob mob) {
            super(mob, Mob.class, false, (LivingEntity target) -> target.getTags().contains(KEEP));
        }

        @Override
        protected AABB getTargetSearchArea(double distance) {
            return mob.getBoundingBox().inflate(distance);
        }
    }

    private static final class Besiege extends Goal {
        private final Mob mob;
        private int repath;

        private Besiege(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return running != null && mob.getTarget() == null;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (running != null && repath-- <= 0) {
                repath = REPATH_TICKS;
                BlockPos goal = running.door == null ? running.at : running.door;
                mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    public static boolean active(MinecraftServer server) {
        return running != null;
    }

    static void start(MinecraftServer server, Engine.Active active) {
        BlockPos at = Engine.at(server, active.step().text("at"));
        ServerLevel level = Engine.overworld(server);
        CompoundTag scratch = active.scratch();
        if (!scratch.contains(DOOR) && active.step().spec().has(DOOR)) {
            BlockPos.findClosestMatch(at, DOOR_SEARCH, DOOR_SEARCH, pos -> level.getBlockState(pos).is(BlockTags.DOORS)).map(pos -> lower(level, pos)).ifPresent(door -> {
                scratch.putLong(DOOR, door.asLong());
                scratch.put(DOOR_STATE, NbtUtils.writeBlockState(level.getBlockState(door).setValue(DoorBlock.OPEN, false)));
                Progress.of(server).setDirty();
            });
        }
        mend(level, scratch);
        if (scratch.contains(DOOR)) {
            BlockPos door = BlockPos.of(scratch.getLong(DOOR));
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof DoorBlock shut) {
                shut.setOpen(null, level, state, door, false);
            }
        }
        running = new Running(active.quest().id(), at, scratch.contains(DOOR) ? BlockPos.of(scratch.getLong(DOOR)) : null, crossing(active), level.getGameTime());
        level.getEntitiesOfClass(Monster.class, new AABB(at).inflate(REACH), Hold::besieges).forEach(monster -> {
            besiege(monster);
            running.monsters.add(monster.getUUID());
        });
        if (crossing(active) && scratch.getBoolean(CROSSED)) {
            Ford.lay(server);
        } else if (crossing(active)) {
            Ford.cross(server);
            Sounds.play(level, Engine.at(server, active.step().text(FROM)).getCenter(), HORN);
        }
        for (String person : keep(active)) {
            People.find(server, person).ifPresent(Hold::vulnerable);
        }
        if (!crossing(active) && !ModList.get().isLoaded("hordes")) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("hold failed: The Hordes is not in this game, so no wave can come").withStyle(ChatFormatting.RED), false);
        }
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        if (running == null || !running.quest.equals(active.quest().id()) || active.scratch().getBoolean(FAILED)) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.Hold hold = Balance.get().hold();
        Balance.Crossing crossing = Balance.get().crossing();
        boolean across = crossing(active);
        int waves = across ? crossing.waves() : hold.waves();
        long now = level.getGameTime();
        int wave = active.scratch().getInt(WAVE);
        if (!defended(server)) {
            if (running.waveStarted >= 0) {
                running.waveStarted++;
            }
            return;
        }
        if (running.waveStarted < 0) {
            if (wave >= waves) {
                end(server, active, level);
                Engine.complete(server, active);
                return;
            }
            if (across && (!Ford.crossed(server) || Talk.busy())) {
                return;
            }
            if (!across && wave == 0 && (now - running.started < (long) hold.calmSeconds() * TICKS_PER_SECOND || Talk.busy())) {
                return;
            }
            nearest(server, running.at).ifPresent(player -> {
                keep(active).forEach(person -> People.find(server, person).filter(body -> !body.getTags().contains(KEEP)).ifPresent(Hold::vulnerable));
                running.waveStarted = now;
                running.spawned = false;
                Talk.notice(Component.literal(active.step().text("wave").replace("{wave}", String.valueOf(wave + 1))
                        .replace("{waves}", String.valueOf(waves))).withStyle(ChatFormatting.DARK_RED));
                if (across) {
                    active.scratch().putBoolean(CROSSED, true);
                    Progress.of(server).setDirty();
                    cross(server, active, level, wave, crossing);
                } else {
                    long team = Math.max(1, Engine.players(server).stream().filter(member -> member.level().dimension() == Level.OVERWORLD).count());
                    command(server, "hordes spawnWave %s %d %s".formatted(player.getScoreboardName(), (hold.firstWave() + hold.waveGrowth() * wave) * team,
                            active.step().spec().has(TABLE_KEY) ? Wayfarer.MODID + ":" + active.step().text(TABLE_KEY) : TABLE));
                }
            });
            return;
        }
        blows(level, now, hold);
        long elapsed = now - running.waveStarted;
        long ticks = (long) (across ? crossing.waveSeconds() : hold.waveSeconds()) * TICKS_PER_SECOND;
        boolean cleared = running.spawned && level.getEntitiesOfClass(Monster.class, new AABB(running.at).inflate(REACH),
                monster -> monster.isAlive() && running.monsters.contains(monster.getUUID())).isEmpty();
        if (cleared && elapsed >= TICKS_PER_SECOND || elapsed >= ticks) {
            running.waveStarted = -1;
            active.scratch().putInt(WAVE, wave + 1);
            Progress.of(server).setDirty();
        }
    }

    private static boolean defended(MinecraftServer server) {
        return Engine.players(server).stream().anyMatch(player -> player.isAlive() && player.level().dimension() == Level.OVERWORLD
                && player.distanceToSqr(running.at.getCenter()) <= (double) REACH * REACH);
    }

    private static boolean crossing(Engine.Active active) {
        return active.step().spec().has(FROM);
    }

    private static void cross(MinecraftServer server, Engine.Active active, ServerLevel level, int wave, Balance.Crossing crossing) {
        BlockPos from = Engine.at(server, active.step().text(FROM));
        List<String> foes = new ArrayList<>();
        active.step().spec().getAsJsonArray(FOES).forEach(foe -> foes.add(foe.getAsString()));
        long players = Math.max(1, Engine.players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD).count());
        long count = (crossing.firstWave() + (long) crossing.waveGrowth() * wave) * players;
        for (int index = 0; index < count; index++) {
            int which = index;
            EntityType.byString(foes.get(index % foes.size())).map(type -> type.create(level)).ifPresent(entity -> {
                if (!(entity instanceof Mob mob)) {
                    return;
                }
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        from.offset(level.random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, level.random.nextInt(SPREAD * 2 + 1) - SPREAD));
                mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
                if (wave == 0 && which == 0 && mob instanceof PatrollingMonster patrol) {
                    patrol.setPatrolLeader(true);
                }
                EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
                mob.setPersistenceRequired();
                mob.addTag(RETREAT);
                level.addFreshEntity(mob);
            });
        }
    }

    private static void retreat(MinecraftServer server, Engine.Active active, ServerLevel level) {
        Sounds.play(level, Engine.at(server, active.step().text(FROM)).getCenter(), HORN);
        BlockPos home = Progress.of(server).place(Ford.OUTPOST).orElse(Engine.at(server, active.step().text(FROM)));
        long until = level.getGameTime() + (long) Balance.get().crossing().retreatSeconds() * TICKS_PER_SECOND;
        running.monsters.stream().map(level::getEntity).filter(entity -> entity instanceof Mob mob && mob.isAlive() && mob.getTags().contains(RETREAT))
                .forEach(entity -> {
                    Mob mob = (Mob) entity;
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
                    retreating.put(mob.getUUID(), until);
                });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (retreating.isEmpty()) {
            return;
        }
        ServerLevel level = Engine.overworld(event.getServer());
        long now = level.getGameTime();
        for (Map.Entry<UUID, Long> entry : List.copyOf(retreating.entrySet())) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                retreating.remove(entry.getKey());
            } else if (now >= entry.getValue()) {
                mob.discard();
                retreating.remove(entry.getKey());
            } else if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
        }
    }

    private static void blows(ServerLevel level, long now, Balance.Hold hold) {
        Map<BlockPos, Integer> beating = new HashMap<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(running.at).inflate(REACH), mob -> Fire.fears(mob) && mob.getTarget() != null)) {
            BlockPos.betweenClosedStream(mob.getBoundingBox().inflate(1)).filter(pos -> level.getBlockState(pos).is(BlockTags.DOORS)
                    || level.getBlockState(pos).is(BlockTags.TRAPDOORS)).findFirst().map(pos -> lower(level, pos))
                    .ifPresent(door -> beating.merge(door, 1, Integer::sum));
        }
        beating.forEach((door, count) -> {
            if (now - running.blows.getOrDefault(door, Long.MIN_VALUE / 2) >= hold.blowTicks()) {
                running.blows.put(door, now);
                Sounds.play(level, door.getCenter(), DOOR_BLOW);
                if (door.equals(running.door)) {
                    strain(level, count, hold.doorStrength());
                }
            }
        });
    }

    private static void strain(ServerLevel level, int count, int strength) {
        running.strain += count;
        if (running.strain < strength) {
            crack(level, running.door, running.strain * CRACK_STAGES / strength);
            return;
        }
        crack(level, running.door, -1);
        level.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, running.door, 0);
        level.destroyBlock(running.door, false);
    }

    private static void crack(ServerLevel level, BlockPos door, int stage) {
        level.destroyBlockProgress(CRACKS, door, stage);
        level.destroyBlockProgress(CRACKS - 1, door.above(), stage);
    }

    private static BlockPos lower(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below().immutable() : pos.immutable();
    }

    private static void mend(ServerLevel level, CompoundTag scratch) {
        if (!scratch.contains(DOOR)) {
            return;
        }
        BlockPos door = BlockPos.of(scratch.getLong(DOOR));
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), scratch.getCompound(DOOR_STATE));
        crack(level, door, -1);
        if (!level.getBlockState(door).is(state.getBlock())) {
            level.setBlock(door, state, Block.UPDATE_ALL);
            level.setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    private static void end(MinecraftServer server, Engine.Active active, ServerLevel level) {
        if (crossing(active)) {
            retreat(server, active, level);
        }
        running.monsters.stream().map(level::getEntity).filter(entity -> entity instanceof Mob mob && mob.isAlive() && !mob.getTags().contains(RETREAT)
                        && !level.canSeeSky(mob.blockPosition()))
                .forEach(entity -> Daylight.crumble(level, (Mob) entity));
        for (String person : keep(active)) {
            People.find(server, person).ifPresent(Hold::guarded);
        }
        mend(level, active.scratch());
        unoutline(level);
        running = null;
    }

    private static void fail(MinecraftServer server, Engine.Active active, ServerLevel level) {
        active.scratch().putBoolean(FAILED, true);
        Progress.of(server).setDirty();
        unoutline(level);
        running.monsters.stream().map(level::getEntity).filter(entity -> entity != null).forEach(Entity::discard);
        mend(level, active.scratch());
        running = null;
        Talk.notice(Component.literal(active.step().text("failed")).withStyle(ChatFormatting.DARK_RED));
        offerRetry(server, active);
    }

    static void resume(MinecraftServer server, Engine.Active active) {
        if (active.scratch().getBoolean(FAILED)) {
            offerRetry(server, active);
        } else {
            start(server, active);
        }
    }

    private static void offerRetry(MinecraftServer server, Engine.Active active) {
        Talk.ask(new Talk.Offer(active.quest().id(), null, List.of(new Story.Choice(RETRY, active.step().text("retry"), "", null, null, null)), null,
                (player, choice) -> retry(server, active)));
    }

    private static void retry(MinecraftServer server, Engine.Active active) {
        active.scratch().remove(FAILED);
        active.scratch().remove(WAVE);
        active.scratch().remove(CROSSED);
        Progress.of(server).setDirty();
        if (crossing(active)) {
            Ford.lay(server);
        } else if (active.step().spec().has(DOOR)) {
            Tollbridge.survivors(server);
        } else {
            Steps.moves(server, active.step());
        }
        start(server, active);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (running == null || !event.getEntity().getTags().contains(KEEP) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        Engine.active(server).stream().filter(active -> active.quest().id().equals(running.quest) && active.step().kind().equals("hold"))
                .findFirst().ifPresent(active -> fail(server, active, level));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (retreating.containsKey(event.getEntity().getUUID()) && event.getNewAboutToBeSetTarget() != null) {
            event.setCanceled(true);
            return;
        }
        if (running != null && event.getNewAboutToBeSetTarget() != null && event.getNewAboutToBeSetTarget().getTags().contains(KEEP)) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(RETREAT)) {
            event.setCanceled(true);
            return;
        }
        if (running == null || !(event.getEntity() instanceof Monster monster) || !besieges(monster)
                || !running.across && !monster.getTags().contains(WAVE_SPAWN) || monster.distanceToSqr(running.at.getCenter()) > (double) REACH * REACH) {
            return;
        }
        besiege(monster);
        running.monsters.add(monster.getUUID());
        if (!event.loadedFromDisk() && running.waveStarted >= 0) {
            running.spawned = true;
        }
    }

    private static boolean besieges(Monster monster) {
        return monster.isAlive() && !monster.getTags().contains(People.TAG) && (!running.across || monster.getTags().contains(RETREAT));
    }

    public static void wave(Mob mob) {
        if (running != null && !running.across) {
            mob.addTag(WAVE_SPAWN);
        }
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (running != null && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && event.getSpawnType() != MobSpawnType.EVENT && Fire.fears(event.getEntity())
                && event.getEntity().distanceToSqr(running.at.getCenter()) <= (double) REACH * REACH) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    private static void besiege(Monster monster) {
        outline(monster, true);
        if (monster.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof Besiege)) {
            return;
        }
        monster.targetSelector.addGoal(0, new Kept(monster));
        monster.goalSelector.addGoal(BESIEGE_PRIORITY, new Besiege(monster));
    }

    static void outline(Entity monster, boolean on) {
        Scoreboard scoreboard = monster.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(OUTLINED);
        if (team == null) {
            team = scoreboard.addPlayerTeam(OUTLINED);
            team.setColor(ChatFormatting.DARK_RED);
        }
        monster.setGlowingTag(on);
        if (on) {
            scoreboard.addPlayerToTeam(monster.getStringUUID(), team);
        } else if (team.getPlayers().contains(monster.getStringUUID())) {
            scoreboard.removePlayerFromTeam(monster.getStringUUID(), team);
        }
    }

    private static void unoutline(ServerLevel level) {
        running.monsters.stream().map(level::getEntity).filter(entity -> entity != null).forEach(entity -> outline(entity, false));
        PlayerTeam team = level.getScoreboard().getPlayerTeam(OUTLINED);
        if (team != null) {
            level.getScoreboard().removePlayerTeam(team);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    static void stop() {
        running = null;
        retreating.clear();
    }

    private static void guarded(Entity body) {
        body.setInvulnerable(true);
        body.removeTag(KEEP);
        if (body instanceof AttributeDataCapable<?> npc) {
            EntityAttributes attributes = npc.getEntityAttributes();
            attributes.setCombatAttributes(attributes.getCombatAttributes().withIsAttackableByMonsters(false).withIsInvulnerable(true));
            npc.setEntityAttributes(attributes);
            npc.refreshEntityAttributes();
        }
    }

    private static void vulnerable(Entity body) {
        body.setInvulnerable(false);
        body.addTag(KEEP);
        if (body instanceof AttributeDataCapable<?> npc) {
            EntityAttributes attributes = npc.getEntityAttributes();
            attributes.setCombatAttributes(attributes.getCombatAttributes().withIsAttackableByMonsters(true).withIsInvulnerable(false));
            npc.setEntityAttributes(attributes);
            npc.refreshEntityAttributes();
        }
        if (body instanceof LivingEntity living && living.getAttribute(Attributes.MAX_HEALTH) != null) {
            living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Balance.get().hold().keepHealth());
            living.setHealth(living.getMaxHealth());
        }
        if (body instanceof LivingEntity living && living.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            living.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        }
    }

    private static List<String> keep(Engine.Active active) {
        List<String> keep = new ArrayList<>();
        if (active.step().spec().has(KEEP_KEY)) {
            active.step().spec().getAsJsonArray(KEEP_KEY).forEach(person -> keep.add(person.getAsString()));
        }
        return keep;
    }

    private static Optional<ServerPlayer> nearest(MinecraftServer server, BlockPos at) {
        return Engine.players(server).stream().filter(player -> player.level().dimension() == Level.OVERWORLD)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(at.getCenter())));
    }

    private static void command(MinecraftServer server, String command) {
        if (ModList.get().isLoaded("hordes")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
    }
}
