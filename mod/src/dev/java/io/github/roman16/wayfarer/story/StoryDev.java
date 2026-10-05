package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Joining;
import io.github.roman16.wayfarer.Journey;
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class StoryDev {
    private static final JsonObject STATES = Prologue.json("/wayfarer/states.json");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("wayfarer_story", Comparator.comparingLong(ChunkPos::toLong));
    private static final int OWNER = 4;
    private static final int LOAD_RADIUS = 2;
    private static final int ROAD_BEYOND = 32;
    private static final int ROAD_SAMPLE = 16;
    private static final int[] HEADINGS = {0, 45, -45, 90, -90, 135, -135, 180};
    private static final int CAMP_REACH = 8;
    private static final int STAND_NEAR = 3;
    private static final int STAND_FAR = 4;
    private static final int RING_STEPS = 8;
    private static final int HEIGHT = 4;
    private static final int SIGN_LINES = 4;
    private static final int SIGN_WIDTH = 15;
    @Nullable
    private static Run run;

    private StoryDev() {}

    private static final class Run {
        private final CommandSourceStack source;
        @Nullable
        private final String target;
        @Nullable
        private final Path save;
        private final Map<UUID, GameType> modes = new HashMap<>();
        private BlockPos here;
        @Nullable
        private ChunkPos ticket;
        @Nullable
        private String building;
        @Nullable
        private String moved;
        private int played;

        private Run(CommandSourceStack source, @Nullable String target, @Nullable Path save, BlockPos here) {
            this.source = source;
            this.target = target;
            this.save = save;
            this.here = here;
        }
    }

    public static long seed() {
        return STATES.get("seed").getAsLong();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("story")
                        .requires(source -> source.hasPermission(OWNER))
                        .then(Commands.literal("build").then(Commands.argument("folder", StringArgumentType.string())
                                .then(Commands.argument("step", StringArgumentType.greedyString()).executes(context -> begin(context.getSource(),
                                        StringArgumentType.getString(context, "step"), Path.of(StringArgumentType.getString(context, "folder")))))))
                        .then(Commands.literal("next").executes(context -> begin(context.getSource(), null, null)))
                        .then(Commands.literal("to").then(Commands.argument("step", StringArgumentType.greedyString())
                                .executes(context -> begin(context.getSource(), StringArgumentType.getString(context, "step"), null))))));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (run == null) {
            return;
        }
        try {
            go(event.getServer());
        } catch (RuntimeException exception) {
            if (run != null) {
                fail(event.getServer(), exception.getMessage() == null ? exception.toString() : exception.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        run = null;
    }

    private static int begin(CommandSourceStack source, @Nullable String target, @Nullable Path save) {
        MinecraftServer server = source.getServer();
        if (run != null) {
            source.sendFailure(Component.literal("story failed: it is already on its way to " + (run.target == null ? "the next step" : run.target)));
            return 0;
        }
        if (server.getPlayerList().getPlayerCount() == 0) {
            source.sendFailure(Component.literal("story failed: nobody is online to walk it; bring someone in with wayfarer pretend join <name>"));
            return 0;
        }
        Optional<Engine.Active> current = Engine.tracked(server);
        if (target != null) {
            Optional<String> wrong = wrong(server, target);
            if (wrong.isPresent()) {
                source.sendFailure(Component.literal("story failed: " + wrong.get()));
                return 0;
            }
            if (save == null && current.filter(active -> active.key().equals(target) && Steps.entered(active)).isPresent()) {
                source.sendSuccess(() -> Component.literal("story: at " + target + ", where it already stood"), false);
                return 1;
            }
        }
        run = new Run(source, target, save, server.getPlayerList().getPlayers().getFirst().blockPosition());
        Talk.reset();
        current.filter(Steps::entered).ifPresent(active -> {
            run.moved = active.key();
            Steps.reset(server, active);
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            run.modes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);
        }
        return 1;
    }

    private static Optional<String> wrong(MinecraftServer server, String target) {
        String[] parts = target.split("/", 2);
        Optional<Story.Quest> quest = Story.INSTANCE.quest(parts[0]);
        int index = quest.map(found -> found.steps().stream().map(Story.Step::id).toList().indexOf(parts.length > 1 ? parts[1] : "")).orElse(-1);
        if (index < 0) {
            return Optional.of("there is no step " + target + "; a step is named quest/step, as ash/the_door is");
        }
        Progress progress = Progress.of(server);
        if (progress.done(parts[0]) || progress.open(parts[0]) && progress.step(parts[0]) > index) {
            return Optional.of(target + " is behind; the story stands at " + Engine.tracked(server).map(Engine.Active::key).orElse("its end"));
        }
        return Optional.empty();
    }

    private static void go(MinecraftServer server) {
        if (run.building != null) {
            hurry(server, run.building);
            if (!Places.ready(server, run.building)) {
                return;
            }
            run.building = null;
        }
        while (run != null && loaded(server, run.here)) {
            Optional<Engine.Active> tracked = Engine.tracked(server);
            if (tracked.isEmpty()) {
                if (run.target == null && run.played > 0) {
                    say("story: at the end, after " + steps(run.played));
                    finish(server);
                } else {
                    fail(server, run.target == null ? "the story has ended" : "the story ends before " + run.target);
                }
                return;
            }
            Engine.Active active = tracked.get();
            if (run.target == null ? run.played > 0 : active.key().equals(run.target)) {
                arrive(server, active);
                return;
            }
            if (!play(server, active)) {
                return;
            }
            run.played++;
            say("story: played " + active.key());
        }
    }

    private static boolean play(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        ServerLevel level = Engine.overworld(server);
        Progress progress = Progress.of(server);
        Balance.Clock clock = Balance.INSTANCE.clock();
        if (!active.key().equals(run.moved)) {
            Steps.moves(server, step);
            run.moved = active.key();
        }
        switch (step.kind()) {
            case "talk" -> {
                step.choices().stream().findFirst().ifPresent(choice -> Engine.choose(server, active.quest().id(), step.id(), choice));
                body(server, step.with()).ifPresent(at -> run.here = at);
            }
            case "bury" -> {
                People.find(server, step.text("whom")).ifPresent(Entity::discard);
                BlockPos sign = Prologue.grave(level);
                write(level, sign, STATES.get("stone").getAsString());
                progress.stone = STATES.get("stone").getAsString();
                run.here = sign;
            }
            case "walk" -> run.here = road(level, Engine.at(server, step.text("from")));
            case "camp" -> {
                BlockPos fire = ground(level, run.here, 0, CAMP_REACH);
                level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
                progress.place(Steps.OWN_CAMP, fire);
                run.here = fire;
            }
            case "dusk" -> {
                if (!built(server, step.text("place"))) {
                    return false;
                }
                Clock.jump(level, clock.dusk(), clock.night());
                Journey.of(server).wake();
            }
            case "reach" -> run.here = Engine.at(server, step.text("place"));
            case "rest" -> {
                BlockPos fire = Engine.at(server, step.text("at"));
                server.getPlayerList().getPlayers().forEach(player -> player.setRespawnPosition(Level.OVERWORLD, fire.above(), player.getYRot(), true, false));
                run.here = fire;
            }
            case "hold" -> Hold.stop();
            case "dawn", "sleep" -> Clock.jump(level, Steps.DAWN, clock.road());
            case "caravan" -> {
                if (!built(server, Caravan.NAME)) {
                    return false;
                }
                Clock.jump(level, clock.evening(), null);
                run.here = Engine.at(server, Caravan.NAME);
            }
            default -> {}
        }
        if (Engine.advance(server, active).isEmpty()) {
            Engine.open(server);
        }
        Talk.reset();
        return true;
    }

    private static void arrive(MinecraftServer server, Engine.Active active) {
        Story.Step step = active.step();
        ServerLevel level = Engine.overworld(server);
        if (!active.key().equals(run.moved)) {
            Steps.moves(server, step);
        }
        BlockPos subject = switch (step.kind()) {
            case "talk" -> body(server, step.with()).orElse(run.here);
            case "bury" -> body(server, step.text("whom")).orElse(run.here);
            default -> run.here;
        };
        BlockPos feet = ground(level, subject, STAND_NEAR, STAND_FAR);
        float yaw = (float) Math.toDegrees(Math.atan2(subject.getZ() - feet.getZ(), subject.getX() - feet.getX())) - 90;
        Progress progress = Progress.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setGameMode(run.modes.getOrDefault(player.getUUID(), GameType.SURVIVAL));
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
            List.copyOf(progress.rewards).forEach(reward -> Engine.claim(player, reward));
        }
        Joining.leave(server, new Joining.Place(Level.OVERWORLD, feet));
        if (run.save != null) {
            save(server, run.save);
        }
        Steps.enter(server, active);
        say("story: at " + active.key() + ", after " + steps(run.played));
        finish(server);
    }

    private static void finish(MinecraftServer server) {
        if (run.ticket != null) {
            Engine.overworld(server).getChunkSource().removeRegionTicket(TICKET, run.ticket, LOAD_RADIUS, run.ticket);
        }
        run = null;
    }

    private static void fail(MinecraftServer server, String why) {
        run.source.sendFailure(Component.literal("story failed: " + why));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = run.modes.get(player.getUUID());
            if (mode != null) {
                player.setGameMode(mode);
            }
        }
        finish(server);
    }

    private static String steps(int count) {
        return count + (count == 1 ? " step" : " steps");
    }

    private static void say(String line) {
        run.source.sendSuccess(() -> Component.literal(line), false);
    }

    private static boolean built(MinecraftServer server, String place) {
        if (Places.ready(server, place)) {
            return true;
        }
        Places.build(server, place);
        run.building = place;
        return false;
    }

    private static void hurry(MinecraftServer server, String place) {
        switch (place) {
            case Tollbridge.NAME -> Tollbridge.tick(server);
            case Caravan.NAME -> Caravan.tick(server);
            default -> throw new IllegalStateException("the story has no way to build " + place);
        }
    }

    private static boolean loaded(MinecraftServer server, BlockPos at) {
        ServerLevel level = Engine.overworld(server);
        ChunkPos chunk = new ChunkPos(at);
        if (!chunk.equals(run.ticket)) {
            if (run.ticket != null) {
                level.getChunkSource().removeRegionTicket(TICKET, run.ticket, LOAD_RADIUS, run.ticket);
            }
            level.getChunkSource().addRegionTicket(TICKET, chunk, LOAD_RADIUS, chunk);
            run.ticket = chunk;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(level, at.getX() + 0.5, at.getY() + HEIGHT, at.getZ() + 0.5, player.getYRot(), 0);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(chunk.x + dx, chunk.z + dz) || !level.areEntitiesLoaded(ChunkPos.asLong(chunk.x + dx, chunk.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<BlockPos> body(MinecraftServer server, @Nullable String person) {
        return person == null ? Optional.empty() : People.find(server, person).map(Entity::blockPosition);
    }

    private static BlockPos road(ServerLevel level, BlockPos from) {
        Unseen.Heights heights = new Unseen.Heights(level);
        int away = Balance.INSTANCE.prologue().awayBlocks() + ROAD_BEYOND;
        int beyond = away + Balance.INSTANCE.tollbridge().aheadBlocks() + ROAD_BEYOND;
        BlockPos best = null;
        int driest = -1;
        for (int heading : HEADINGS) {
            double way = Math.toRadians(heading);
            if (heights.wet(from.getX() + (int) Math.round(Math.cos(way) * away), from.getZ() + (int) Math.round(Math.sin(way) * away))) {
                continue;
            }
            int dry = 0;
            for (int distance = away; distance <= beyond; distance += ROAD_SAMPLE) {
                if (!heights.wet(from.getX() + (int) Math.round(Math.cos(way) * distance), from.getZ() + (int) Math.round(Math.sin(way) * distance))) {
                    dry++;
                }
            }
            if (dry > driest) {
                driest = dry;
                int x = from.getX() + (int) Math.round(Math.cos(way) * away);
                int z = from.getZ() + (int) Math.round(Math.sin(way) * away);
                best = new BlockPos(x, heights.surface(x, z) + 1, z);
            }
        }
        if (best == null) {
            throw new IllegalStateException("every road " + away + " blocks from " + from.toShortString() + " ends in water; pick another seed in story/states.toml");
        }
        return best;
    }

    private static BlockPos ground(ServerLevel level, BlockPos around, int nearest, int farthest) {
        for (int ring = nearest; ring <= farthest; ring++) {
            for (int step = 0; step < (ring == 0 ? 1 : RING_STEPS * ring); step++) {
                double angle = Math.PI * 2 * step / (RING_STEPS * Math.max(ring, 1));
                int x = around.getX() + (int) Math.round(Math.cos(angle) * ring);
                int z = around.getZ() + (int) Math.round(Math.sin(angle) * ring);
                for (int dy = 0; dy <= HEIGHT * 2; dy++) {
                    BlockPos feet = new BlockPos(x, around.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2), z);
                    if (standing(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        return around;
    }

    private static boolean standing(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockState under = level.getBlockState(floor);
        return under.isFaceSturdy(level, floor, Direction.UP) && !under.is(BlockTags.LOGS) && !under.is(BlockTags.LEAVES)
                && !under.is(Blocks.CAMPFIRE) && level.getFluidState(floor).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    private static void write(ServerLevel level, BlockPos sign, String words) {
        if (!(level.getBlockEntity(sign) instanceof SignBlockEntity entity)) {
            throw new IllegalStateException("the grave has no sign at " + sign.toShortString());
        }
        List<String> lines = new ArrayList<>();
        for (String word : words.split(" ")) {
            if (!lines.isEmpty() && lines.getLast().length() + 1 + word.length() <= SIGN_WIDTH) {
                lines.set(lines.size() - 1, lines.getLast() + " " + word);
            } else {
                lines.add(word);
            }
        }
        SignText text = entity.getFrontText();
        for (int line = 0; line < Math.min(lines.size(), SIGN_LINES); line++) {
            text = text.setMessage(line, Component.literal(lines.get(line)));
        }
        entity.setText(text, true);
        entity.setWaxed(true);
        entity.setChanged();
        level.sendBlockUpdated(sign, entity.getBlockState(), entity.getBlockState(), Block.UPDATE_ALL);
    }

    private static void save(MinecraftServer server, Path folder) {
        server.saveEverything(true, true, true);
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path partial = folder.resolveSibling(folder.getFileName() + ".partial");
        try {
            delete(partial);
            delete(folder);
            try (Stream<Path> files = Files.walk(world)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().equals("session.lock")) {
                        continue;
                    }
                    Path copy = partial.resolve(world.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.move(partial, folder, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot keep the world in " + folder + ": " + exception.getMessage(), exception);
        }
    }

    private static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
