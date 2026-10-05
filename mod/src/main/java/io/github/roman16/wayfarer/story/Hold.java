package io.github.roman16.wayfarer.story;

import de.markusbordihn.easynpc.data.attribute.EntityAttributes;
import de.markusbordihn.easynpc.entity.easynpc.data.AttributeDataCapable;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class Hold {
    private static final String KEEP = Wayfarer.MODID + "_keep";
    private static final String WAVE = "wave";
    private static final String FAILED = "failed";
    private static final int REACH = 96;
    private static final int TICKS_PER_SECOND = 20;
    @Nullable
    private static Running running;

    private Hold() {}

    private static final class Running {
        private final String quest;
        private final BlockPos at;
        private final Set<UUID> monsters = new HashSet<>();
        private long waveStarted = -1;

        private Running(String quest, BlockPos at) {
            this.quest = quest;
            this.at = at;
        }
    }

    public static boolean active(MinecraftServer server) {
        return running != null;
    }

    static void start(MinecraftServer server, Engine.Active active) {
        BlockPos at = Engine.at(server, active.step().text("at"));
        running = new Running(active.quest().id(), at);
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
                running = null;
                Engine.complete(server, active);
                return;
            }
            nearest(server, running.at).ifPresent(player -> {
                running.waveStarted = now;
                running.monsters.clear();
                Talk.notice(Component.literal(active.step().text("wave").replace("{wave}", String.valueOf(wave + 1))
                        .replace("{waves}", String.valueOf(hold.waves()))).withStyle(ChatFormatting.RED));
                command(server, "hordes spawnWave %s %d".formatted(player.getScoreboardName(), hold.firstWave() + hold.waveGrowth() * wave));
            });
            return;
        }
        long elapsed = now - running.waveStarted;
        long ticks = (long) hold.waveSeconds() * TICKS_PER_SECOND;
        boolean cleared = running.monsters.stream().map(level::getEntity).noneMatch(entity -> entity != null && entity.isAlive());
        if (cleared && elapsed >= TICKS_PER_SECOND || elapsed >= ticks) {
            running.waveStarted = -1;
            active.scratch().putInt(WAVE, wave + 1);
            Progress.of(server).setDirty();
        }
    }

    private static void fail(MinecraftServer server, Engine.Active active, ServerLevel level) {
        active.scratch().putBoolean(FAILED, true);
        Progress.of(server).setDirty();
        running.monsters.stream().map(level::getEntity).filter(entity -> entity != null).forEach(Entity::discard);
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

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (running == null || event.loadedFromDisk() || !(event.getEntity() instanceof Monster monster)
                || monster.distanceToSqr(running.at.getCenter()) > (double) REACH * REACH) {
            return;
        }
        monster.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(monster, Mob.class, false,
                (LivingEntity target) -> target.getTags().contains(KEEP)));
        running.monsters.add(monster.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    static void stop() {
        running = null;
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
        return Engine.players(server).stream().filter(player -> player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD)
                .min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(at.getCenter())));
    }

    private static void command(MinecraftServer server, String command) {
        if (ModList.get().isLoaded("hordes")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
    }
}
