package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Nights {
    private static final String NAME = "wayfarer_nights";
    private static final String BLOOD_MOON = "enhancedcelestials2defaultlunarevents:blood_moon";
    private static final String CELESTIALS = "enhancedcelestials2core";
    private static final String HORDES = "hordes";
    private static final long TICKS_PER_DAY = 24000;
    private static final long DUSK = 12000;
    private static final long MOONRISE = 12600;
    private static final long MIDNIGHT = 18000;
    private static final int CHECK_TICKS = 20;

    private Nights() {}

    private static final class State extends SavedData {
        long dawn = -1;
        long dusk = -1;
        long moon = -1;
        long horde = -1;

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.dawn = tag.getLong("dawn");
            state.dusk = tag.getLong("dusk");
            state.moon = tag.getLong("moon");
            state.horde = tag.getLong("horde");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("dawn", dawn);
            tag.putLong("dusk", dusk);
            tag.putLong("moon", moon);
            tag.putLong("horde", horde);
            return tag;
        }
    }

    public static boolean horde(long night) {
        return night % Balance.INSTANCE.nights().hordeEvery() == 0;
    }

    public static boolean bloodMoon(long night) {
        return night % Balance.INSTANCE.nights().bloodMoonEvery() == 0;
    }

    public static boolean sleepless(ServerPlayer player) {
        return !Journey.slain(player) && (horde(tonight(player.server.overworld())) || Stillness.found(player));
    }

    public static boolean redMoon(MinecraftServer server) {
        return bloodMoon(tonight(server.overworld())) && !Journey.dragonDead(server);
    }

    public static boolean night(Level overworld) {
        return overworld.getDayTime() % TICKS_PER_DAY >= DUSK;
    }

    public static boolean fearless(ServerPlayer player) {
        return night(player.server.overworld()) && (sleepless(player) || redMoon(player.server));
    }

    public static NightPayload forecast(ServerPlayer player) {
        return new NightPayload(sleepless(player), redMoon(player.server));
    }

    public static boolean tentsShut(ServerPlayer player) {
        return sleepless(player) && night(player.server.overworld());
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, forecast(player));
    }

    private static long tonight(Level overworld) {
        return overworld.getDayTime() / TICKS_PER_DAY + 1;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        State state = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), NAME);
        long day = level.getDayTime() / TICKS_PER_DAY;
        long time = level.getDayTime() % TICKS_PER_DAY;
        if (state.dawn != day) {
            state.dawn = day;
            state.setDirty();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                NightPayload forecast = forecast(player);
                PacketDistributor.sendToPlayer(player, forecast);
                if (forecast.hard()) {
                    warn(player, forecast);
                }
            }
        }
        if (time >= DUSK && state.dusk != day) {
            state.dusk = day;
            state.setDirty();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (sleepless(player)) {
                    Notes.first(player, "horde");
                    if (Tents.isTent(player.level().dimension())) {
                        Tents.eject(player);
                    }
                }
                if (redMoon(server)) {
                    Notes.first(player, "blood_moon");
                }
            }
        }
        if (time >= MOONRISE && state.moon != day && redMoon(server)) {
            state.moon = day;
            state.setDirty();
            if (ModList.get().isLoaded(CELESTIALS)) {
                command(server, "enhancedcelestials2core setLunarEvent " + BLOOD_MOON);
            }
        }
        if (time >= MIDNIGHT && state.horde != day) {
            state.horde = day;
            state.setDirty();
            if (ModList.get().isLoaded(HORDES)) {
                for (ServerPlayer player : level.players()) {
                    if (sleepless(player)) {
                        command(server, "hordes start " + player.getGameProfile().getName() + " " + (TICKS_PER_DAY - time));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NightPayload forecast = forecast(player);
        PacketDistributor.sendToPlayer(player, forecast);
        if (forecast.hard()) {
            player.sendSystemMessage(Component.translatable("wayfarer.night." + kind(forecast) + ".chat").withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() == null && sleepless(player)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("wayfarer.night.no_sleep").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void warn(ServerPlayer player, NightPayload forecast) {
        String kind = kind(forecast);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("wayfarer.night." + kind + ".subtitle")));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("wayfarer.night." + kind + ".title").withStyle(ChatFormatting.RED)));
        player.sendSystemMessage(Component.translatable("wayfarer.night." + kind + ".chat").withStyle(ChatFormatting.RED));
        player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.AMBIENT, 0.6F, 1);
    }

    private static String kind(NightPayload forecast) {
        return forecast.horde() && forecast.bloodMoon() ? "both" : forecast.horde() ? "horde" : "blood_moon";
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
