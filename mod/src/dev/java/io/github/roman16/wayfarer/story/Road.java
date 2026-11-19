package io.github.roman16.wayfarer.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Belongings;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Road {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_road", Comparator.comparingLong(ChunkPos::toLong));
    private static final int OWNER = 4;
    private static final int LOAD_RADIUS = 3;
    private static final int GRAZE_CHUNKS = 2;
    private static final String PARTS = " | ";
    private static final Set<String> LAND = Set.of(Frost.LAID, Mine.LAID);
    @Nullable
    private static Packing packing;

    private Road() {}

    private record Packing(CommandSourceStack source, @Nullable ChunkPos grazing) {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("road")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.literal("pack").executes(context -> pack(context.getSource())))
                        .then(Commands.literal("follow").then(Commands.argument("save", StringArgumentType.string())
                                .executes(context -> follow(context.getSource(), Path.of(StringArgumentType.getString(context, "save")), null))
                                .then(Commands.argument("step", StringArgumentType.greedyString())
                                        .executes(context -> follow(context.getSource(), Path.of(StringArgumentType.getString(context, "save")),
                                                StringArgumentType.getString(context, "step"))))))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        packing = null;
    }

    private static int pack(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (packing != null) {
            source.sendFailure(Component.literal("road failed: it is already packing"));
            return 0;
        }
        if (server.getPlayerList().getPlayerCount() == 0) {
            source.sendFailure(Component.literal("road failed: nobody is online to pack for; bring each player in with wayfarer pretend join <name> <id>"));
            return 0;
        }
        ChunkPos grazing = Caravan.grazing(server).map(ChunkPos::new).orElse(null);
        if (grazing != null) {
            Engine.overworld(server).getChunkSource().addRegionTicket(TICKET, grazing, LOAD_RADIUS, grazing);
        }
        packing = new Packing(source, grazing);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (packing == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Engine.overworld(server);
        if (packing.grazing() != null && !loaded(level, packing.grazing())) {
            return;
        }
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        players.forEach(Belongings::leaveTent);
        List<String> parts = new ArrayList<>();
        players.forEach(player -> parts.add(player.getGameProfile().getName() + ": " + Belongings.pack(player)));
        if (Caravan.horse(server) != null) {
            parts.add(Caravan.takeAlong(level) ? "the caravan's horse travels with it" : "the caravan's horse stays behind, since it was nowhere near the caravan");
        }
        if (packing.grazing() != null) {
            level.getChunkSource().removeRegionTicket(TICKET, packing.grazing(), LOAD_RADIUS, packing.grazing());
        }
        CommandSourceStack source = packing.source();
        packing = null;
        source.sendSuccess(() -> Component.literal("road: packed" + PARTS + String.join(PARTS, parts)), false);
    }

    private static boolean loaded(ServerLevel level, ChunkPos centre) {
        for (int dx = -GRAZE_CHUNKS; dx <= GRAZE_CHUNKS; dx++) {
            for (int dz = -GRAZE_CHUNKS; dz <= GRAZE_CHUNKS; dz++) {
                if (!level.hasChunk(centre.x + dx, centre.z + dz) || !level.areEntitiesLoaded(ChunkPos.asLong(centre.x + dx, centre.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int follow(CommandSourceStack source, Path save, @Nullable String back) {
        MinecraftServer server = source.getServer();
        Path data = save.resolve("data");
        Progress old;
        CompoundTag caravan;
        try {
            if (!Files.exists(data.resolve(Progress.NAME + ".dat"))) {
                source.sendFailure(Component.literal("road failed: " + save + " holds no story"));
                return 0;
            }
            old = Progress.load(read(data.resolve(Progress.NAME + ".dat")), server.registryAccess());
            caravan = Files.exists(data.resolve(Caravan.SAVE + ".dat")) ? read(data.resolve(Caravan.SAVE + ".dat")) : new CompoundTag();
        } catch (IOException | IllegalStateException exception) {
            source.sendFailure(Component.literal("road failed: cannot read the story in " + save + ": " + exception.getMessage()));
            return 0;
        }
        Optional<Story.Quest> under = Story.get().quests().stream().filter(quest -> quest.kind().equals(Engine.MAIN) && old.open(quest.id())).findFirst();
        if (under.isEmpty()) {
            List<String> gone = old.steps.keySet().stream().filter(id -> Story.get().quest(id).isEmpty()).toList();
            source.sendFailure(Component.literal(gone.isEmpty() ? "road failed: the story has ended on this world; there is no step to carry it to"
                    : "road failed: the road on this world went through quests the story no longer has (" + String.join(", ", gone) + "), so no step of it can be carried"));
            return 0;
        }
        Story.Quest quest = under.get();
        String reached = quest.id() + "/" + quest.steps().get(old.step(quest.id())).id();
        if (back != null) {
            Optional<String> refused = behind(quest, old.step(quest.id()), reached, back);
            if (refused.isPresent()) {
                source.sendFailure(Component.literal("road failed: " + refused.get()));
                return 0;
            }
        }
        String target = back != null ? back : reached;
        return StoryDev.begin(source, target, null, new StoryDev.Way(Map.copyOf(old.choices), old.stone, () -> lay(source, old, caravan, target)));
    }

    private static Optional<String> behind(Story.Quest quest, int at, String reached, String back) {
        String[] parts = back.split("/", 2);
        if (parts.length != 2 || !parts[0].equals(quest.id())) {
            return Optional.of(back + " is not a step of " + quest.id() + ", which the road stands at");
        }
        for (int index = 0; index < quest.steps().size(); index++) {
            if (quest.steps().get(index).id().equals(parts[1])) {
                return index <= at ? Optional.empty() : Optional.of(back + " is ahead of the road, which stands at " + reached);
            }
        }
        return Optional.of(quest.id() + " has no step " + parts[1]);
    }

    private static CompoundTag read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data");
    }

    private static void lay(CommandSourceStack source, Progress old, CompoundTag caravan, String target) {
        MinecraftServer server = source.getServer();
        Progress fresh = Progress.of(server);
        String carried = target.substring(0, target.indexOf('/'));
        int begun = fresh.step(carried);
        replace(fresh.steps, old.steps);
        fresh.steps.put(carried, begun);
        Map<String, CompoundTag> grown = new LinkedHashMap<>(fresh.scratch);
        fresh.scratch.clear();
        old.scratch.forEach((key, kept) -> {
            if (Story.get().quest(key).isEmpty() && !LAND.contains(key)) {
                fresh.scratch.put(key, kept);
            }
        });
        grown.forEach((key, kept) -> {
            if (LAND.contains(key) || old.done(key)) {
                fresh.scratch.put(key, kept);
            }
        });
        CaptainsPages.carry(old, fresh);
        replace(fresh.choices, old.choices);
        replace(fresh.told, old.told);
        replace(fresh.conversations, old.conversations);
        replace(fresh.pages, old.pages);
        replace(fresh.gear, old.gear);
        replace(fresh.gearTracking, old.gearTracking);
        replace(fresh.people, old.people);
        replace(fresh.words, old.words);
        replace(fresh.rewards, old.rewards);
        replace(fresh.claims, old.claims);
        replace(fresh.gifts, old.gifts);
        replace(fresh.joined, old.joined);
        replace(fresh.named, old.named);
        replace(fresh.visited, old.visited);
        replace(fresh.seen, old.seen);
        replace(fresh.tracking, old.tracking);
        replace(fresh.trust, old.trust);
        fresh.stone = old.stone;
        long yesterday = Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY) - 1;
        fresh.gearMet.clear();
        old.gearMet.forEach((line, day) -> fresh.gearMet.put(line, Math.min(day, yesterday)));
        fresh.setDirty();
        Caravan.adoptFrom(server, caravan);
        Talk.reset();
        Journal.syncAll(server);
        long side = Story.get().quests().stream().filter(quest -> !quest.kind().equals(Engine.MAIN) && old.open(quest.id())).count();
        BlockPos at = server.getPlayerList().getPlayers().getFirst().blockPosition();
        source.sendSuccess(() -> Component.literal("road: carried to " + target + PARTS + old.choices.size() + " choices" + PARTS + side + " side quest"
                + (side == 1 ? "" : "s") + " under way" + PARTS + "the team stands at " + at.toShortString()), false);
    }

    private static <K, V> void replace(Map<K, V> into, Map<K, V> from) {
        into.clear();
        into.putAll(from);
    }

    private static <T> void replace(Collection<T> into, Collection<T> from) {
        into.clear();
        into.addAll(from);
    }
}
