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
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class Hold {
    private static final String KEEP = Wayfarer.MODID + "_keep";
    private static final String DOOR = "door";
    private static final String DOOR_BLOW = "door_blow";
    private static final String DOOR_STATE = "door_state";
    private static final String WAVE = "wave";
    private static final String FAILED = "failed";
    private static final String TABLE = Wayfarer.MODID + ":gaze";
    private static final int BESIEGE_PRIORITY = 4;
    private static final int CRACK_STAGES = 10;
    private static final int CRACKS = -Wayfarer.MODID.hashCode();
    private static final int DOOR_SEARCH = 16;
    private static final int REACH = 96;
    private static final int REPATH_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    @Nullable
    private static Running running;

    private Hold() {}

    private static final class Running {
        private final String quest;
        private final BlockPos at;
        @Nullable
        private final BlockPos door;
        private final Map<BlockPos, Long> blows = new HashMap<>();
        private final Set<UUID> monsters = new HashSet<>();
        private final Set<UUID> wave = new HashSet<>();
        private int strain;
        private long waveStarted = -1;

        private Running(String quest, BlockPos at, @Nullable BlockPos door) {
            this.quest = quest;
            this.at = at;
            this.door = door;
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
        if (!scratch.contains(DOOR)) {
            BlockPos.findClosestMatch(at, DOOR_SEARCH, DOOR_SEARCH, pos -> level.getBlockState(pos).is(BlockTags.DOORS)).map(pos -> lower(level, pos)).ifPresent(door -> {
                scratch.putLong(DOOR, door.asLong());
                scratch.put(DOOR_STATE, NbtUtils.writeBlockState(level.getBlockState(door).setValue(DoorBlock.OPEN, false)));
                Progress.of(server).setDirty();
            });
        }
        mend(level, scratch);
        running = new Running(active.quest().id(), at, scratch.contains(DOOR) ? BlockPos.of(scratch.getLong(DOOR)) : null);
        level.getEntitiesOfClass(Monster.class, new AABB(at).inflate(REACH), Monster::isAlive).forEach(monster -> {
            besiege(monster);
            running.monsters.add(monster.getUUID());
        });
        for (String person : keep(active)) {
            People.find(server, person).ifPresent(Hold::vulnerable);
        }
        if (!ModList.get().isLoaded("hordes")) {
            Talk.notice(Component.literal("hold failed: The Hordes is not in this game, so no wave can come").withStyle(ChatFormatting.RED));
        }
    }

    static void tick(MinecraftServer server, Engine.Active active) {
        if (running == null || !running.quest.equals(active.quest().id()) || active.scratch().getBoolean(FAILED)) {
            return;
        }
        ServerLevel level = Engine.overworld(server);
        Balance.Hold hold = Balance.INSTANCE.hold();
        long now = level.getGameTime();
        int wave = active.scratch().getInt(WAVE);
        if (running.waveStarted < 0) {
            if (wave >= hold.waves()) {
                end(server, active, level);
                Engine.complete(server, active);
                return;
            }
            nearest(server, running.at).ifPresent(player -> {
                keep(active).forEach(person -> People.find(server, person).filter(body -> !body.getTags().contains(KEEP)).ifPresent(Hold::vulnerable));
                running.waveStarted = now;
                running.wave.clear();
                Talk.notice(Component.literal(active.step().text("wave").replace("{wave}", String.valueOf(wave + 1))
                        .replace("{waves}", String.valueOf(hold.waves()))).withStyle(ChatFormatting.RED));
                command(server, "hordes spawnWave %s %d %s".formatted(player.getScoreboardName(), hold.firstWave() + hold.waveGrowth() * wave, TABLE));
            });
            return;
        }
        blows(level, now, hold);
        long elapsed = now - running.waveStarted;
        long ticks = (long) hold.waveSeconds() * TICKS_PER_SECOND;
        boolean cleared = running.wave.stream().map(level::getEntity).noneMatch(entity -> entity != null && entity.isAlive());
        if (cleared && elapsed >= TICKS_PER_SECOND || elapsed >= ticks) {
            running.waveStarted = -1;
            active.scratch().putInt(WAVE, wave + 1);
            Progress.of(server).setDirty();
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
        running.monsters.stream().map(level::getEntity).filter(entity -> entity instanceof Mob mob && mob.isAlive() && !level.canSeeSky(mob.blockPosition()))
                .forEach(entity -> Daylight.crumble(level, (Mob) entity));
        for (String person : keep(active)) {
            People.find(server, person).ifPresent(Hold::guarded);
        }
        mend(level, active.scratch());
        running = null;
    }

    private static void fail(MinecraftServer server, Engine.Active active, ServerLevel level) {
        active.scratch().putBoolean(FAILED, true);
        Progress.of(server).setDirty();
        running.monsters.stream().map(level::getEntity).filter(entity -> entity != null).forEach(Entity::discard);
        mend(level, active.scratch());
        running = null;
        Talk.notice(Component.literal(active.step().text("failed")).withStyle(ChatFormatting.RED).append(" ")
                .append(Component.literal("[" + active.step().text("retry") + "]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + Wayfarer.MODID + " retry")))));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID).then(Commands.literal("retry").executes(context -> {
            MinecraftServer server = context.getSource().getServer();
            for (Engine.Active active : Engine.active(server)) {
                if (active.step().kind().equals("hold") && active.scratch().getBoolean(FAILED)) {
                    active.scratch().remove(FAILED);
                    active.scratch().remove(WAVE);
                    Progress.of(server).setDirty();
                    Tollbridge.survivors(server);
                    start(server, active);
                    return 1;
                }
            }
            context.getSource().sendFailure(Component.translatable("wayfarer.hold.nothing"));
            return 0;
        })));
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
        if (running != null && event.getNewAboutToBeSetTarget() != null && event.getNewAboutToBeSetTarget().getTags().contains(KEEP)) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (running == null || !(event.getEntity() instanceof Monster monster) || monster.distanceToSqr(running.at.getCenter()) > (double) REACH * REACH) {
            return;
        }
        besiege(monster);
        running.monsters.add(monster.getUUID());
        if (!event.loadedFromDisk()) {
            running.wave.add(monster.getUUID());
        }
    }

    private static void besiege(Monster monster) {
        if (monster.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof Besiege)) {
            return;
        }
        monster.targetSelector.addGoal(0, new Kept(monster));
        monster.goalSelector.addGoal(BESIEGE_PRIORITY, new Besiege(monster));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    static void stop() {
        running = null;
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
            living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Balance.INSTANCE.hold().keepHealth());
            living.setHealth(living.getMaxHealth());
        }
    }

    private static List<String> keep(Engine.Active active) {
        List<String> keep = new ArrayList<>();
        active.step().spec().getAsJsonArray("keep").forEach(person -> keep.add(person.getAsString()));
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
