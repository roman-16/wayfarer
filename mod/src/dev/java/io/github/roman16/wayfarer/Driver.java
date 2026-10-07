package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.client.Hud;
import io.github.roman16.wayfarer.story.StoryDev;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
public final class Driver {
    private static final boolean SHOWROOM = Boolean.getBoolean(Wayfarer.MODID + ".showroom");
    private static final String WORLD_SUFFIX = Boolean.getBoolean(Wayfarer.MODID + ".lean") ? "-lean" : "";
    private static final String PLAY_WORLD = "play" + WORLD_SUFFIX;
    private static final String SHOWROOM_WORLD = "showroom" + WORLD_SUFFIX;
    private static final String STATE = System.getProperty(Wayfarer.MODID + ".state", "");
    private static final String STATE_CACHE = System.getProperty(Wayfarer.MODID + ".state.cache", "");
    private static final String STATE_WORLD = "state" + WORLD_SUFFIX;
    private static final String STORY_LINE = "chat: story: ";
    private static final String STORY_AT = STORY_LINE + "at ";
    private static final long SEED = 20260929L;
    private static final String DISTANT_HORIZONS = "distanthorizons";
    private static final Pattern FAILED = Pattern.compile("^[^:<]+ failed: ");
    private static final int FIND_LIMIT = 20;
    private static final int FIND_RADIUS = 32;
    private static final int SETTLE_CHUNKS = 3;
    private static final int SETTLE_FRAMES = 2;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final long PERF_NANOS = 1_000_000_000L;
    private static final long STEP_LIMIT_NANOS = 60_000_000_000L;
    private static final Field CLICK_COUNT = clickCount();
    private static final Map<String, Integer> BUTTONS = Map.of("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, "middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "right", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final Method MOUSE_MOVE = mouse("onMove", long.class, double.class, double.class);
    private static final Method MOUSE_PRESS = mouse("onPress", long.class, int.class, int.class, int.class);
    private static final String EXPERIMENTAL_WARNING = "selectWorld.backupQuestion.experimental";
    private static final String LOAD_WITHOUT_BACKUP = "selectWorld.backupJoinSkipButton";
    private static final Set<String> PLAYER_STEPS = Set.of("beside", "clear", "fly", "hud", "inspect", "interact", "key", "person", "place", "sky", "spawn", "state", "story", "workshop");
    private static final double INTERACT_REACH = 8;
    private static final int IDLE_FPS = 5;
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final String SENTINEL = "execute if entity @s";
    private static final String SENTINEL_REPLY = "commands.execute.conditional.pass_count";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<String> steps = new ArrayDeque<>();
    private static final List<String> log = new ArrayList<>();
    private static final Set<Integer> awaited = new HashSet<>();
    private static boolean leaving;
    private static boolean opened;
    private static boolean running;
    private static boolean synced = true;
    private static boolean sentinelSent;
    private static int waiting;
    private static CompletableFuture<String> pending;
    private static long frames;
    private static long framesFrom = -1;
    private static BooleanSupplier until;
    private static Runnable then;
    private static CompassPayload compassFrom;
    private static String current = "";
    private static long progressed;
    private static long loadingMark = -1;
    private static String failure;
    private static String waitingOn;

    static {
        Problems.install();
    }

    private Driver() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!opened && minecraft.screen instanceof TitleScreen) {
            opened = true;
            open(minecraft);
            return;
        }
        Path folder = minecraft.gameDirectory.toPath().resolve("driver");
        if (take(folder.resolve("quit"))) {
            minecraft.stop();
            return;
        }
        if (!running && !leaving && Background.idle()) {
            leaving = true;
            Background.leave("the client");
            minecraft.stop();
            return;
        }
        if (running && take(folder.resolve("abort"))) {
            stopBatch(folder, "aborted");
            return;
        }
        if (running && minecraft.player == null && !joinNext() && dialog(minecraft, folder)) {
            return;
        }
        if (!running) {
            load(folder.resolve("commands.txt"));
            return;
        }
        if (failure != null) {
            String failed = failure;
            failure = null;
            stopBatch(folder, "error: `%s` failed at once: %s".formatted(current, failed));
            return;
        }
        if (waiting > 0) {
            waiting--;
            progressed = System.nanoTime();
            return;
        }
        if (minecraft.player == null) {
            loading(minecraft);
        }
        if (System.nanoTime() - progressed > STEP_LIMIT_NANOS) {
            String stuck = until != null || pending != null ? current : steps.isEmpty() ? "the end of the batch" : steps.peek();
            stopBatch(folder, "error: `%s` did not finish within %d s; %s".formatted(stuck, STEP_LIMIT_NANOS / PERF_NANOS, why(minecraft)));
            return;
        }
        if (pending != null) {
            if (!pending.isDone()) {
                return;
            }
            settlePending();
            progressed = System.nanoTime();
        }
        if (until != null) {
            if (!until.getAsBoolean()) {
                return;
            }
            until = null;
            waitingOn = null;
            progressed = System.nanoTime();
            Runnable finished = then;
            then = null;
            if (finished != null) {
                finished.run();
            }
        }
        while (!steps.isEmpty()) {
            if (minecraft.player != null && !sync(minecraft)) {
                return;
            }
            String step = steps.peek();
            if (!ready(minecraft, step)) {
                return;
            }
            steps.poll();
            current = step;
            progressed = System.nanoTime();
            note("> " + step);
            run(minecraft, step);
            if (waiting > 0 || pending != null || until != null) {
                return;
            }
        }
        if (minecraft.player != null && !sync(minecraft)) {
            return;
        }
        finish(folder);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        frames++;
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (sentinelSent && message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(SENTINEL_REPLY)) {
            event.setCanceled(true);
            sentinelSent = false;
            synced = true;
            return;
        }
        if (running) {
            log.add("chat: " + message.getString());
            if (failure == null && FAILED.matcher(message.getString()).find()) {
                failure = message.getString();
            }
        }
    }

    private static void loading(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerLevel level = server == null ? null : server.overworld();
        long mark = level == null ? -1 : level.getChunkSource().getLoadedChunksCount() + (long) server.getTickCount() * Integer.MAX_VALUE;
        if (mark != loadingMark) {
            loadingMark = mark;
            progressed = System.nanoTime();
        }
    }

    private static boolean take(Path signal) {
        try {
            return Files.deleteIfExists(signal);
        } catch (IOException exception) {
            LOGGER.error("driver: cannot remove {}", signal, exception);
            return false;
        }
    }

    private static void open(Minecraft minecraft) {
        try {
            Path folder = minecraft.gameDirectory.toPath().resolve("driver");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("game.pid"), Long.toString(ProcessHandle.current().pid()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        begin(enter(minecraft, SHOWROOM ? "showroom" : STATE.isEmpty() ? "play" : "state " + STATE + " " + STATE_CACHE));
    }

    private static List<String> enter(Minecraft minecraft, String world) {
        String[] words = world.split(" ");
        standing(minecraft, words[0], words.length > 1 ? words[1] : null);
        switch (words[0]) {
            case "showroom" -> {
                enter(minecraft, SHOWROOM_WORLD, GameType.CREATIVE, SEED);
                return JsonParser.parseString(resource("/wayfarer/showroom.json")).getAsJsonObject().getAsJsonArray("steps").asList().stream()
                        .map(step -> step.getAsString())
                        .toList();
            }
            case "state" -> {
                boolean built = minecraft.getLevelSource().levelExists(STATE_WORLD);
                enter(minecraft, STATE_WORLD, GameType.SURVIVAL, StoryDev.seed());
                return built ? List.of("settle") : List.of("settle", "story " + words[1] + " " + words[2], "settle");
            }
            case "play" -> {
                enter(minecraft, PLAY_WORLD, GameType.SURVIVAL, WorldOptions.randomSeed());
                return List.of("settle");
            }
            default -> {
                note("error: enter takes showroom, play or state <quest/step> <cache>, not " + world);
                return List.of();
            }
        }
    }

    private static void leave(Minecraft minecraft) {
        if (minecraft.level != null) {
            boolean local = minecraft.isLocalServer();
            minecraft.level.disconnect();
            if (local) {
                minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            } else {
                minecraft.disconnect();
            }
        }
        minecraft.setScreen(new TitleScreen());
        standing(minecraft, "title", null);
        until = () -> minecraft.level == null && minecraft.getSingleplayerServer() == null && minecraft.screen instanceof TitleScreen;
    }

    private static void standing(Minecraft minecraft, String world, @Nullable String state) {
        JsonObject where = new JsonObject();
        where.addProperty("state", state);
        where.addProperty("world", world);
        try {
            Path folder = minecraft.gameDirectory.toPath().resolve("driver");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("world.json"), where.toString());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void enter(Minecraft minecraft, String world, GameType mode, long seed) {
        if (minecraft.getLevelSource().levelExists(world)) {
            minecraft.createWorldOpenFlows().openWorld(world, () -> minecraft.setScreen(new TitleScreen()));
            return;
        }
        LevelSettings settings = new LevelSettings(world, mode, false, Difficulty.HARD, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(
                world, settings, new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
    }

    private static String resource(String name) {
        try (InputStream stream = Driver.class.getResourceAsStream(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void load(Path commands) {
        if (!Files.exists(commands)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(commands);
            Files.delete(commands);
            begin(lines);
        } catch (IOException exception) {
            LOGGER.error("driver: cannot read {}", commands, exception);
        }
    }

    private static String why(Minecraft minecraft) {
        String screen = minecraft.screen == null ? "no screen open" : "the open screen is " + minecraft.screen.getClass().getSimpleName();
        String player = minecraft.player == null ? "no player" : minecraft.player.isAlive() ? "the player is alive" : "the player is dead";
        return screen + ", " + player + (synced ? "" : ", the server has not answered the last command") + (waitingOn == null ? "" : ", " + waitingOn);
    }

    private static void begin(List<String> lines) {
        lines.stream().map(String::strip).filter(line -> !line.isEmpty()).forEach(steps::add);
        log.clear();
        awaited.clear();
        failure = null;
        running = true;
        progressed = System.nanoTime();
        draw(Minecraft.getInstance(), true);
    }

    private static void draw(Minecraft minecraft, boolean busy) {
        Background.use();
        if (Background.ON) {
            minecraft.getWindow().setFramerateLimit(busy ? minecraft.options.framerateLimit().get() : IDLE_FPS);
        }
    }

    private static void finish(Path folder) {
        running = false;
        draw(Minecraft.getInstance(), false);
        try {
            Files.createDirectories(folder);
            Path partial = folder.resolve("done.tmp");
            Files.write(partial, log);
            Files.move(partial, folder.resolve("done.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            LOGGER.error("driver: cannot write the batch log", exception);
        }
    }

    private static void note(String line) {
        LOGGER.info("driver: {}", line);
        log.add(line);
    }

    private static boolean sync(Minecraft minecraft) {
        if (synced) {
            return true;
        }
        if (!sentinelSent) {
            sentinelSent = true;
            minecraft.player.connection.sendCommand(SENTINEL);
        }
        return false;
    }

    private static boolean ready(Minecraft minecraft, String step) {
        String verb = step.split(" ", 2)[0];
        if (verb.equals("settle")) {
            return minecraft.player != null && sync(minecraft) && loaded(minecraft) && freshFrame() && rendered(minecraft);
        }
        if (verb.equals("shot")) {
            return freshFrame();
        }
        if (verb.equals("press")) {
            AbstractWidget widget = labelled(minecraft, step.substring(verb.length()).strip());
            return widget == null || widget.active;
        }
        if (verb.equals("await")) {
            return heard(step.substring(verb.length()).strip()) != null;
        }
        if (verb.equals("hud")) {
            if (minecraft.player == null) {
                return false;
            }
            if (compassFrom == null) {
                compassFrom = HudState.compass;
            }
            return HudState.compass != compassFrom;
        }
        return minecraft.player != null || !(step.startsWith("/") || PLAYER_STEPS.contains(verb));
    }

    private static Integer heard(String text) {
        for (int index = 0; index < log.size(); index++) {
            String line = log.get(index);
            if (!awaited.contains(index) && line.startsWith("chat: ") && line.contains(text)) {
                return index;
            }
        }
        return null;
    }

    private static boolean freshFrame() {
        if (framesFrom < 0) {
            framesFrom = frames;
        }
        return frames > framesFrom + SETTLE_FRAMES;
    }

    private static boolean dialog(Minecraft minecraft, Path folder) {
        if (minecraft.screen instanceof BackupConfirmScreen backup && translationKey(backup.getTitle()).equals(EXPERIMENTAL_WARNING)) {
            for (GuiEventListener child : backup.children()) {
                if (child instanceof Button button && translationKey(button.getMessage()).equals(LOAD_WITHOUT_BACKUP)) {
                    note("note: confirmed the experimental settings warning of this dev world; it does not come again");
                    button.onPress();
                    return true;
                }
            }
        }
        if (minecraft.screen instanceof BackupConfirmScreen || minecraft.screen instanceof ConfirmScreen
                || minecraft.screen instanceof AlertScreen || minecraft.screen instanceof DisconnectedScreen) {
            stopBatch(folder, "blocked: the game is asking on its " + minecraft.screen.getClass().getSimpleName() + ": "
                    + minecraft.screen.getNarrationMessage().getString().replace('\n', ' '));
            return true;
        }
        return false;
    }

    private static Field clickCount() {
        try {
            Field field = KeyMapping.class.getDeclaredField("clickCount");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Method mouse(String name, Class<?>... parameters) {
        try {
            Method method = MouseHandler.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void mouse(Minecraft minecraft, Method method, Object... arguments) {
        Object[] call = new Object[arguments.length + 1];
        call[0] = minecraft.getWindow().getWindow();
        System.arraycopy(arguments, 0, call, 1, arguments.length);
        try {
            method.invoke(minecraft.mouseHandler, call);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static void stopBatch(Path folder, String reason) {
        steps.clear();
        waiting = 0;
        pending = null;
        until = null;
        waitingOn = null;
        then = null;
        sentinelSent = false;
        synced = true;
        note(reason);
        finish(folder);
    }

    private static boolean loaded(Minecraft minecraft) {
        ChunkPos center = minecraft.player.chunkPosition();
        for (int dx = -SETTLE_CHUNKS; dx <= SETTLE_CHUNKS; dx++) {
            for (int dz = -SETTLE_CHUNKS; dz <= SETTLE_CHUNKS; dz++) {
                if (!minecraft.level.getChunkSource().hasChunk(center.x + dx, center.z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean rendered(Minecraft minecraft) {
        try {
            Class<?> sodium = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            Object renderer = sodium.getMethod("instanceNullable").invoke(null);
            Method complete = sodium.getMethod("isTerrainRenderComplete");
            return renderer == null || (boolean) complete.invoke(renderer);
        } catch (ReflectiveOperationException exception) {
            return minecraft.levelRenderer.hasRenderedAllSections();
        }
    }

    private static void run(Minecraft minecraft, String step) {
        String[] words = step.split(" ", 2);
        String argument = words.length > 1 ? words[1] : "";
        switch (words[0]) {
            case "await" -> {
                int index = heard(argument);
                awaited.add(index);
                note("await: " + log.get(index).substring("chat: ".length()));
            }
            case "assets" -> {
                pending = minecraft.reloadResourcePacks().thenApply(ignored -> "assets: reloaded");
                until = () -> minecraft.getOverlay() == null;
                waitingOn = "the loading screen to fade";
            }
            case "beside", "clear", "person", "place", "spawn" -> command(minecraft, "wayfarer workshop " + step);
            case "chat" -> minecraft.options.chatVisibility().set(argument.equals("on") ? ChatVisiblity.FULL : ChatVisiblity.HIDDEN);
            case "click" -> {
                String[] at = argument.split(" ");
                click(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]), at.length > 2 ? at[2] : "left");
            }
            case "close" -> {
                if (minecraft.screen != null) {
                    minecraft.screen.onClose();
                }
            }
            case "far" -> far();
            case "find" -> find(minecraft, argument);
            case "fly" -> {
                minecraft.player.getAbilities().flying = argument.equals("on");
                minecraft.player.onUpdateAbilities();
            }
            case "gui" -> minecraft.options.hideGui = argument.equals("off");
            case "inspect" -> inspect(minecraft, argument);
            case "hud" -> {
                note("hud: " + compass(HudState.compass));
                compassFrom = null;
            }
            case "interact" -> interact(minecraft, argument);
            case "enter" -> {
                List<String> next = enter(minecraft, argument);
                for (int index = next.size() - 1; index >= 0; index--) {
                    steps.addFirst(next.get(index));
                }
            }
            case "join" -> join(minecraft, argument);
            case "key" -> key(minecraft, argument);
            case "leave" -> leave(minecraft);
            case "listeners" -> Arrays.stream(argument.split(" ")).map(Swapped::listen).forEach(Driver::note);
            case "log" -> {
                List<String> problems = Problems.drain();
                if (problems.isEmpty()) {
                    note("log: nothing new");
                }
                problems.forEach(problem -> note("log: " + problem));
            }
            case "perf" -> perf(minecraft);
            case "pos" -> note("pos: %.1f %.1f %.1f facing %.0f %.0f in %s".formatted(minecraft.player.getX(), minecraft.player.getY(),
                    minecraft.player.getZ(), minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.level.dimension().location()));
            case "point" -> {
                if (minecraft.screen == null) {
                    note("error: no screen to point at");
                } else {
                    String[] at = argument.split(" ");
                    point(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]));
                }
            }
            case "press" -> press(minecraft, argument);
            case "quit" -> minecraft.stop();
            case "scale" -> scale(minecraft, Integer.parseInt(argument));
            case "screen" -> describe(minecraft);
            case "settle" -> framesFrom = -1;
            case "sneak" -> {
                boolean on = argument.equals("on");
                minecraft.options.keyShift.setDown(on);
                until = () -> minecraft.player.isShiftKeyDown() == on;
            }
            case "shot" -> {
                framesFrom = -1;
                shot(minecraft, argument);
            }
            case "state" -> command(minecraft, "wayfarer state");
            case "story" -> story(minecraft, argument);
            case "use" -> use(minecraft, argument);
            case "type" -> argument.chars().forEach(character -> {
                if (minecraft.screen != null) {
                    minecraft.screen.charTyped((char) character, 0);
                }
            });
            case "sky" -> sky(minecraft, Integer.parseInt(argument));
            case "wait" -> waiting = Integer.parseInt(argument);
            case "workshop" -> command(minecraft, "wayfarer workshop enter");
            default -> {
                if (step.startsWith("/")) {
                    command(minecraft, step.substring(1));
                } else {
                    note("error: unknown step " + step);
                }
            }
        }
    }

    private static void settlePending() {
        try {
            note(pending.join());
        } catch (CompletionException | CancellationException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            note("error: " + cause.getMessage());
        }
        pending = null;
    }

    private static void inspect(Minecraft minecraft, String argument) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            note("error: inspect needs the built-in server of a world opened by the client");
            return;
        }
        String[] words = argument.split(" ");
        UUID id = minecraft.player.getUUID();
        ResourceKey<Level> dimension = minecraft.level.dimension();
        pending = server.submit(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return "inspect: " + switch (words[0]) {
                case "block" -> Inspect.block(server.getLevel(dimension),
                        new BlockPos(Integer.parseInt(words[1]), Integer.parseInt(words[2]), Integer.parseInt(words[3])));
                case "creature" -> Inspect.creature(player, ResourceLocation.parse(words[1]));
                case "inventory" -> Inspect.inventory(player);
                case "item" -> Inspect.item(player);
                default -> throw new IllegalArgumentException("inspect takes block <x> <y> <z>, creature <type>, inventory or item");
            };
        });
    }

    private static void command(Minecraft minecraft, String command) {
        synced = false;
        minecraft.player.connection.sendCommand(command);
    }

    private static void story(Minecraft minecraft, String argument) {
        String[] words = argument.split(" ");
        command(minecraft, words.length > 1 ? "wayfarer story build \"%s\" %s".formatted(words[1], words[0]) : "wayfarer story to " + words[0]);
        int[] seen = {log.size()};
        waitingOn = "the story to stand at " + words[0];
        until = () -> {
            for (; seen[0] < log.size(); seen[0]++) {
                String line = log.get(seen[0]);
                if (line.startsWith(STORY_LINE)) {
                    progressed = System.nanoTime();
                    if (line.startsWith(STORY_AT)) {
                        if (words.length > 1) {
                            Hud.clearBanners();
                        }
                        return true;
                    }
                }
            }
            return false;
        };
    }

    private static void perf(Minecraft minecraft) {
        long fromFrames = frames;
        long fromNanos = System.nanoTime();
        until = () -> System.nanoTime() - fromNanos >= PERF_NANOS;
        then = () -> {
            double fps = (frames - fromFrames) * (double) PERF_NANOS / (System.nanoTime() - fromNanos);
            IntegratedServer server = minecraft.getSingleplayerServer();
            String tick = server == null ? "no server" : "%.1f ms a server tick".formatted(server.getAverageTickTimeNanos() / NANOS_PER_MILLI);
            note("perf: %.0f fps over one second, %s, drawn by %s".formatted(fps, tick, GlUtil.getRenderer()));
        };
    }

    private static void far() {
        if (!ModList.get().isLoaded(DISTANT_HORIZONS)) {
            note("error: far needs Distant Horizons; launch the client with start --far");
            return;
        }
        long from = System.nanoTime();
        FarView.Work[] last = {FarView.read()};
        if (!last[0].generating()) {
            note("error: Distant Horizons draws no far view in this client; launch it with start --far");
            return;
        }
        long[] checked = {from};
        until = () -> {
            if (System.nanoTime() - checked[0] < PERF_NANOS) {
                return false;
            }
            checked[0] = System.nanoTime();
            FarView.Work work = FarView.read();
            boolean still = work.generated() == last[0].generated();
            if (!still || work.drawn() != last[0].drawn()) {
                progressed = System.nanoTime();
            }
            last[0] = work;
            waitingOn = "Distant Horizons " + work;
            return work.left() == 0 && still;
        };
        then = () -> note("far: Distant Horizons has generated and drawn the far view, after %.0f s".formatted(
                (System.nanoTime() - from) / (double) PERF_NANOS));
    }

    private static void sky(Minecraft minecraft, int ticks) {
        List<Long> times = new ArrayList<>();
        until = () -> {
            times.add(minecraft.level.getDayTime());
            progressed = System.nanoTime();
            return times.size() > ticks;
        };
        then = () -> {
            long least = Long.MAX_VALUE;
            long most = Long.MIN_VALUE;
            for (int index = 1; index < times.size(); index++) {
                long step = times.get(index) - times.get(index - 1);
                least = Math.min(least, step);
                most = Math.max(most, step);
            }
            note("sky: over %d ticks the client's clock moved %d, %d to %d a tick, at %.3f a tick as the server set it%s".formatted(ticks,
                    times.getLast() - times.getFirst(), least, most, minecraft.level.getDayTimePerTick(),
                    least < 0 ? "; it stepped backwards" : ""));
        };
    }

    private static String compass(CompassPayload reading) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", reading.mode().name().toLowerCase(Locale.ROOT));
        JsonArray markers = new JsonArray();
        for (CompassPayload.Marker marker : reading.markers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", marker.kind().name().toLowerCase(Locale.ROOT));
            entry.addProperty("name", marker.name());
            entry.addProperty("x", marker.x());
            entry.addProperty("z", marker.z());
            markers.add(entry);
        }
        json.add("markers", markers);
        return json.toString();
    }

    private static void shot(Minecraft minecraft, String name) {
        Path path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png").toAbsolutePath().normalize();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            note("screenshot: " + path);
        } catch (IOException exception) {
            note("error: cannot save " + path + ": " + exception.getMessage());
        }
    }

    private static void find(Minecraft minecraft, String argument) {
        String[] words = argument.split(" ");
        ResourceLocation id = ResourceLocation.parse(words[0]);
        int radius = words.length > 1 ? Integer.parseInt(words[1]) : FIND_RADIUS;
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            note("error: no block " + id);
            return;
        }
        BlockPos center = minecraft.player.blockPosition();
        BlockPos low = new BlockPos(center.getX() - radius, minecraft.level.getMinBuildHeight(), center.getZ() - radius);
        BlockPos high = new BlockPos(center.getX() + radius, minecraft.level.getMaxBuildHeight() - 1, center.getZ() + radius);
        List<BlockPos> found = BlockPos.betweenClosedStream(low, high)
                .filter(pos -> minecraft.level.getBlockState(pos).is(block))
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .limit(FIND_LIMIT)
                .toList();
        note("found %d %s within %d: %s".formatted(found.size(), id, radius,
                found.stream().map(pos -> pos.getX() + " " + pos.getY() + " " + pos.getZ()).collect(Collectors.joining(", "))));
    }

    private static boolean joinNext() {
        return until == null && pending == null && steps.peek() != null && steps.peek().startsWith("join ");
    }

    private static void scale(Minecraft minecraft, int scale) {
        int height = Math.max(WINDOW_HEIGHT, scale * GUI_HEIGHT);
        int width = Math.max(WINDOW_WIDTH, Math.max(scale * GUI_WIDTH, height * WINDOW_WIDTH / WINDOW_HEIGHT));
        minecraft.getWindow().setWindowed(width, height);
        until = () -> {
            if (minecraft.getWindow().getWidth() != width || minecraft.getWindow().getHeight() != height) {
                return false;
            }
            minecraft.options.guiScale().set(scale);
            minecraft.resizeDisplay();
            return minecraft.getWindow().getGuiScale() == scale;
        };
    }

    private static void join(Minecraft minecraft, String address) {
        leave(minecraft);
        standing(minecraft, "server", address);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address), new ServerData(address, address, ServerData.Type.OTHER),
                false, null);
        until = () -> minecraft.player != null && minecraft.level != null && !minecraft.isLocalServer()
                && (minecraft.screen == null || minecraft.screen instanceof DeathScreen) && loaded(minecraft);
    }

    private static void key(Minecraft minecraft, String name) {
        if (name.equals("escape")) {
            if (minecraft.screen != null) {
                minecraft.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            return;
        }
        Arrays.stream(minecraft.options.keyMappings)
                .filter(mapping -> mapping.getName().equals(name))
                .findFirst()
                .ifPresentOrElse(mapping -> {
                    KeyMapping.click(mapping.getKey());
                    until = () -> clicks(mapping) == 0;
                }, () -> note("error: no key mapping " + name));
    }

    private static int clicks(KeyMapping mapping) {
        try {
            return CLICK_COUNT.getInt(mapping);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void click(Minecraft minecraft, double x, double y, String name) {
        Integer button = BUTTONS.get(name);
        if (button == null) {
            note("error: click takes left, middle or right, not " + name);
            return;
        }
        if (minecraft.screen == null) {
            note("error: no screen to click");
            return;
        }
        point(minecraft, x, y);
        long moved = frames;
        until = () -> {
            if (frames <= moved + SETTLE_FRAMES) {
                return false;
            }
            mouse(minecraft, MOUSE_PRESS, button, GLFW.GLFW_PRESS, 0);
            mouse(minecraft, MOUSE_PRESS, button, GLFW.GLFW_RELEASE, 0);
            return true;
        };
    }

    private static void point(Minecraft minecraft, double x, double y) {
        Window window = minecraft.getWindow();
        mouse(minecraft, MOUSE_MOVE, x * window.getScreenWidth() / window.getGuiScaledWidth(), y * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static void interact(Minecraft minecraft, String type) {
        ResourceLocation id = ResourceLocation.parse(type);
        Entity nearest = minecraft.level.getEntities(minecraft.player, minecraft.player.getBoundingBox().inflate(INTERACT_REACH),
                        entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id)).stream()
                .min(Comparator.comparingDouble(minecraft.player::distanceToSqr))
                .orElse(null);
        if (nearest == null) {
            note("error: no " + id + " within " + (int) INTERACT_REACH + " blocks");
            return;
        }
        minecraft.gameMode.interact(minecraft.player, nearest, InteractionHand.MAIN_HAND);
        synced = false;
    }

    private static void use(Minecraft minecraft, String argument) {
        String[] xyz = argument.split(" ");
        BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 towards = minecraft.player.getEyePosition().subtract(centre).normalize();
        Direction face = Arrays.stream(Direction.values()).filter(side -> minecraft.level.getBlockState(pos.relative(side)).canBeReplaced())
                .max(Comparator.comparingDouble(side -> side.getStepX() * towards.x + side.getStepY() * towards.y + side.getStepZ() * towards.z))
                .orElse(Direction.getNearest(towards.x, towards.y, towards.z));
        Vec3 at = centre.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
        synced = false;
    }

    private static void press(Minecraft minecraft, String label) {
        AbstractWidget widget = labelled(minecraft, label);
        if (widget == null) {
            note("error: no widget labelled " + label);
            return;
        }
        click(minecraft, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, "left");
    }

    private static AbstractWidget labelled(Minecraft minecraft, String label) {
        return widgets(minecraft).stream().filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElse(null);
    }

    private static void describe(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (screen == null) {
            note("screen: none");
            return;
        }
        note("screen: %s \"%s\" %dx%d".formatted(screen.getClass().getName(), screen.getTitle().getString(), screen.width, screen.height));
        for (AbstractWidget widget : widgets(minecraft)) {
            note("widget: %s \"%s\" at %d,%d size %dx%d".formatted(widget.getClass().getSimpleName(), widget.getMessage().getString(),
                    widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        }
    }

    private static List<AbstractWidget> widgets(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return List.of();
        }
        return minecraft.screen.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .toList();
    }
}
