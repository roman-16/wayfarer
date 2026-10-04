package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class GiftSpike {
    private static final int GAMEMASTER = 2;
    private static final int LOOK_TICKS = 20;
    private static final int PROVOKED_TICKS = 4 * 20;
    private static final int WATCH_BLOCKS = 32;
    private static final Map<UUID, Integer> charges = new HashMap<>();
    private static final Set<UUID> hushed = new HashSet<>();

    private GiftSpike() {}

    public record Flare() implements CustomPacketPayload {
        public static final Type<Flare> TYPE = new Type<>(Wayfarer.id("spike_flare"));
        public static final StreamCodec<ByteBuf, Flare> STREAM_CODEC = StreamCodec.unit(new Flare());

        @Override
        public Type<Flare> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("gift")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("hush").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> hush(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("flare").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> giveFlare(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("crouch").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> crouch(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("hunted").executes(context -> hunted(context.getSource())))));
    }

    private static int hush(ServerPlayer player) {
        boolean on = hushed.add(player.getUUID());
        if (!on) {
            hushed.remove(player.getUUID());
        }
        say(player.server, "hush: %s %s Hush".formatted(player.getScoreboardName(), on ? "has" : "no longer has"));
        return 1;
    }

    private static int giveFlare(ServerPlayer player) {
        boolean on = !charges.containsKey(player.getUUID());
        if (on) {
            charges.put(player.getUUID(), Balance.INSTANCE.flare().charges());
        } else {
            charges.remove(player.getUUID());
        }
        say(player.server, "flare: %s %s the Flare".formatted(player.getScoreboardName(), on ? "has" : "no longer has"));
        return 1;
    }

    private static int crouch(ServerPlayer player) {
        player.setShiftKeyDown(!player.isShiftKeyDown());
        say(player.server, "hush: %s %s".formatted(player.getScoreboardName(), player.isShiftKeyDown() ? "crouches" : "stands up"));
        return 1;
    }

    private static int hunted(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            List<Mob> near = player.serverLevel().getEntities(EntityTypeTest.forClass(Mob.class), new AABB(player.blockPosition()).inflate(WATCH_BLOCKS),
                    Fire::fears);
            long hunting = near.stream().filter(mob -> mob.getTarget() == player).count();
            say(source.getServer(), "hush: %d of the gaze's creatures near %s, %d hunting them".formatted(near.size(), player.getScoreboardName(), hunting));
        }
        return 1;
    }

    private static boolean hidden(ServerPlayer player, LivingEntity hunter) {
        return hushed.contains(player.getUUID()) && player.isShiftKeyDown() && !provoked(hunter, player);
    }

    private static boolean provoked(LivingEntity hunter, ServerPlayer player) {
        return hunter.getLastHurtByMob() == player && hunter.tickCount - hunter.getLastHurtByMobTimestamp() < PROVOKED_TICKS;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (Fire.fears(event.getEntity()) && event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && hidden(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % LOOK_TICKS != 0 || hushed.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<? extends Mob> losing = level.getEntities(EntityTypeTest.forClass(Mob.class),
                    mob -> Fire.fears(mob) && mob.getTarget() instanceof ServerPlayer player && hidden(player, mob));
            Map<ServerPlayer, Long> lost = losing.stream().collect(Collectors.groupingBy(mob -> (ServerPlayer) mob.getTarget(), Collectors.counting()));
            for (Mob mob : losing) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            lost.forEach((player, count) -> say(server, "hush: %d of the gaze's creatures lost %s".formatted(count, player.getScoreboardName())));
        }
    }

    public static void flare(ServerPlayer player) {
        Integer left = charges.get(player.getUUID());
        if (left == null) {
            say(player.server, "flare: %s has no Flare".formatted(player.getScoreboardName()));
            return;
        }
        if (left == 0) {
            player.displayClientMessage(Component.literal("The ember is spent").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            say(player.server, "flare: %s's ember is spent".formatted(player.getScoreboardName()));
            return;
        }
        charges.put(player.getUUID(), left - 1);
        ServerLevel level = player.serverLevel();
        int reach = Balance.INSTANCE.flare().reachBlocks();
        List<Mob> crumbled = level.getEntities(EntityTypeTest.forClass(Mob.class), new AABB(player.blockPosition()).inflate(reach),
                mob -> Fire.fears(mob) && mob.distanceToSqr(player) <= reach * reach);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY(1), player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.5), player.getZ(), 128, reach / 4.0, 1, reach / 4.0, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1, 0.6F);
        for (Mob mob : crumbled) {
            level.sendParticles(ParticleTypes.WHITE_ASH, mob.getX(), mob.getY(0.5), mob.getZ(), 40, mob.getBbWidth() / 2, mob.getBbHeight() / 2,
                    mob.getBbWidth() / 2, 0.02);
            mob.discard();
        }
        say(player.server, "flare: %s flared, %d of the gaze's creatures within %d blocks crumbled".formatted(player.getScoreboardName(),
                crumbled.size(), reach));
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffectInstance().is(Wayfarer.WARM) && charges.containsKey(player.getUUID())) {
            int full = Balance.INSTANCE.flare().charges();
            if (charges.put(player.getUUID(), full) < full) {
                say(player.server, "flare: %s rested at a fire, and the ember is full again".formatted(player.getScoreboardName()));
            }
        }
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    @EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
    public static final class Key {
        private static final KeyMapping FLARE = new KeyMapping("key.wayfarer.flare", GLFW.GLFW_KEY_F6, "key.categories.misc");

        private Key() {}

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(FLARE);
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            while (FLARE.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    PacketDistributor.sendToServer(new Flare());
                }
            }
        }
    }
}
