package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class EndingSpike {
    private static final String DATA = Wayfarer.MODID + "_spike_ending";
    private static final String EDF_HEALTH = "DragonHealth";
    private static final String EDF_OBJECTIVE = "EDFR.config";
    private static final int END_REACH = 192;
    private static final int GAMEMASTER = 2;
    private static final String LAST_LINE = "Far below, in the deep dark, the sculk creeps one block further. Nobody hears it.";
    private static final String PIP_SKIN = "minecraft:textures/entity/player/wide/zuri.png";
    private static final int SHARDS = 12;
    private static final Map<UUID, Vec3> charges = new HashMap<>();
    private static final Set<UUID> aimed = new HashSet<>();
    private static final Map<UUID, ServerPlayer> aimedAt = new HashMap<>();
    private static final Set<UUID> sized = new HashSet<>();
    private static int shards = SHARDS;

    private EndingSpike() {}

    public record Ending(String lines) implements CustomPacketPayload {
        public static final Type<Ending> TYPE = new Type<>(Wayfarer.id("spike_ending"));
        public static final StreamCodec<ByteBuf, Ending> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Ending::new, Ending::lines);

        @Override
        public Type<Ending> type() {
            return TYPE;
        }
    }

    public record Skin(String player, String texture) implements CustomPacketPayload {
        public static final Type<Skin> TYPE = new Type<>(Wayfarer.id("spike_skin"));
        public static final StreamCodec<ByteBuf, Skin> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Skin::player,
                ByteBufCodecs.STRING_UTF8, Skin::texture, Skin::new);

        @Override
        public Type<Skin> type() {
            return TYPE;
        }
    }

    static final class World extends SavedData {
        private boolean gone;
        private final Map<String, String> skins = new HashMap<>();

        static World of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(World::new, World::load, null), DATA);
        }

        private static World load(CompoundTag tag, HolderLookup.Provider registries) {
            World world = new World();
            world.gone = tag.getBoolean("gone");
            CompoundTag skins = tag.getCompound("skins");
            skins.getAllKeys().forEach(key -> world.skins.put(key, skins.getString(key)));
            return world;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("gone", gone);
            CompoundTag skins = new CompoundTag();
            this.skins.forEach(skins::putString);
            tag.put("skins", skins);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("ending")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("dragon").then(Commands.argument("shards", IntegerArgumentType.integer(0, SHARDS))
                                .executes(context -> dragon(context.getSource(), IntegerArgumentType.getInteger(context, "shards")))))
                        .then(Commands.literal("hunt").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> hunt(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("gone").executes(context -> gone(context.getSource(), true)))
                        .then(Commands.literal("restore").executes(context -> gone(context.getSource(), false)))
                        .then(Commands.literal("pip").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> pip(EntityArgument.getPlayer(context, "player")))))));
    }

    private static int health(MinecraftServer server) {
        Balance.Dragon balance = Balance.INSTANCE.dragon();
        int players = Math.max(1, server.getPlayerList().getPlayerCount());
        return balance.healthBase() + balance.healthPerShard() * shards + balance.healthPerPlayer() * (players - 1);
    }

    private static int dragon(CommandSourceStack source, int set) {
        shards = set;
        MinecraftServer server = source.getServer();
        ServerLevel end = server.getLevel(Level.END);
        int health = health(server);
        Objective objective = server.getScoreboard().getObjective(EDF_OBJECTIVE);
        if (objective != null) {
            server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(EDF_HEALTH), objective).set(health);
        }
        sized.clear();
        List<? extends EnderDragon> dragons = end.getEntities(EntityTypeTest.forClass(EnderDragon.class), Entity::isAlive);
        dragons.forEach(dragon -> size(dragon, health));
        List<EndCrystal> crystals = end.getEntities(EntityType.END_CRYSTAL, new AABB(0, 0, 0, 0, 0, 0).inflate(END_REACH), Entity::isAlive);
        crystals.sort(Comparator.comparingDouble(crystal -> Math.atan2(crystal.getZ(), crystal.getX())));
        int keep = Math.min(shards, crystals.size());
        int spacing = crystals.isEmpty() ? 1 : Math.max(1, crystals.size() / Math.max(1, keep));
        int kept = 0;
        for (int index = 0; index < crystals.size(); index++) {
            if (kept < keep && index % spacing == 0) {
                kept++;
            } else {
                crystals.get(index).discard();
            }
        }
        say(server, "ending: %d shards set; the dragon has %d health%s, %d of %d crystals stand, it aims %.1f blocks wide and %s".formatted(shards,
                health, objective == null ? "" : " (Ender Dragon Fight Remastered told too)", kept, crystals.size(), aimError(),
                shards <= Balance.INSTANCE.dragon().sightShards() ? "loses a player out of its line" : "follows a player out of its line"));
        return 1;
    }

    private static double aimError() {
        return Balance.INSTANCE.dragon().blindAimBlocks() * (SHARDS - shards) / (double) SHARDS;
    }

    private static void size(EnderDragon dragon, int health) {
        dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        dragon.setHealth(health);
        sized.add(dragon.getUUID());
    }

    private static int hunt(ServerPlayer player) {
        ServerLevel end = player.server.getLevel(Level.END);
        for (EnderDragon dragon : end.getEntities(EntityTypeTest.forClass(EnderDragon.class), Entity::isAlive)) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.STRAFE_PLAYER);
            dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER).setTarget(player);
            aimedAt.put(dragon.getUUID(), player);
            say(player.server, "ending: the dragon turns on %s".formatted(player.getScoreboardName()));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof DragonFireball fireball) || !(fireball.getOwner() instanceof EnderDragon dragon)
                || !aimed.add(fireball.getUUID())) {
            return;
        }
        Vec3 motion = fireball.getDeltaMovement();
        if (aimError() > 0) {
            double spread = Math.atan2(aimError(), Math.max(1, nearestDistance(fireball)));
            double yaw = (fireball.getRandom().nextDouble() * 2 - 1) * spread;
            double pitch = (fireball.getRandom().nextDouble() * 2 - 1) * spread;
            fireball.setDeltaMovement(motion.yRot((float) yaw).xRot((float) pitch));
        }
        if (aimedAt.get(dragon.getUUID()) instanceof ServerPlayer target) {
            say(target.server, "ending: a fireball flies at %s, passing %.1f blocks wide where the dragon aimed %.1f".formatted(target.getScoreboardName(),
                    wide(fireball.position(), fireball.getDeltaMovement(), target), wide(fireball.position(), motion, target)));
        }
    }

    private static double wide(Vec3 from, Vec3 direction, ServerPlayer target) {
        Vec3 toTarget = target.position().add(0, 1, 0).subtract(from);
        return toTarget.cross(direction.normalize()).length();
    }

    private static double nearestDistance(Entity entity) {
        return entity.level().players().stream().mapToDouble(player -> player.distanceTo(entity)).min().orElse(1);
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof DragonFireball fireball && fireball.getOwner() instanceof EnderDragon dragon
                && aimedAt.get(dragon.getUUID()) instanceof ServerPlayer target) {
            Vec3 at = event.getRayTraceResult().getLocation();
            say(target.server, "ending: a fireball lands %.1f blocks from %s".formatted(at.distanceTo(target.position()), target.getScoreboardName()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            return;
        }
        for (EnderDragon dragon : end.getEntities(EntityTypeTest.forClass(EnderDragon.class), Entity::isAlive)) {
            if (!sized.contains(dragon.getUUID()) && dragon.getTags().contains("ticked")) {
                size(dragon, health(server));
                say(server, "ending: a dragon rises with %d health".formatted(health(server)));
            }
            EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
            if (phase == EnderDragonPhase.CHARGING_PLAYER) {
                DragonChargePlayerPhase charge = dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER);
                Vec3 target = charge.getFlyTargetLocation();
                if (target != null && aimError() > 0 && !target.equals(charges.get(dragon.getUUID()))) {
                    Vec3 wide = target.add((dragon.getRandom().nextDouble() * 2 - 1) * aimError(), 0, (dragon.getRandom().nextDouble() * 2 - 1) * aimError());
                    charge.setTarget(wide);
                    charges.put(dragon.getUUID(), wide);
                    say(server, "ending: the dragon charges %.1f blocks wide".formatted(wide.distanceTo(target)));
                }
            }
            if ((phase == EnderDragonPhase.STRAFE_PLAYER || phase == EnderDragonPhase.CHARGING_PLAYER)
                    && shards <= Balance.INSTANCE.dragon().sightShards() && server.getTickCount() % 20 == 0) {
                ServerPlayer target = aimedAt.get(dragon.getUUID());
                if (target != null && !sees(dragon, target)) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                    say(server, "ending: the dragon loses %s behind cover".formatted(target.getScoreboardName()));
                }
            }
        }
    }

    private static boolean sees(EnderDragon dragon, ServerPlayer player) {
        HitResult hit = dragon.level().clip(new ClipContext(dragon.getEyePosition(), player.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, dragon));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static int gone(CommandSourceStack source, boolean gone) {
        World world = World.of(source.getServer());
        world.gone = gone;
        world.setDirty();
        if (gone) {
            source.getServer().getPlayerList().getPlayers().forEach(EndingSpike::showEnding);
        }
        say(source.getServer(), gone ? "ending: the world is gone" : "ending: the world is back, for testing");
        return 1;
    }

    private static void showEnding(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Ending(String.join("\n", "The frame goes still. Your friends are alone at the fire.", "", LAST_LINE)));
    }

    private static int pip(ServerPlayer player) {
        World world = World.of(player.server);
        world.skins.put(player.getUUID().toString(), PIP_SKIN);
        world.setDirty();
        PacketDistributor.sendToAllPlayers(new Skin(player.getUUID().toString(), PIP_SKIN));
        say(player.server, "ending: %s plays on as Pip".formatted(player.getScoreboardName()));
        return 1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        World world = World.of(player.server);
        world.skins.forEach((uuid, texture) -> PacketDistributor.sendToPlayer(player, new Skin(uuid, texture)));
        if (world.gone) {
            showEnding(player);
            say(player.server, "ending: %s joins a world that is gone and sees only its ending".formatted(player.getScoreboardName()));
        }
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
