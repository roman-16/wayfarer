package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.After;
import io.github.roman16.wayfarer.story.Bell;
import io.github.roman16.wayfarer.story.Deeds;
import io.github.roman16.wayfarer.story.Engine;
import io.github.roman16.wayfarer.story.Hold;
import io.github.roman16.wayfarer.story.Talk;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Nights {
    private static final String AWAKE = "awake";
    private static final String HARD_NIGHT = "hard_night";
    private static final String HORDE_MUSIC = "hold";
    private static final String NAME = "wayfarer_nights";
    private static final String HORDES = "hordes";
    private static final String CELESTIALS = "enhancedcelestials2core";
    private static final String RED_MOON = "enhancedcelestials2defaultlunarevents:blood_moon";
    private static final long TICKS_PER_DAY = 24000;
    private static final long DUSK = 12000;
    private static final long MIDNIGHT = 18000;
    private static final long MOONRISE = 12500;
    private static final long MOON_FADE = 100;
    private static final int CHECK_TICKS = 20;

    private Nights() {}

    private static final class State extends SavedData {
        long dawn = -1;
        long dusk = -1;
        long horde = -1;
        long red = -1;
        long rolled = -1;
        long hunted = -1;
        long freed = -1;
        final Set<UUID> free = new HashSet<>();

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.dawn = tag.getLong("dawn");
            state.dusk = tag.getLong("dusk");
            state.horde = tag.getLong("horde");
            state.red = tag.contains("red") ? tag.getLong("red") : -1;
            state.rolled = tag.contains("rolled") ? tag.getLong("rolled") : -1;
            state.hunted = tag.contains("hunted") ? tag.getLong("hunted") : -1;
            state.freed = tag.contains("freed") ? tag.getLong("freed") : -1;
            tag.getList("free", Tag.TAG_INT_ARRAY).forEach(id -> state.free.add(NbtUtils.loadUUID(id)));
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong("dawn", dawn);
            tag.putLong("dusk", dusk);
            tag.putLong("horde", horde);
            tag.putLong("red", red);
            tag.putLong("rolled", rolled);
            tag.putLong("hunted", hunted);
            tag.putLong("freed", freed);
            ListTag ids = new ListTag();
            free.forEach(id -> ids.add(NbtUtils.createUUID(id)));
            tag.put("free", ids);
            return tag;
        }
    }

    public static boolean sleepless(ServerPlayer player) {
        return sleepless(Journey.over(player.server), Stillness.found(player) || hunted(player.server));
    }

    public static boolean hard(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().anyMatch(Nights::sleepless);
    }

    private static boolean hunted(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return state(level).hunted == level.getDayTime() / TICKS_PER_DAY;
    }

    private static State state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), NAME);
    }

    public static boolean sleepless(boolean over, boolean found) {
        return !over && found;
    }

    public static boolean night(Level overworld) {
        return overworld.getDayTime() % TICKS_PER_DAY >= DUSK;
    }

    public static boolean fearless(ServerPlayer player) {
        return night(player.server.overworld()) && sleepless(player);
    }

    public static NightPayload forecast(ServerPlayer player) {
        return new NightPayload(sleepless(player));
    }

    public static boolean tentsShut(ServerPlayer player) {
        return (sleepless(player) || Hold.active(player.server)) && night(player.server.overworld());
    }

    public static void redMoon(MinecraftServer server) {
        ServerLevel level = server.overworld();
        State state = state(level);
        long day = level.getDayTime() / TICKS_PER_DAY;
        if (state.red == day || !ModList.get().isLoaded(CELESTIALS)) {
            return;
        }
        state.red = day;
        state.setDirty();
        command(server, "enhancedcelestials2core scheduleLunarEvent insert " + RED_MOON + " 0");
        if (level.getDayTime() % TICKS_PER_DAY >= MOONRISE) {
            level.setDayTime(level.getDayTime() + MOON_FADE);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, forecast(player));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK_TICKS != 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        State state = state(level);
        long day = level.getDayTime() / TICKS_PER_DAY;
        long time = level.getDayTime() % TICKS_PER_DAY;
        if (Clock.evening() && state.rolled != day && !Talk.busy()) {
            state.rolled = day;
            state.setDirty();
            boolean told = Engine.main(server).filter(active -> active.step().spec().has(AWAKE)).isPresent();
            if (state.red != day && !told && !Hold.active(server) && !Journey.over(server) && (After.hunted(server) || level.getRandom().nextDouble() < found(server))) {
                state.hunted = day;
                server.getPlayerList().getPlayers().stream().filter(player -> !After.sheltered(player)).forEach(Nights::uneasy);
            }
        }
        if (state.dawn != day) {
            state.dawn = day;
            state.setDirty();
            Deeds.dawn(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ModList.get().isLoaded(HORDES)) {
                    Hordes.stop(player);
                }
                if (HORDE_MUSIC.equals(Music.looping(player))) {
                    Music.stop(player);
                }
                NightPayload forecast = forecast(player);
                PacketDistributor.sendToPlayer(player, forecast);
                if (forecast.sleepless()) {
                    warn(player);
                }
            }
        }
        if (time >= DUSK && state.dusk != day) {
            state.dusk = day;
            state.setDirty();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (sleepless(player)) {
                    Firsts.first(player, "horde");
                    if (Tents.isTent(player.level().dimension())) {
                        Tents.eject(player);
                    }
                }
            }
        }
        if (time >= MIDNIGHT && state.horde != day) {
            state.horde = day;
            state.setDirty();
            if (ModList.get().isLoaded(HORDES)) {
                for (ServerPlayer player : level.players()) {
                    if (sleepless(player) && !letGo(player)) {
                        Deeds.hardNight(player);
                        command(server, "hordes start " + player.getGameProfile().getName() + " " + (TICKS_PER_DAY - time));
                        Music.play(player, HORDE_MUSIC, true);
                    }
                }
            }
        }
    }

    private static boolean letGo(ServerPlayer player) {
        ServerLevel level = player.server.overworld();
        State state = state(level);
        return state.freed == level.getDayTime() / TICKS_PER_DAY && state.free.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !night(player.server.overworld()) || !sleepless(player) || letGo(player)) {
            return;
        }
        ServerLevel level = player.server.overworld();
        State state = state(level);
        long day = level.getDayTime() / TICKS_PER_DAY;
        if (state.freed != day) {
            state.freed = day;
            state.free.clear();
        }
        state.free.add(player.getUUID());
        state.setDirty();
        if (ModList.get().isLoaded(HORDES)) {
            Hordes.stop(player);
        }
        if (HORDE_MUSIC.equals(Music.looping(player))) {
            Music.stop(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && night(player.server.overworld()) && letGo(player)) {
            Talk.tell(player, Component.translatable("wayfarer.night.let_go").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NightPayload forecast = forecast(player);
        PacketDistributor.sendToPlayer(player, forecast);
        if (forecast.sleepless()) {
            Talk.tell(player, Component.translatable("wayfarer.night.chat").withStyle(ChatFormatting.DARK_RED));
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

    private static double found(MinecraftServer server) {
        return found(Shine.of(server), Bell.present(server));
    }

    public static double found(int shine, boolean bell) {
        Balance.Shine balance = Balance.get().shine();
        return (double) shine / balance.foundShine() * (bell ? balance.bellFound() : 1);
    }

    private static void uneasy(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("wayfarer.night.uneasy")));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("wayfarer.night.title").withStyle(ChatFormatting.RED)));
        Talk.tell(player, Component.translatable("wayfarer.night.uneasy_chat").withStyle(ChatFormatting.DARK_RED));
        Sounds.play(player, HARD_NIGHT);
        sync(player);
        Firsts.first(player, "horde");
        if (Tents.isTent(player.level().dimension())) {
            Tents.eject(player);
        }
    }

    private static void warn(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("wayfarer.night.subtitle")));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("wayfarer.night.title").withStyle(ChatFormatting.RED)));
        Talk.tell(player, Component.translatable("wayfarer.night.chat").withStyle(ChatFormatting.DARK_RED));
        Sounds.play(player, HARD_NIGHT);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
