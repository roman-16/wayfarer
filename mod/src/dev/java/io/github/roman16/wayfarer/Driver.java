package io.github.roman16.wayfarer;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
public final class Driver {
    private static final boolean SHOWROOM = Boolean.getBoolean(Wayfarer.MODID + ".showroom");
    private static final String PLAY_WORLD = "play";
    private static final String SHOWROOM_WORLD = "showroom";
    private static final long SEED = 20260929L;
    private static final int FIND_LIMIT = 20;
    private static final int FIND_RADIUS = 32;
    private static final int SETTLE_CHUNKS = 3;
    private static final int SETTLE_FRAMES = 2;
    private static final String SENTINEL = "execute if entity @s";
    private static final String SENTINEL_REPLY = "commands.execute.conditional.pass_count";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<String> steps = new ArrayDeque<>();
    private static final List<String> log = new ArrayList<>();
    private static boolean opened;
    private static boolean running;
    private static boolean synced = true;
    private static boolean sentinelSent;
    private static int waiting;
    private static long frames;
    private static long settledFrom = -1;

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
        if (running && take(folder.resolve("abort"))) {
            steps.clear();
            waiting = 0;
            sentinelSent = false;
            synced = true;
            note("aborted");
            finish(folder);
            return;
        }
        if (!running) {
            load(folder.resolve("commands.txt"));
            return;
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        while (!steps.isEmpty()) {
            String step = steps.peek();
            if (!ready(minecraft, step)) {
                return;
            }
            steps.poll();
            note("> " + step);
            run(minecraft, step);
            if (waiting > 0) {
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
        if (SHOWROOM) {
            begin(JsonParser.parseString(resource("/wayfarer/showroom.json")).getAsJsonObject().getAsJsonArray("steps").asList().stream()
                    .map(step -> step.getAsString())
                    .toList());
            enter(minecraft, SHOWROOM_WORLD, GameType.CREATIVE, SEED);
        } else {
            begin(List.of("settle"));
            enter(minecraft, PLAY_WORLD, GameType.SURVIVAL, WorldOptions.randomSeed());
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

    private static void begin(List<String> lines) {
        lines.stream().map(String::strip).filter(line -> !line.isEmpty()).forEach(steps::add);
        log.clear();
        running = true;
    }

    private static void finish(Path folder) {
        running = false;
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
            if (minecraft.player == null || !sync(minecraft) || !loaded(minecraft)) {
                return false;
            }
            if (settledFrom < 0) {
                settledFrom = frames;
            }
            return frames > settledFrom + SETTLE_FRAMES && rendered(minecraft);
        }
        return minecraft.player != null || !(step.startsWith("/") || verb.equals("fly") || verb.equals("key"));
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
            case "click" -> {
                String[] at = argument.split(" ");
                click(minecraft, Double.parseDouble(at[0]), Double.parseDouble(at[1]));
            }
            case "close" -> minecraft.setScreen(null);
            case "find" -> find(minecraft, argument);
            case "fly" -> {
                minecraft.player.getAbilities().flying = argument.equals("on");
                minecraft.player.onUpdateAbilities();
            }
            case "gui" -> minecraft.options.hideGui = argument.equals("off");
            case "key" -> key(minecraft, argument);
            case "pos" -> note("pos: %.1f %.1f %.1f facing %.0f %.0f in %s".formatted(minecraft.player.getX(), minecraft.player.getY(),
                    minecraft.player.getZ(), minecraft.player.getYRot(), minecraft.player.getXRot(), minecraft.level.dimension().location()));
            case "press" -> press(minecraft, argument);
            case "quit" -> minecraft.stop();
            case "screen" -> describe(minecraft);
            case "settle" -> settledFrom = -1;
            case "shot" -> shot(minecraft, argument);
            case "type" -> argument.chars().forEach(character -> {
                if (minecraft.screen != null) {
                    minecraft.screen.charTyped((char) character, 0);
                }
            });
            case "wait" -> waiting = Integer.parseInt(argument);
            default -> {
                if (step.startsWith("/")) {
                    synced = false;
                    minecraft.player.connection.sendCommand(step.substring(1));
                } else {
                    note("error: unknown step " + step);
                }
            }
        }
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
                .ifPresentOrElse(mapping -> KeyMapping.click(mapping.getKey()), () -> note("error: no key mapping " + name));
    }

    private static void click(Minecraft minecraft, double x, double y) {
        if (minecraft.screen == null) {
            note("error: no screen to click");
            return;
        }
        Screen screen = minecraft.screen;
        screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void press(Minecraft minecraft, String label) {
        widgets(minecraft).stream()
                .filter(widget -> widget.getMessage().getString().equals(label))
                .findFirst()
                .ifPresentOrElse(
                        widget -> click(minecraft, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0),
                        () -> note("error: no widget labelled " + label));
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
