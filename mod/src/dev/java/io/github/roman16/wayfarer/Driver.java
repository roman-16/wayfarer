package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.client.Conversation;
import io.github.roman16.wayfarer.client.Frames;
import io.github.roman16.wayfarer.client.Graphics;
import io.github.roman16.wayfarer.client.Hud;
import io.github.roman16.wayfarer.client.JournalScreen;
import io.github.roman16.wayfarer.client.Recap;
import io.github.roman16.wayfarer.mixin.dev.GuiAccessor;
import io.github.roman16.wayfarer.story.StoryDev;
import io.github.roman16.wayfarer.story.Tasks;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import nomadictents.block.TentDoorBlock;
import nomadictents.item.TentItem;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
public final class Driver {
    private static final int SCROLL_TURNS = 32;
    private static final boolean SHOWROOM = Boolean.getBoolean(Wayfarer.MODID + ".showroom");
    private static final String WORLD_SUFFIX = Boolean.getBoolean(Wayfarer.MODID + ".lean") ? "-lean" : "";
    private static final String PLAY_WORLD = "play" + WORLD_SUFFIX;
    private static final String SHOWROOM_WORLD = "showroom" + WORLD_SUFFIX;
    private static final String PLAY_SEED = System.getProperty(Wayfarer.MODID + ".seed", "");
    private static final String STATE = System.getProperty(Wayfarer.MODID + ".state", "");
    private static final String STATE_CACHE = System.getProperty(Wayfarer.MODID + ".state.cache", "");
    private static final String STATE_WORLD = "state" + WORLD_SUFFIX;
    private static final String SAVE = System.getProperty(Wayfarer.MODID + ".world", "");
    private static final Set<String> LOOKING = Set.of("log", "pos", "screen", "shot");
    private static final String STOPPED = "stopped:";
    private static final long CHECKPOINT_TICKS = 2048;
    private static final long TALK_QUIET_TICKS = 200;
    private static final int RECENT_HURT_TICKS = 100;
    private static final double SAFE_BLOCKS = 16;
    private static final String STORY_LINE = "chat: story: ";
    private static final String STORY_AT = STORY_LINE + "at ";
    private static final long SEED = 20260929L;
    private static final String DISTANT_HORIZONS = "distanthorizons";
    private static final Pattern FAILED = Pattern.compile("^[^:<]+ failed: ");
    private static final String STORY_CHECK = "story check failed: ";
    private static final Pattern AT_BLOCK = Pattern.compile(" at -?\\d+, -?\\d+, -?\\d+");
    private static final int FIND_LIMIT = 20;
    private static final int FIND_RADIUS = 32;
    private static final int SETTLE_CHUNKS = 3;
    private static final int SETTLE_FRAMES = 2;
    private static final int FLIGHT_HOLD_TICKS = 20;
    private static final int PANORAMA_FOV = 90;
    private static final int PANORAMA_FRAMES = 32;
    private static final int PANORAMA_SIZE = 2048;
    private static final int CLOSE_LAYERS = 8;
    private static final float[][] PANORAMA_FACES = {{0, 0}, {90, 0}, {180, 0}, {-90, 0}, {0, -90}, {0, 90}};
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final long PERF_NANOS = 1_000_000_000L;
    private static final long STEP_LIMIT_NANOS = 60_000_000_000L;
    private static final Field CLICK_COUNT = clickCount();
    private static final Field CAUSE_OF_DEATH = causeOfDeath();
    private static final Map<String, Integer> BUTTONS = Map.of("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, "middle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "right", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final String RELATIVE = "~";
    private static final long LABEL_NANOS = 2_000_000_000L;
    private static final Method MOUSE_MOVE = mouse("onMove", long.class, double.class, double.class);
    private static final Method MOUSE_PRESS = mouse("onPress", long.class, int.class, int.class, int.class);
    private static final Method MOUSE_SCROLL = mouse("onScroll", long.class, double.class, double.class);
    private static final int PITCH_TICKS = 40;
    private static final int LAUNCH_BLOCKS = 4;
    private static final double LAUNCH_CLEAR = 2;
    private static final int DIG_TICKS = 400;
    private static final int WAY_CLEARS = 4;
    private static final float SOFT_HARDNESS = 0.75f;
    private static final int LAUNCH_TICKS = 40;
    private static final int STEP_IN_TICKS = 200;
    private static final int TAKE_BOAT_TICKS = 100;
    private static final int SWING_TICKS = 4;
    private static final int PICKUP_TICKS = 20;
    private static final double WATER_TOP = 0.9;
    private static final String EXPERIMENTAL_WARNING = "selectWorld.backupQuestion.experimental";
    private static final String LOAD_WITHOUT_BACKUP = "selectWorld.backupJoinSkipButton";
    private static final Set<String> PLAYER_STEPS = Set.of("beside", "cheat", "clear", "craft", "dig", "dismount", "distance", "eat", "embark", "fight", "fish", "fly", "follow",
            "gather", "go", "hold", "hotbar", "hud", "idle", "inspect", "interact", "key", "listen", "panorama", "person", "pitch", "place", "put", "row", "sky", "spawn", "state", "story",
            "turn", "wear", "workshop");
    private static final int SLOT_MIDDLE = 8;
    private static final String BARITONE = "baritoe";
    private static final double INTERACT_REACH = 8;
    private static final double REACH_MARGIN = 0.25;
    private static final int REWALK_TICKS = 10;
    private static final int SLOT_SIZE = 16;
    private static final double HALF = 0.5;
    private static final String SHIFT = "shift";
    private static final int IDLE_FPS = 5;
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<String> steps = new ArrayDeque<>();
    private static final List<String> log = new ArrayList<>();
    private static final String CHAT = "chat: ";
    private static final String TALK = "talk: ";
    private static long talked;
    private static boolean chatHidden;
    private static boolean bare;
    private static final Set<Integer> awaited = new HashSet<>();
    private static final Map<String, Integer> checks = new LinkedHashMap<>();
    private static boolean leaving;
    private static boolean opened;
    private static boolean running;
    private static boolean turns;
    private static boolean stopping;
    private static boolean mourned;
    private static boolean checked;
    private static long lastCheckpoint = -1;
    private static long talkedAt = -TALK_QUIET_TICKS;
    @Nullable
    private static JsonObject lastSeen;
    @Nullable
    private static String openSave;
    @Nullable
    private static String left = "";
    private static boolean synced = true;
    private static int asked;
    private static boolean asking;
    private static int waiting;
    private static CompletableFuture<String> pending;
    private static long frames;
    private static long framesFrom = -1;
    private static BooleanSupplier until;
    private static boolean shifting;
    private static Runnable then;
    private static CompassPayload compassFrom;
    private static String current = "";
    private static boolean stepped;
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
        for (Conversation.Logged line : Conversation.since(talked)) {
            note(TALK + line.text());
            talked = line.seq();
            talkedAt = minecraft.level == null ? talkedAt : minecraft.level.getGameTime();
            progressed = System.nanoTime();
        }
        save(minecraft);
        if (!opened && minecraft.screen instanceof TitleScreen) {
            opened = true;
            open(minecraft);
            return;
        }
        Path folder = minecraft.gameDirectory.toPath().resolve("driver");
        if (take(folder.resolve("quit"))) {
            turns = false;
            minecraft.stop();
            return;
        }
        if (!running && !leaving && !turns && Background.idle()) {
            leaving = true;
            turns = false;
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
            halt("error: %s failed at once: %s".formatted(runningStep(), failed));
            return;
        }
        if (minecraft.player != null && minecraft.level != null && body(minecraft)) {
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
            String reason = "error: `%s` did not finish within %d s; %s".formatted(stuck, STEP_LIMIT_NANOS / PERF_NANOS, why(minecraft));
            if (stuck.equals(current) && !LOOKING.contains(current.split(" ", 2)[0])) {
                halt(reason);
                progressed = System.nanoTime();
            } else {
                stopBatch(folder, reason);
            }
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
                if (waiting > 0 || pending != null || until != null) {
                    return;
                }
            }
        }
        while (!steps.isEmpty()) {
            if (minecraft.player != null && !sync(minecraft)) {
                return;
            }
            String step = steps.peek();
            if (stopping && !LOOKING.contains(step.split(" ", 2)[0])) {
                steps.poll();
                if (stepped) {
                    observe(minecraft);
                }
                stepped = false;
                note("> " + step);
                note("skipped: the step before stopped");
                continue;
            }
            if (!ready(minecraft, step)) {
                return;
            }
            steps.poll();
            if (stepped) {
                observe(minecraft);
            }
            stepped = true;
            current = step;
            progressed = System.nanoTime();
            note("> " + step);
            try {
                run(minecraft, step);
            } catch (RuntimeException exception) {
                halt("error: `%s` failed at once: %s".formatted(step, exception.getMessage() == null ? exception : exception.getMessage()));
                return;
            }
            if (waiting > 0 || pending != null || until != null) {
                return;
            }
        }
        if (minecraft.player != null && !sync(minecraft)) {
            return;
        }
        if (!checked) {
            checked = true;
            if (turns && due(minecraft) && safe(minecraft)) {
                checkpoint(minecraft);
                return;
            }
        }
        finish(folder);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        frames++;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (bare) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (chatHidden && event.getName().equals(VanillaGuiLayers.CHAT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (!running || event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) {
            return;
        }
        String text = message.getString();
        if (text.startsWith(STORY_CHECK)) {
            String kind = AT_BLOCK.matcher(text.substring(STORY_CHECK.length())).replaceAll("");
            if (checks.merge(kind, 1, Integer::sum) == 1) {
                note("check: " + text.substring(STORY_CHECK.length()));
            }
            return;
        }
        log.add(CHAT + text);
        if (failure == null && FAILED.matcher(text).find()) {
            failure = text;
        }
    }

    private static String digest(Minecraft minecraft) {
        StringBuilder seen = new StringBuilder();
        seen.append(HudState.compass).append(HudState.gaze).append(HudState.night).append(HudState.panel).append(List.copyOf(HudState.banners))
                .append(Hud.showing()).append(JournalScreen.latest());
        LocalPlayer player = minecraft.player;
        if (player != null) {
            seen.append(player.position()).append(player.getYRot()).append(player.getXRot());
            for (ItemStack stack : player.getInventory().items) {
                seen.append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(stack.getCount()).append(stack.getComponentsPatch());
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seen.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static boolean batching() {
        return running;
    }

    public static boolean betweenTurns() {
        return turns && !running;
    }

    public static boolean panoramic() {
        return bare;
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
        List<String> first = enter(minecraft, SHOWROOM ? "showroom" : !SAVE.isEmpty() ? "save " + SAVE : !STATE.isEmpty() ? "state " + STATE + " " + STATE_CACHE
                : ("play " + PLAY_SEED).strip());
        List<String> noted = List.copyOf(log);
        begin(first);
        log.addAll(noted);
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
                boolean built = Files.exists(Path.of(words[2]).resolve("level.dat"));
                enter(minecraft, STATE_WORLD, GameType.SURVIVAL, StoryDev.seed());
                return built ? List.of("settle") : List.of("settle", "story " + words[1] + " " + words[2], "settle");
            }
            case "play" -> {
                enter(minecraft, PLAY_WORLD, GameType.SURVIVAL, words.length > 1 ? Long.parseLong(words[1]) : WorldOptions.randomSeed());
                return List.of("settle");
            }
            case "save" -> {
                String name = world.substring(words[0].length()).strip();
                if (!minecraft.getLevelSource().levelExists(name)) {
                    note("error: no save " + name + " in " + minecraft.getLevelSource().getBaseDir());
                    return List.of();
                }
                standing(minecraft, "save", name);
                turns = turned(minecraft).contains(name);
                minecraft.createWorldOpenFlows().openWorld(name, () -> minecraft.setScreen(new TitleScreen()));
                return List.of("settle");
            }
            default -> {
                note("error: enter takes showroom, play [seed], save <name> or state <quest/step> <cache>, not " + world);
                return List.of();
            }
        }
    }

    private static Path turnsFile(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("driver").resolve("turns.txt");
    }

    private static Set<String> turned(Minecraft minecraft) {
        try {
            Path file = turnsFile(minecraft);
            return Files.exists(file) ? new HashSet<>(Files.readAllLines(file)) : new HashSet<>();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void turn(Minecraft minecraft, boolean on) {
        turns = on;
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        String name = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        Set<String> names = turned(minecraft);
        if (on ? names.add(name) : names.remove(name)) {
            try {
                Files.write(turnsFile(minecraft), names.stream().sorted().toList());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static void leave(Minecraft minecraft) {
        if (minecraft.level != null) {
            left = Recap.world();
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
        LevelSettings settings = new LevelSettings(world, mode, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
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
        checks.clear();
        current = "";
        failure = null;
        running = true;
        stepped = false;
        stopping = false;
        checked = false;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getAllLevels().forEach(level -> level.noSave = false));
        }
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
        Minecraft minecraft = Minecraft.getInstance();
        checks.forEach((kind, count) -> {
            if (count > 1) {
                note("check: " + kind + ", " + count + " times in this batch");
            }
        });
        if (minecraft.player != null) {
            mourn(minecraft);
            if (stepped) {
                observe(minecraft);
            }
            JsonObject now = seen(minecraft);
            now.add("around", Senses.around(minecraft));
            note("now: " + now);
            note("clock: " + minecraft.level.getGameTime());
        }
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

    static void note(String line) {
        LOGGER.info("driver: {}", line);
        log.add(line);
    }

    private static boolean sync(Minecraft minecraft) {
        if (synced) {
            return true;
        }
        if (!asking) {
            asking = true;
            asked++;
            PacketDistributor.sendToServer(new Sync.Ask(asked));
        }
        return false;
    }

    static void answered(int id) {
        if (asking && id == asked) {
            asking = false;
            synced = true;
        }
    }

    private static boolean ready(Minecraft minecraft, String step) {
        String verb = step.split(" ", 2)[0];
        if (minecraft.screen instanceof ReceivingLevelScreen) {
            return false;
        }
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
        if (verb.equals("opens")) {
            return minecraft.screen != null && minecraft.screen.getTitle().getString().contains(step.substring(verb.length()).strip());
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
        boolean typed = verb.equals("key") && !step.substring(verb.length()).strip().startsWith("key.");
        return minecraft.player != null || typed || !(step.startsWith("/") || PLAYER_STEPS.contains(verb));
    }

    private static Integer heard(String text) {
        for (int index = 0; index < log.size(); index++) {
            String line = log.get(index);
            if (!awaited.contains(index) && (line.startsWith(CHAT) || line.startsWith(TALK)) && line.contains(text)) {
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

    private static Field causeOfDeath() {
        try {
            Field field = DeathScreen.class.getDeclaredField("causeOfDeath");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
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

    private static void legs(Minecraft minecraft, String verb, String argument) {
        if (!ModList.get().isLoaded(BARITONE)) {
            throw new IllegalStateException(verb + " needs Baritone, which the client loads in every mode but lean");
        }
        Legs.begin(minecraft, verb, argument);
        until = () -> {
            Legs.Tick tick = Legs.tick(minecraft);
            Legs.drainTold().forEach(line -> note(verb + ": " + line));
            if (tick.progressed()) {
                progressed = System.nanoTime();
            }
            if (tick.failed() != null) {
                abandon();
                Legs.chatter().forEach(line -> note("baritone: " + line));
                note("error: `%s` failed at once: %s".formatted((verb + " " + argument).strip(), tick.failed()));
                return true;
            }
            if (tick.done() != null) {
                note(verb + ": " + tick.done());
                stopping |= tick.done().startsWith(STOPPED);
                return true;
            }
            return false;
        };
    }

    private static boolean body(Minecraft minecraft) {
        Senses.tick(minecraft);
        if (minecraft.screen instanceof DeathScreen) {
            mourn(minecraft);
        }
        if (!ModList.get().isLoaded(BARITONE)) {
            return false;
        }
        Body.tick(minecraft, Legs.kind());
        Body.drain().forEach(line -> note("body: " + line));
        String back = Body.takeHandBack();
        if (back != null && Legs.kind() != null) {
            Legs.handBack(back);
        } else if (back != null) {
            note("body: " + STOPPED + " " + back);
        }
        if (Body.takeTookOver()) {
            stopping = true;
            waiting = 0;
        }
        if (Body.takeFinished()) {
            Legs.resume(minecraft);
        }
        if (Body.busy()) {
            progressed = System.nanoTime();
        }
        return Body.acting();
    }

    private static void mourn(Minecraft minecraft) {
        if (minecraft.player.isAlive()) {
            mourned = false;
            return;
        }
        if (mourned) {
            return;
        }
        mourned = true;
        note("death: " + (minecraft.screen instanceof DeathScreen dead ? causeOfDeath(dead) : "you died"));
        stopping = true;
        waiting = 0;
    }

    private static String causeOfDeath(DeathScreen dead) {
        try {
            Component cause = (Component) CAUSE_OF_DEATH.get(dead);
            return cause == null ? "you died" : cause.getString();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void observe(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        Senses.heardSince().forEach(line -> note("heard: " + line));
        Senses.hurtSince().forEach(line -> note("hurt: " + line));
        JsonObject now = seen(minecraft);
        JsonObject changed = changes(lastSeen, now);
        lastSeen = now;
        if (changed.size() > 0) {
            note("seen: " + changed);
        }
    }

    private static JsonObject changes(@Nullable JsonObject before, JsonObject now) {
        if (before == null) {
            return now;
        }
        JsonObject changed = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : now.entrySet()) {
            JsonElement was = before.get(entry.getKey());
            if (entry.getValue().equals(was)) {
                continue;
            }
            changed.add(entry.getKey(), entry.getValue().isJsonObject() && was != null && was.isJsonObject()
                    ? changes(was.getAsJsonObject(), entry.getValue().getAsJsonObject()) : entry.getValue());
        }
        before.keySet().stream().filter(key -> !now.has(key)).forEach(key -> changed.add(key, JsonNull.INSTANCE));
        return changed;
    }

    private static void save(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        String open = server == null || minecraft.player == null ? null : server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
        if (Objects.equals(open, openSave)) {
            return;
        }
        openSave = open;
        mourned = false;
        lastCheckpoint = open == null ? -1 : minecraft.level.getGameTime();
        talkedAt = -TALK_QUIET_TICKS;
        lastSeen = null;
        Senses.forget();
        if (ModList.get().isLoaded(BARITONE)) {
            Body.forget();
        }
        try {
            Path file = minecraft.gameDirectory.toPath().resolve("driver").resolve("save.json");
            if (open == null) {
                Files.deleteIfExists(file);
            } else {
                JsonObject save = new JsonObject();
                save.addProperty("folder", open);
                Files.createDirectories(file.getParent());
                Files.writeString(file, save.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean due(Minecraft minecraft) {
        return minecraft.level != null && minecraft.level.getGameTime() - lastCheckpoint >= CHECKPOINT_TICKS;
    }

    private static boolean safe(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getSingleplayerServer() == null || !player.isAlive() || minecraft.screen != null
                || !minecraft.gameMode.getPlayerMode().isSurvival() || !minecraft.level.tickRateManager().runsNormally()) {
            return false;
        }
        boolean quiet = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .noneMatch(entity -> Senses.hostile(entity) && entity.isAlive() && entity.distanceTo(player) <= SAFE_BLOCKS);
        return quiet && player.getHealth() >= player.getMaxHealth() / 2 && player.onGround() && !player.isInWater() && !player.isOnFire()
                && minecraft.level.getGameTime() - talkedAt >= TALK_QUIET_TICKS && !Senses.hurtLately(minecraft, RECENT_HURT_TICKS);
    }

    private static void checkpoint(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            note("error: checkpoint needs a world the client opened itself; the dev server keeps its own with server.py's checkpoint step");
            return;
        }
        String where = Senses.time(minecraft.level.getDayTime()) + ", at " + minecraft.player.blockPosition().toShortString();
        lastCheckpoint = minecraft.level.getGameTime();
        pending = server.submit(() -> {
            server.saveEverything(true, true, true);
            server.getAllLevels().forEach(level -> level.noSave = true);
            return "checkpoint: " + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize() + " | " + where;
        });
    }

    static void logged(String line) {
        if (running) {
            note(line);
        } else {
            LOGGER.info("driver: {}", line);
        }
    }

    private static void halt(String reason) {
        if (ModList.get().isLoaded(BARITONE)) {
            Legs.cancel(Minecraft.getInstance());
        }
        abandon();
        waiting = 0;
        pending = null;
        until = null;
        waitingOn = null;
        then = null;
        asking = false;
        synced = true;
        note(reason);
    }

    private static String runningStep() {
        return current.isEmpty() ? "the batch" : "`" + current + "`";
    }

    private static void stopBatch(Path folder, String reason) {
        if (ModList.get().isLoaded(BARITONE)) {
            Legs.cancel(Minecraft.getInstance());
        }
        steps.clear();
        waiting = 0;
        pending = null;
        until = null;
        waitingOn = null;
        then = null;
        asking = false;
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
                note("await: " + log.get(index).substring(log.get(index).indexOf(": ") + 2));
            }
            case "assets" -> {
                pending = minecraft.reloadResourcePacks().thenApply(ignored -> "assets: reloaded");
                until = () -> minecraft.getOverlay() == null;
                waitingOn = "the loading screen to fade";
            }
            case "beside", "clear", "person", "place", "spawn" -> command(minecraft, "wayfarer workshop " + step);
            case "board" -> {
                if (StyleBoard.PAGES.contains(argument)) {
                    minecraft.setScreen(new StyleBoard(argument));
                } else {
                    note("error: board takes one of " + String.join(", ", StyleBoard.PAGES) + ", not " + argument);
                }
            }
            case "chat" -> chatHidden = argument.equals("off");
            case "checkpoint" -> checkpoint(minecraft);
            case "dig" -> dig(minecraft, argument);
            case "digest" -> note("digest: " + digest(minecraft));
            case "dh" -> {
                String[] setting = argument.split(" ", 2);
                String result = setting.length == 2 ? FarView.set(setting[0], setting[1]) : "error: dh takes a setting and a value";
                note(result.startsWith("error: ") ? result : "dh: " + result);
            }
            case "distance" -> distance(minecraft, Integer.parseInt(argument));
            case "click" -> {
                String[] at = argument.split(" ");
                if (at.length > 2 && at[2].equals(SHIFT)) {
                    shiftClick(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]));
                } else {
                    click(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]), at.length > 2 ? at[2] : "left", false);
                }
            }
            case "close" -> {
                for (int layer = 0; layer < CLOSE_LAYERS && minecraft.screen != null && minecraft.level != null; layer++) {
                    minecraft.screen.onClose();
                }
            }
            case "face" -> face(minecraft, argument);
            case "far" -> far();
            case "find" -> find(minecraft, argument);
            case "forget" -> JournalScreen.firstPage();
            case "graphics" -> graphics(argument);
            case "eat", "fight", "follow", "gather", "go", "idle", "listen", "row" -> legs(minecraft, step.split(" ", 2)[0], argument);
            case "embark" -> embark(minecraft);
            case "cheat" -> cheat(argument);
            case "craft" -> Hands.craft(minecraft, argument);
            case "dismount" -> dismount(minecraft);
            case "hotbar" -> Hands.hotbar(minecraft, argument);
            case "fish" -> Fishing.fish(minecraft, argument);
            case "pitch" -> pitch(minecraft);
            case "put" -> Hands.put(minecraft, argument);
            case "wear" -> Hands.wear(minecraft, argument);
            case "fly" -> fly(minecraft.player, argument.equals("on"));
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
            case "hold" -> hold(minecraft, argument);
            case "key" -> key(minecraft, argument);
            case "leave" -> leave(minecraft);
            case "loading" -> {
                Recap.begin(argument.equals("new") ? "" : left);
                minecraft.setScreen(new GenericMessageScreen(Component.translatable("multiplayer.downloadingTerrain")));
            }
            case "listeners" -> Arrays.stream(argument.split(" ")).map(Swapped::listen).forEach(Driver::note);
            case "log" -> {
                List<String> problems = Problems.drain();
                if (problems.isEmpty()) {
                    note("log: nothing new");
                }
                problems.forEach(problem -> note("log: " + problem));
            }
            case "panorama" -> panorama(minecraft, argument);
            case "particles" -> minecraft.options.particles().set(ParticleStatus.valueOf(argument.toUpperCase(Locale.ROOT)));
            case "perf" -> perf(minecraft, argument.isEmpty() ? 1 : Integer.parseInt(argument));
            case "pos" -> note("pos: %.1f %.1f %.1f facing %.0f %.0f in %s".formatted(minecraft.player.getX(), minecraft.player.getY(),
                    minecraft.player.getZ(), minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.level.dimension().location()));
            case "opens" -> note("opens: " + minecraft.screen.getClass().getName() + " \"" + minecraft.screen.getTitle().getString() + "\"");
            case "pause" -> minecraft.pauseGame(false);
            case "point" -> {
                if (minecraft.screen == null) {
                    note("error: no screen to point at");
                } else {
                    String[] at = argument.split(" ");
                    point(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]));
                    long moved = frames;
                    until = () -> frames > moved + SETTLE_FRAMES;
                }
            }
            case "press" -> press(minecraft, argument);
            case "quit" -> {
                turns = false;
                minecraft.stop();
            }
            case "scale" -> scale(minecraft, Integer.parseInt(argument));
            case "scroll" -> {
                String[] at = argument.split(" ");
                scroll(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]), Double.parseDouble(at[2]));
            }
            case "screen" -> describe(minecraft);
            case "settle" -> framesFrom = -1;
            case "sneak" -> {
                if (minecraft.screen != null) {
                    fail("sneak failed: a screen is open; shift-click with click <x> <y> shift");
                    return;
                }
                boolean on = argument.equals("on");
                minecraft.options.keyShift.setDown(on);
                until = () -> minecraft.player.isShiftKeyDown() == on;
            }
            case "shot" -> {
                framesFrom = -1;
                shot(minecraft, argument);
            }
            case "state" -> command(minecraft, "wayfarer state");
            case "still" -> still(minecraft, argument);
            case "story" -> story(minecraft, argument);
            case "use" -> use(minecraft, argument);
            case "vsync" -> minecraft.options.enableVsync().set(argument.equals("on"));
            case "turn" -> Legs.turn(minecraft, argument);
            case "turns" -> turn(minecraft, argument.equals("on"));
            case "type" -> argument.chars().forEach(character -> {
                Screen typedInto = minecraft.screen;
                if (typedInto != null && !ClientHooks.onScreenCharTypedPre(typedInto, (char) character, 0) && !typedInto.charTyped((char) character, 0)) {
                    ClientHooks.onScreenCharTypedPost(typedInto, (char) character, 0);
                }
            });
            case "skin" -> note(Dressed.wear(minecraft, argument));
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

    private static void still(Minecraft minecraft, String ticks) {
        command(minecraft, ticks.isEmpty() ? "wayfarer still" : "wayfarer still " + ticks);
        waitingOn = "the world to stand still";
        until = () -> sync(minecraft) && Still.standing(minecraft);
        then = () -> {
            note(Still.hold(minecraft));
            if (!ticks.isEmpty()) {
                command(minecraft, "wayfarer still");
            }
        };
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

    private static void perf(Minecraft minecraft, int seconds) {
        Frames.Count count = Frames.count();
        long fromNanos = System.nanoTime();
        until = () -> {
            progressed = System.nanoTime();
            return System.nanoTime() - fromNanos >= seconds * PERF_NANOS;
        };
        waitingOn = "the frames of %d s".formatted(seconds);
        then = () -> {
            Frames.Summary frames = count.stop();
            IntegratedServer server = minecraft.getSingleplayerServer();
            String tick = server == null ? "no server" : "%.1f ms a server tick".formatted(server.getAverageTickTimeNanos() / NANOS_PER_MILLI);
            note("perf: %.0f fps on average over %d s, 1%% low %.0f fps, worst frame %.0f ms, %s, drawn by %s".formatted(frames.fps(), seconds, frames.low(),
                    frames.worstMillis(), tick, GlUtil.getRenderer()));
        };
    }

    private static void fly(LocalPlayer player, boolean on) {
        int[] held = {0};
        until = () -> {
            if (player.getAbilities().flying != on) {
                player.getAbilities().flying = on;
                player.onUpdateAbilities();
                held[0] = 0;
                return false;
            }
            return ++held[0] >= FLIGHT_HOLD_TICKS;
        };
        waitingOn = "the flight to hold";
    }

    private static void graphics(String argument) {
        String[] words = argument.split(" ");
        if (words.length != 3 || !words[1].equals("shaders") || !List.of("on", "off").contains(words[2])) {
            note("error: graphics takes a step and shaders on or off, such as graphics low shaders off");
            return;
        }
        Graphics.shaders(words[2].equals("on"));
        Graphics.choose(Graphics.valueOf(words[0].toUpperCase(Locale.ROOT)));
        note("graphics: " + words[0] + ", shaders " + words[2]);
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

    private static JsonObject seen(Minecraft minecraft) {
        JsonObject json = new JsonObject();
        Tasks.Panel panel = HudState.panel;
        json.addProperty("title", panel.title().getString());
        json.addProperty("objective", panel.objective().getString());
        JsonArray tasks = new JsonArray();
        panel.open().forEach(task -> tasks.add(task.getString()));
        json.add("tasks", tasks);
        json.add("compass", JsonParser.parseString(compass(HudState.compass)));
        GuiAccessor gui = (GuiAccessor) minecraft.gui;
        if (gui.wayfarer$bar() != null && gui.wayfarer$barTime() > 0) {
            json.addProperty("bar", gui.wayfarer$bar().getString());
        }
        String aimed = Senses.crosshair(minecraft);
        if (aimed != null) {
            json.addProperty("crosshair", aimed);
        }
        json.add("body", Senses.body(minecraft));
        return json;
    }

    private static String compass(CompassPayload reading) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", reading.mode().name().toLowerCase(Locale.ROOT));
        if (reading.mode() == CompassPayload.Mode.FAR) {
            json.addProperty("ember", Component.translatable(Hud.heatKey(reading.heat())).getString());
        }
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

    private static void distance(Minecraft minecraft, int chunks) {
        minecraft.options.renderDistance().set(chunks);
        minecraft.options.broadcastOptions();
        int[] most = {0};
        until = () -> {
            ChunkPos center = minecraft.player.chunkPosition();
            int have = 0;
            int wanted = 0;
            for (int dx = 1 - chunks; dx < chunks; dx++) {
                for (int dz = 1 - chunks; dz < chunks; dz++) {
                    if (dx * dx + dz * dz < (chunks - 1) * (chunks - 1)) {
                        wanted++;
                        if (minecraft.level.getChunkSource().hasChunk(center.x + dx, center.z + dz)) {
                            have++;
                        }
                    }
                }
            }
            if (have > most[0]) {
                most[0] = have;
                progressed = System.nanoTime();
            }
            return have == wanted;
        };
    }

    private static void panorama(Minecraft minecraft, String name) {
        float yaw = minecraft.player.getYRot();
        int fov = minecraft.options.fov().get();
        double effects = minecraft.options.fovEffectScale().get();
        // Vanilla's panoramic mode garbles the near terrain under Iris with Distant Horizons, so the faces take its 90 degrees from the options.
        minecraft.options.fov().set(PANORAMA_FOV);
        minecraft.options.fovEffectScale().set(0.0);
        minecraft.gameRenderer.setRenderBlockOutline(false);
        bare = true;
        face(minecraft, name, 0, yaw, () -> {
            bare = false;
            minecraft.options.fov().set(fov);
            minecraft.options.fovEffectScale().set(effects);
            minecraft.gameRenderer.setRenderBlockOutline(true);
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(0);
        });
    }

    private static void face(Minecraft minecraft, String name, int face, float yaw, Runnable done) {
        LocalPlayer player = minecraft.player;
        player.setYRot(yaw + PANORAMA_FACES[face][0]);
        player.setXRot(PANORAMA_FACES[face][1]);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        long[] steady = {-1};
        until = () -> {
            if (!rendered(minecraft)) {
                steady[0] = -1;
                return false;
            }
            if (steady[0] < 0) {
                steady[0] = frames;
            }
            return frames >= steady[0] + PANORAMA_FRAMES;
        };
        then = () -> {
            Path path = minecraft.gameDirectory.toPath().resolve("screenshots/panorama").resolve(name).resolve("panorama_" + face + ".png")
                    .toAbsolutePath().normalize();
            try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
                    NativeImage square = new NativeImage(PANORAMA_SIZE, PANORAMA_SIZE, false)) {
                int side = Math.min(image.getWidth(), image.getHeight());
                image.resizeSubRectTo((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side, square);
                Files.createDirectories(path.getParent());
                square.writeToFile(path);
                note("screenshot: " + path);
            } catch (IOException exception) {
                note("error: cannot save " + path + ": " + exception.getMessage());
            }
            if (face + 1 < PANORAMA_FACES.length) {
                face(minecraft, name, face + 1, yaw, done);
            } else {
                done.run();
            }
        };
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
                .filter(pos -> Hands.same(minecraft.level.getBlockState(pos), block))
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

    private static void hold(Minecraft minecraft, String argument) {
        String[] parts = argument.split(" ");
        boolean on = parts.length > 1 && parts[1].equals("on");
        Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.getName().equals(parts[0])).findFirst()
                .ifPresentOrElse(mapping -> {
                    mapping.setDown(on);
                    if (on) {
                        KeyMapping.click(mapping.getKey());
                    }
                }, () -> note("error: no key mapping " + parts[0]));
    }

    private static void key(Minecraft minecraft, String name) {
        if (name.equals("escape")) {
            if (minecraft.screen != null) {
                minecraft.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            return;
        }
        if (!name.startsWith("key.")) {
            String[] parts = name.split("\\+");
            int modifiers = Arrays.stream(parts, 0, parts.length - 1).mapToInt(part -> switch (part) {
                case "ctrl" -> GLFW.GLFW_MOD_CONTROL;
                case "shift" -> GLFW.GLFW_MOD_SHIFT;
                case "alt" -> GLFW.GLFW_MOD_ALT;
                default -> throw new IllegalArgumentException("no modifier " + part);
            }).reduce(0, (a, b) -> a | b);
            if (modifiers == GLFW.GLFW_MOD_CONTROL && parts[parts.length - 1].equals("a") && minecraft.screen != null
                    && minecraft.screen.getFocused() instanceof EditBox field) {
                field.moveCursorToEnd(false);
                field.setHighlightPos(0);
                return;
            }
            InputConstants.Key pressed = InputConstants.getKey("key.keyboard." + parts[parts.length - 1]);
            long window = minecraft.getWindow().getWindow();
            minecraft.keyboardHandler.keyPress(window, pressed.getValue(), 0, GLFW.GLFW_PRESS, modifiers);
            minecraft.keyboardHandler.keyPress(window, pressed.getValue(), 0, GLFW.GLFW_RELEASE, modifiers);
            return;
        }
        Arrays.stream(minecraft.options.keyMappings)
                .filter(mapping -> mapping.getName().equals(name))
                .findFirst()
                .ifPresentOrElse(mapping -> {
                    boolean typed = mapping.getKey().getType() == InputConstants.Type.KEYSYM && !mapping.isUnbound();
                    if (minecraft.screen != null || typed) {
                        long window = minecraft.getWindow().getWindow();
                        minecraft.keyboardHandler.keyPress(window, mapping.getKey().getValue(), 0, GLFW.GLFW_PRESS, 0);
                        minecraft.keyboardHandler.keyPress(window, mapping.getKey().getValue(), 0, GLFW.GLFW_RELEASE, 0);
                    } else {
                        KeyMapping.click(mapping.getKey());
                    }
                    if (minecraft.screen == null) {
                        long[] taken = {-1};
                        int[] ticks = {0};
                        until = () -> {
                            if (taken[0] < 0 && (clicks(mapping) == 0 || ++ticks[0] > 1)) {
                                unclick(mapping);
                                taken[0] = frames;
                            }
                            return taken[0] >= 0 && frames > taken[0];
                        };
                    }
                }, () -> note("error: no key mapping " + name));
    }

    private static int clicks(KeyMapping mapping) {
        try {
            return CLICK_COUNT.getInt(mapping);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void unclick(KeyMapping mapping) {
        try {
            CLICK_COUNT.setInt(mapping, 0);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static boolean shifting() {
        return shifting;
    }

    private static void click(Minecraft minecraft, double x, double y, String name, boolean shifted) {
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
            int mods = shifted ? GLFW.GLFW_MOD_SHIFT : 0;
            shifting = shifted;
            mouse(minecraft, MOUSE_PRESS, button, GLFW.GLFW_PRESS, mods);
            mouse(minecraft, MOUSE_PRESS, button, GLFW.GLFW_RELEASE, mods);
            shifting = false;
            return true;
        };
    }

    private static void scroll(Minecraft minecraft, double x, double y, double lines) {
        if (minecraft.screen == null) {
            fail("scroll failed: no screen is open");
            return;
        }
        point(minecraft, x, y);
        long moved = frames;
        until = () -> {
            if (frames <= moved + SETTLE_FRAMES) {
                return false;
            }
            mouse(minecraft, MOUSE_SCROLL, 0.0, -lines);
            return true;
        };
    }

    private static void pitch(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        int slot = IntStream.range(0, Inventory.getSelectionSize()).filter(index -> player.getInventory().getItem(index).getItem() instanceof TentItem)
                .findFirst().orElse(-1);
        if (slot < 0) {
            fail("pitch failed: no tent in the hotbar");
            return;
        }
        Pitch.ask(slot);
        after(() -> Pitch.answer() != null, "waiting for the server to find room for the tent", () -> {
            Pitch.Found found = Pitch.answer();
            if (!found.room()) {
                fail("pitch failed: " + found.why());
                return;
            }
            BlockPos stand = found.stand();
            note("pitch: room for the tent " + Math.round(Math.sqrt(player.blockPosition().distSqr(found.door()))) + " blocks off at "
                    + found.door().toShortString() + ", its door facing " + found.direction().getOpposite().getSerializedName());
            long[] ticks = {0};
            after(() -> {
                if (player.blockPosition().equals(stand)) {
                    Legs.halt();
                    return true;
                }
                if (ticks[0]++ % REWALK_TICKS == 0) {
                    Legs.walkTo(stand, 0);
                }
                progressed = player.getDeltaMovement().horizontalDistanceSqr() > 0 ? System.nanoTime() : progressed;
                return false;
            }, "walking to the tent's spot at " + stand.toShortString(), () -> {
                player.getInventory().selected = slot;
                BlockState spot = minecraft.level.getBlockState(found.door());
                useAt(minecraft, spot.isAir() ? found.door().below() : found.door(), missed -> {
                    long[] waited = {0};
                    after(() -> minecraft.level.getBlockState(found.door()).getBlock() instanceof TentDoorBlock || waited[0]++ > PITCH_TICKS,
                            "the tent going up", () -> {
                                if (minecraft.level.getBlockState(found.door()).getBlock() instanceof TentDoorBlock) {
                                    note("pitch: the tent stands at " + found.door().toShortString());
                                } else {
                                    fail("pitch failed: the tent did not go up at " + found.door().toShortString()
                                            + (missed == null ? "; the action bar says why" : "; the click went to " + missed));
                                }
                            });
                });
            });
        });
    }

    private static void point(Minecraft minecraft, double x, double y) {
        Window window = minecraft.getWindow();
        mouse(minecraft, MOUSE_MOVE, x * window.getScreenWidth() / window.getGuiScaledWidth(), y * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static void interact(Minecraft minecraft, String type) {
        Optional<Entity> found = Hands.creature(minecraft, type, INTERACT_REACH);
        if (found.isEmpty()) {
            fail("interact failed: no " + type + " within " + (int) INTERACT_REACH + " blocks" + Hands.near(minecraft, INTERACT_REACH));
            return;
        }
        clickOn(minecraft, found.get(), "interact", missed -> {
            if (missed != null) {
                note("interact: the click went to " + missed + ", not to " + Senses.describe(found.get()));
            }
        });
    }

    static void clickOn(Minecraft minecraft, Entity target, String verb, Consumer<String> done) {
        LocalPlayer player = minecraft.player;
        double reach = player.entityInteractionRange() - REACH_MARGIN;
        long[] ticks = {0};
        long from = minecraft.level.getGameTime();
        after(() -> {
            if (!target.isAlive() || target.isRemoved()) {
                return true;
            }
            player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            boolean near = target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
            if (near || minecraft.level.getGameTime() - from > STEP_IN_TICKS) {
                if (ModList.get().isLoaded(BARITONE)) {
                    Legs.halt();
                }
                return true;
            }
            if (!ModList.get().isLoaded(BARITONE)) {
                fail(verb + " failed: " + Senses.describe(target) + " is out of reach, " + Math.round(target.distanceTo(player)) + " blocks, and lean mode has no legs");
                return true;
            }
            if (ticks[0]++ % REWALK_TICKS == 0) {
                Legs.walkTo(target.blockPosition(), 1);
            }
            progressed = System.nanoTime();
            return false;
        }, "reaching " + Senses.describe(target), () -> {
            if (!target.isAlive() || target.isRemoved()) {
                fail(verb + " failed: " + Senses.describe(target) + " is gone");
                return;
            }
            clearThenClick(minecraft, target, verb, done, WAY_CLEARS);
        });
    }

    static void click(Minecraft minecraft, Vec3 at, Predicate<HitResult> onTarget, boolean strict, String verb, String what, Consumer<String> done) {
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        drawn("the crosshair to settle on " + what, () -> {
            String aimed = Senses.aimed(minecraft);
            if (aimed == null) {
                fail(verb + " failed: nothing within reach there, " + Math.round(minecraft.player.getEyePosition().distanceTo(at)) + " blocks");
                return;
            }
            boolean hit = onTarget.test(minecraft.hitResult);
            if (strict && !hit) {
                fail(verb + " failed: the click would go to " + aimed + ", not to " + what);
                return;
            }
            KeyMapping.click(minecraft.options.keyUse.getKey());
            after(() -> clicks(minecraft.options.keyUse) == 0, "the click", () -> {
                synced = false;
                done.accept(hit ? null : aimed);
            });
        });
    }

    private static void shiftClick(Minecraft minecraft, double x, double y) {
        Optional<Slot> slot = minecraft.screen instanceof AbstractContainerScreen<?> container ? container.getMenu().slots.stream()
                .filter(each -> each.isActive() && x >= container.getGuiLeft() + each.x && x < container.getGuiLeft() + each.x + SLOT_SIZE
                        && y >= container.getGuiTop() + each.y && y < container.getGuiTop() + each.y + SLOT_SIZE)
                .findFirst() : Optional.empty();
        if (slot.isEmpty()) {
            click(minecraft, x, y, "left", true);
            return;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) minecraft.screen;
        minecraft.gameMode.handleInventoryMouseClick(container.getMenu().containerId, slot.get().index, 0, ClickType.QUICK_MOVE, minecraft.player);
        synced = false;
    }

    static BlockPos block(Minecraft minecraft, String x, String y, String z) {
        BlockPos here = minecraft.player.blockPosition();
        return new BlockPos(coordinate(x, here.getX()), coordinate(y, here.getY()), coordinate(z, here.getZ()));
    }

    private static int coordinate(String text, int from) {
        return text.startsWith(RELATIVE) ? from + (text.length() > 1 ? Integer.parseInt(text.substring(1)) : 0) : Integer.parseInt(text);
    }

    private static double axis(String text, int from) {
        String number = text.startsWith(RELATIVE) ? text.substring(1) : text;
        double value = (text.startsWith(RELATIVE) ? from : 0) + (number.isEmpty() ? 0 : Double.parseDouble(number));
        return number.contains(".") ? value : value + HALF;
    }

    private static void face(Minecraft minecraft, String argument) {
        String[] xyz = argument.split(" ");
        BlockPos here = minecraft.player.blockPosition();
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(axis(xyz[0], here.getX()), axis(xyz[1], here.getY()), axis(xyz[2], here.getZ())));
    }

    private static void use(Minecraft minecraft, String argument) {
        if (minecraft.screen != null) {
            fail("use failed: " + minecraft.screen.getTitle().getString() + " is open; close it first");
            return;
        }
        String[] xyz = argument.split(" ");
        BlockPos pos = block(minecraft, xyz[0], xyz[1], xyz[2]);
        String name = minecraft.level.getBlockState(pos).getBlock().getName().getString();
        Optional<Entity> box = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Interaction && entity.getBoundingBox().intersects(new AABB(pos)))
                .min(Comparator.comparingDouble(entity -> entity.distanceTo(minecraft.player)));
        if (box.isPresent()) {
            clickOn(minecraft, box.get(), "use", missed -> note(missed == null ? "use: the interaction box over " + name
                    : "use: the click went to " + missed + ", not to the interaction box over " + name));
            return;
        }
        useAt(minecraft, pos, missed -> note(missed == null ? "use: " + name : "use: the click went to " + missed + ", not to " + name));
    }

    static void useAt(Minecraft minecraft, BlockPos pos, Consumer<String> done) {
        Block block = minecraft.level.getBlockState(pos).getBlock();
        within(minecraft, pos, at -> click(minecraft, at,
                hit -> hit instanceof BlockHitResult aimed && (aimed.getBlockPos().equals(pos)
                        || aimed.getBlockPos().distManhattan(pos) == 1 && minecraft.level.getBlockState(aimed.getBlockPos()).is(block)),
                false, "use", block.getName().getString(), done));
    }

    private static void dig(Minecraft minecraft, String argument) {
        String[] xyz = argument.split(" ");
        digAt(minecraft, block(minecraft, xyz[0], xyz[1], xyz[2]));
    }

    private static void digAt(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.isAir()) {
            fail("dig failed: nothing stands at " + pos.toShortString());
            return;
        }
        String name = state.getBlock().getName().getString();
        LocalPlayer player = minecraft.player;
        within(minecraft, pos, at -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
            drawn("the crosshair to settle on " + name, () -> {
                if (!(minecraft.hitResult instanceof BlockHitResult aimed) || aimed.getType() == HitResult.Type.MISS || !aimed.getBlockPos().equals(pos)) {
                    String hit = Senses.aimed(minecraft);
                    fail("dig failed: " + (hit == null ? "nothing within reach there" : "the crosshair is on " + hit + ", not on the " + name));
                    return;
                }
                breakBlock(minecraft, pos, aimed.getDirection(), "dig", broken -> note("dig: broke the " + name + " with " + broken), () -> digAt(minecraft, pos));
            });
        });
    }

    private static void breakBlock(Minecraft minecraft, BlockPos pos, Direction side, String verb, Consumer<String> then, Runnable again) {
        LocalPlayer player = minecraft.player;
        Block block = minecraft.level.getBlockState(pos).getBlock();
        String name = block.getName().getString();
        String hand = player.getMainHandItem().isEmpty() ? "a bare hand" : player.getMainHandItem().getHoverName().getString();
        long from = minecraft.level.getGameTime();
        boolean[] interrupted = {false};
        minecraft.gameMode.startDestroyBlock(pos, side);
        after(() -> {
            if (Body.acting()) {
                minecraft.gameMode.stopDestroyBlock();
                interrupted[0] = true;
                return true;
            }
            if (!minecraft.level.getBlockState(pos).is(block) || minecraft.level.getGameTime() - from > DIG_TICKS) {
                return true;
            }
            minecraft.gameMode.continueDestroyBlock(pos, side);
            player.swing(InteractionHand.MAIN_HAND);
            progressed = System.nanoTime();
            return false;
        }, "breaking the " + name, () -> {
            if (interrupted[0]) {
                after(() -> {
                    progressed = System.nanoTime();
                    return !Body.acting();
                }, "the body to be done before breaking the " + name, again);
                return;
            }
            if (minecraft.level.getBlockState(pos).is(block)) {
                minecraft.gameMode.stopDestroyBlock();
                fail(verb + " failed: the " + name + " did not break in " + DIG_TICKS / SharedConstants.TICKS_PER_SECOND + " s with " + hand);
                return;
            }
            synced = false;
            then.accept(hand);
        });
    }

    private static boolean soft(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.hasBlockEntity() && state.getDestroySpeed(minecraft.level, pos) >= 0 && state.getDestroySpeed(minecraft.level, pos) <= SOFT_HARDNESS;
    }

    private static void clearThenClick(Minecraft minecraft, Entity target, String verb, Consumer<String> done, int clears) {
        Vec3 centre = target.getBoundingBox().getCenter();
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, centre);
        drawn("the crosshair to settle on " + Senses.describe(target), () -> {
            if (clears > 0 && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && soft(minecraft, hit.getBlockPos())) {
                BlockPos way = hit.getBlockPos();
                String name = minecraft.level.getBlockState(way).getBlock().getName().getString();
                breakBlock(minecraft, way, hit.getDirection(), verb, hand -> {
                    note(verb + ": broke the " + name + " in the way at " + way.toShortString());
                    clearThenClick(minecraft, target, verb, done, clears - 1);
                }, () -> clearThenClick(minecraft, target, verb, done, clears));
                return;
            }
            click(minecraft, centre, hit -> hit instanceof EntityHitResult entity && entity.getEntity() == target, false, verb, Senses.describe(target), done);
        });
    }

    private static void within(Minecraft minecraft, BlockPos pos, Consumer<Vec3> then) {
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 towards = minecraft.player.getEyePosition().subtract(centre).normalize();
        Direction face = Arrays.stream(Direction.values()).filter(side -> minecraft.level.getBlockState(pos.relative(side)).canBeReplaced())
                .max(Comparator.comparingDouble(side -> side.getStepX() * towards.x + side.getStepY() * towards.y + side.getStepZ() * towards.z))
                .orElse(Direction.getNearest(towards.x, towards.y, towards.z));
        VoxelShape shape = minecraft.level.getBlockState(pos).getShape(minecraft.level, pos);
        Vec3 at = shape.isEmpty() || shape.bounds().getSize() >= 1 ? centre.add(face.getStepX() * HALF, face.getStepY() * HALF, face.getStepZ() * HALF)
                : shape.bounds().getCenter().add(Vec3.atLowerCornerOf(pos));
        Block block = minecraft.level.getBlockState(pos).getBlock();
        LocalPlayer player = minecraft.player;
        double reach = player.blockInteractionRange() - REACH_MARGIN;
        long[] ticks = {0};
        long from = minecraft.level.getGameTime();
        after(() -> {
            if (player.getEyePosition().distanceTo(at) <= reach) {
                if (ModList.get().isLoaded(BARITONE) && ticks[0] > 0) {
                    Legs.halt();
                }
                return true;
            }
            if (!ModList.get().isLoaded(BARITONE) || minecraft.level.getGameTime() - from > STEP_IN_TICKS) {
                return true;
            }
            if (ticks[0]++ % REWALK_TICKS == 0) {
                Legs.walkTo(pos.relative(face), 1);
            }
            progressed = System.nanoTime();
            return false;
        }, "stepping within reach of " + block.getName().getString(), () -> then.accept(at));
    }

    private static void embark(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Optional<Entity> boat = StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Boat && entity.distanceTo(player) <= INTERACT_REACH)
                .min(Comparator.comparingDouble(player::distanceTo));
        if (boat.isEmpty()) {
            launch(minecraft);
            return;
        }
        board(minecraft, boat.get());
    }

    private static void launch(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        int slot = IntStream.range(0, Inventory.getSelectionSize()).filter(index -> player.getInventory().getItem(index).getItem() instanceof BoatItem)
                .findFirst().orElse(-1);
        if (slot < 0) {
            fail("embark failed: no boat or raft within " + (int) INTERACT_REACH + " blocks, and none in the hotbar");
            return;
        }
        BlockPos feet = player.blockPosition();
        Optional<BlockPos> water = BlockPos.betweenClosedStream(feet.offset(-LAUNCH_BLOCKS, -2, -LAUNCH_BLOCKS), feet.offset(LAUNCH_BLOCKS, 1, LAUNCH_BLOCKS))
                .map(BlockPos::immutable)
                .filter(pos -> BlockPos.betweenClosedStream(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))
                        .allMatch(around -> minecraft.level.getFluidState(around).is(FluidTags.WATER) && minecraft.level.getBlockState(around.above()).isAir())
                        && minecraft.level.getBlockState(pos.above(2)).isAir()
                        && Math.hypot(pos.getX() + HALF - player.getX(), pos.getZ() + HALF - player.getZ()) >= LAUNCH_CLEAR)
                .min(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceTo(player.getEyePosition())));
        if (water.isEmpty()) {
            fail("embark failed: no open water within " + LAUNCH_BLOCKS + " blocks to put the boat on");
            return;
        }
        player.getInventory().selected = slot;
        Vec3 surface = Vec3.atBottomCenterOf(water.get()).add(0, WATER_TOP, 0);
        Vec3 look = surface.subtract(player.getEyePosition());
        player.setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90);
        player.setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
        String name = player.getMainHandItem().getHoverName().getString();
        long drawn = frames;
        after(() -> frames > drawn + SETTLE_FRAMES, "facing the water", () -> {
            minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            long from = minecraft.level.getGameTime();
            after(() -> nearestBoat(minecraft).isPresent() || minecraft.level.getGameTime() - from > LAUNCH_TICKS, "putting the " + name + " on the water", () -> {
                Optional<Entity> placed = nearestBoat(minecraft);
                if (placed.isEmpty()) {
                    fail("embark failed: the " + name + " did not go onto the water at " + water.get().toShortString());
                    return;
                }
                note("embark: put the " + name + " on the water");
                board(minecraft, placed.get());
            });
        });
    }

    private static Optional<Entity> nearestBoat(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return StreamSupport.stream(minecraft.level.entitiesForRendering().spliterator(), false)
                .filter(entity -> entity instanceof Boat && entity.distanceTo(player) <= INTERACT_REACH)
                .min(Comparator.comparingDouble(player::distanceTo));
    }

    private static void board(Minecraft minecraft, Entity target) {
        LocalPlayer player = minecraft.player;
        String name = target.getName().getString();
        if (player.getVehicle() == target) {
            note("embark: in the " + name + " already");
            return;
        }
        after(() -> {
            minecraft.options.keyJump.setDown(player.isUnderWater());
            return !player.isUnderWater();
        }, "coming up out of the water", () -> {
            minecraft.options.keyJump.setDown(false);
            clickOn(minecraft, target, "embark", missed -> {
                if (missed != null) {
                    fail("embark failed: the click went to " + missed + ", not to the " + name);
                    return;
                }
                after(() -> player.getVehicle() == target, "getting into the " + name, () -> note("embark: in the " + name));
            });
        });
    }

    private static void dismount(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player.getVehicle() == null) {
            fail("dismount failed: you ride nothing");
            return;
        }
        Entity ridden = player.getVehicle();
        String own = "key." + BuiltInRegistries.ENTITY_TYPE.getKey(ridden.getType()).getNamespace() + ".dismount";
        Optional<KeyMapping> dismount = Arrays.stream(minecraft.options.keyMappings).filter(mapping -> mapping.getName().equals(own)).findFirst();
        if (dismount.isPresent() && dismount.get().isUnbound()) {
            fail("dismount failed: the " + ridden.getName().getString() + " gets off with its own key, " + Component.translatable(own).getString()
                    + ", and it is not bound");
            return;
        }
        minecraft.options.keyShift.setDown(true);
        dismount.ifPresent(mapping -> mapping.setDown(true));
        after(() -> {
            dismount.filter(mapping -> clicks(mapping) == 0).ifPresent(mapping -> KeyMapping.click(mapping.getKey()));
            return player.getVehicle() == null;
        }, "getting off", () -> {
            minecraft.options.keyShift.setDown(false);
            dismount.ifPresent(mapping -> mapping.setDown(false));
            note("dismount: " + (player.isInWater() ? "in the water" : "on foot") + " at " + player.blockPosition().toShortString());
            if (ridden instanceof Boat boat && !player.isInWater()) {
                takeBoat(minecraft, boat);
            }
        });
    }

    private static void takeBoat(Minecraft minecraft, Boat boat) {
        LocalPlayer player = minecraft.player;
        String name = boat.getName().getString();
        long from = minecraft.level.getGameTime();
        int[] swings = {0};
        after(() -> {
            if (boat.isRemoved() || minecraft.level.getGameTime() - from > TAKE_BOAT_TICKS || boat.distanceTo(player) > player.entityInteractionRange()) {
                return true;
            }
            Vec3 look = boat.getBoundingBox().getCenter().subtract(player.getEyePosition());
            player.setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90);
            player.setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
            if (minecraft.level.getGameTime() % SWING_TICKS == 0 && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() == boat) {
                KeyMapping.click(minecraft.options.keyAttack.getKey());
                swings[0]++;
            }
            return false;
        }, "breaking the " + name + " to take it along", () -> {
            long dropped = minecraft.level.getGameTime();
            after(() -> minecraft.level.getGameTime() - dropped > PICKUP_TICKS, "picking the " + name + " up",
                    () -> note("dismount: " + (boat.isRemoved() ? "took the " + name + " along" : "left the " + name + " on the water, out of reach")));
        });
    }

    private static void cheat(String command) {
        PacketDistributor.sendToServer(new Cheat.Run(command.startsWith("/") ? command.substring(1) : command));
        synced = false;
        note("cheat: kept in the play record as a workaround; write it into the log");
    }

    static void progress() {
        progressed = System.nanoTime();
    }

    static void fail(String why) {
        abandon();
        until = null;
        then = null;
        waitingOn = null;
        note("error: %s failed at once: %s".formatted(runningStep(), why));
    }

    private static void abandon() {
        steps.removeIf(step -> !LOOKING.contains(step.split(" ", 2)[0]));
    }

    static void after(BooleanSupplier condition, String what, Runnable next) {
        until = condition;
        waitingOn = what;
        then = next;
    }

    static void drawn(String what, Runnable next) {
        long from = frames;
        after(() -> frames > from + SETTLE_FRAMES, what, next);
    }

    static boolean answered(Minecraft minecraft) {
        return sync(minecraft);
    }

    static void unsynced() {
        synced = false;
    }

    static void say(String line) {
        note(line);
    }

    private static void press(Minecraft minecraft, String label) {
        long drawn = frames;
        long from = System.nanoTime();
        until = () -> frames > drawn + SETTLE_FRAMES && (labelled(minecraft, label) != null || System.nanoTime() - from > LABEL_NANOS);
        then = () -> {
            AbstractWidget widget = labelled(minecraft, label);
            if (widget == null) {
                note("error: no widget labelled " + label);
                return;
            }
            for (int turn = 0; turn < SCROLL_TURNS && minecraft.screen != null && widget.getY() + widget.getHeight() > minecraft.screen.height * 3 / 4; turn++) {
                int before = widget.getY();
                minecraft.screen.mouseScrolled(widget.getX() + 1, Math.min(widget.getY(), minecraft.screen.height - 1), 0, -1);
                if (widget.getY() == before) {
                    break;
                }
            }
            click(minecraft, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, "left", false);
        };
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
        if (screen instanceof AbstractContainerScreen<?> container) {
            for (Slot slot : container.getMenu().slots) {
                ItemStack stack = slot.getItem();
                note("slot: %d %s at %d,%d".formatted(slot.index, stack.isEmpty() ? "empty" : stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()),
                        container.getGuiLeft() + slot.x + SLOT_MIDDLE, container.getGuiTop() + slot.y + SLOT_MIDDLE));
            }
        }
    }

    private static List<AbstractWidget> widgets(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return List.of();
        }
        List<AbstractWidget> found = new ArrayList<>();
        widgets(minecraft.screen.children(), found);
        return found;
    }

    private static void widgets(List<? extends GuiEventListener> children, List<AbstractWidget> found) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && !(child instanceof ContainerEventHandler)) {
                found.add(widget);
            }
            if (child instanceof ContainerEventHandler container) {
                widgets(container.children(), found);
            }
        }
    }
}
