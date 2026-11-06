package io.github.roman16.wayfarer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Narration;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.mixin.ConnectScreenAccessor;
import io.github.roman16.wayfarer.mixin.LevelLoadingScreenAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

public final class Recap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "recaps.json";
    private static final String LINES = "lines";
    private static final String STORY = "story";
    private static final String TITLE = "title";
    private static final String SERVER = "server:";
    private static final Component TERRAIN = Component.translatable("multiplayer.downloadingTerrain");
    private static final Page FIRST = new Page("", List.of(Component.translatable("wayfarer.recap.first")));
    private static final Page WAITING = new Page("", List.of());
    private static String world = "";
    private static String server = "";
    private static final int MARGIN = 12;
    private static final int MAX_WIDTH = 224;
    private static final int PERCENT = 100;
    private static final int STATUS_GAP = 6;
    @Nullable
    private static JsonObject kept;
    @Nullable
    private static Page loading;
    private static long shownAt;

    private Recap() {
    }

    public static void begin(String world) {
        server = "";
        show(world);
    }

    private static void show(String world) {
        JsonElement page = kept().get(world);
        loading = page == null ? FIRST : Page.of(page.getAsJsonObject());
    }

    public static void connecting(String address) {
        server = SERVER + address;
        JsonElement page = kept().get(server);
        loading = page == null ? WAITING : Page.of(page.getAsJsonObject());
    }

    public static String world() {
        return world;
    }

    public static void world(String id) {
        world = id;
        if (loading != null) {
            show(id);
        }
    }

    static void remember(String view) {
        String key = world;
        if (key.isEmpty()) {
            return;
        }
        JsonArray story = JsonParser.parseString(view).getAsJsonObject().getAsJsonArray(STORY);
        JsonObject page = null;
        if (story != null && !story.isEmpty()) {
            JsonObject chapter = story.get(story.size() - 1).getAsJsonObject();
            page = new JsonObject();
            page.add(TITLE, chapter.get(TITLE));
            page.add(LINES, chapter.get(LINES));
        }
        JsonObject recaps = kept();
        if (Objects.equals(recaps.get(key), page) && (server.isEmpty() || Objects.equals(recaps.get(server), page))) {
            return;
        }
        for (String kept : server.isEmpty() ? List.of(key) : List.of(key, server)) {
            if (page == null) {
                recaps.remove(kept);
            } else {
                recaps.add(kept, page);
            }
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(recaps));
        } catch (IOException exception) {
            LOGGER.error("Cannot keep the story's last page in {}", file(), exception);
        }
    }

    static void tick(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        boolean menu = screen instanceof TitleScreen || screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen
                || screen instanceof DisconnectedScreen;
        if (menu || minecraft.level != null && status(screen) == null) {
            if (loading != null && shownAt != 0) {
                if (menu) {
                    Speech.stopPages();
                } else {
                    Speech.fade();
                }
            }
            loading = null;
            shownAt = 0;
        }
    }

    public static boolean holding() {
        return loading != null && !loading.told().isEmpty() && (shownAt == 0 || Util.getMillis() - shownAt < Stamp.RECAP_HOLD_MILLIS);
    }

    private static void speak(Page page) {
        shownAt = Util.getMillis();
        if (page.told().isEmpty()) {
            return;
        }
        String newest = page.told().getLast().getString();
        Optional<Narration.Clip> clip = page == FIRST ? Narration.said(null, newest, false) : Narration.chosen(YourVoice.chosen().id(), newest);
        clip.ifPresent(found -> Speech.pages(List.of(found.path())));
    }

    static void render(ScreenEvent.Render.Pre event) {
        Page page = loading;
        Screen screen = event.getScreen();
        Component status = status(screen);
        if (page == null || status == null) {
            return;
        }
        if (shownAt == 0) {
            speak(page);
        }
        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        screen.renderBackground(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        int floor = screen.height - MARGIN;
        for (Renderable renderable : screen.renderables) {
            if (renderable instanceof AbstractButton button) {
                floor = Math.min(floor, button.getY() - STATUS_GAP);
            }
        }
        int width = Math.min(MAX_WIDTH, screen.width - MARGIN * 2);
        int pageWidth = width - JournalScreen.COVER_EDGE * 2 - JournalScreen.SPINE;
        int textWidth = pageWidth - JournalScreen.PAD * 2;
        int head = page.title().isEmpty() ? Stamp.MARK_HEIGHT + JournalScreen.TITLE_GAP
                : Stamp.DISPLAY_HEIGHT + JournalScreen.TITLE_GAP + Stamp.DIVIDER_HEIGHT + 2;
        int frame = JournalScreen.COVER_EDGE * 2 + JournalScreen.PAD * 2 + head;
        int room = floor - MARGIN - STATUS_GAP - font.lineHeight;
        List<FormattedCharSequence> lines = page.tail(font, textWidth, Math.max(0, (room - frame) / JournalScreen.LINE));
        int height = frame + lines.size() * JournalScreen.LINE;
        int left = (screen.width - width) / 2;
        int top = MARGIN + (room - height) / 2;
        int pagesTop = top + JournalScreen.COVER_EDGE;
        int spine = left + JournalScreen.COVER_EDGE;
        int pageLeft = spine + JournalScreen.SPINE;
        graphics.blitSprite(Stamp.BOOK, left, top, width, height);
        graphics.blitSprite(Stamp.SPINE, spine, pagesTop, JournalScreen.SPINE, height - JournalScreen.COVER_EDGE * 2);
        graphics.blitSprite(Stamp.PAGE_RIGHT, pageLeft, pagesTop, pageWidth, height - JournalScreen.COVER_EDGE * 2);
        Stamp.mark(graphics, spine + (JournalScreen.SPINE - Stamp.MARK_WIDTH) / 2, top + (JournalScreen.COVER_EDGE - Stamp.MARK_HEIGHT) / 2, false);
        int x = pageLeft + JournalScreen.PAD;
        int y = pagesTop + JournalScreen.PAD;
        if (page.title().isEmpty()) {
            Stamp.mark(graphics, pageLeft + (pageWidth - Stamp.MARK_WIDTH) / 2, y, true);
            y += head;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, pageLeft + (pageWidth - font.width(line)) / 2, y, Stamp.INK, false);
                y += JournalScreen.LINE;
            }
        } else {
            Stamp.title(graphics, font, font.substrByWidth(Stamp.display(page.title()), textWidth).getString(), x, y, Stamp.INK_RED, false);
            Stamp.divider(graphics, x, y + Stamp.DISPLAY_HEIGHT + JournalScreen.TITLE_GAP, textWidth, true);
            y += head;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, x, y, Stamp.INK, false);
                y += JournalScreen.LINE;
            }
        }
        String said = status.getString();
        Stamp.label(graphics, font, said, (screen.width - Stamp.labelWidth(font, said)) / 2, top + height + STATUS_GAP, Stamp.LABEL, true);
        for (Renderable renderable : screen.renderables) {
            if (renderable instanceof AbstractButton button) {
                button.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
            }
        }
    }

    @Nullable
    private static Component status(@Nullable Screen screen) {
        return switch (screen) {
            case ConnectScreen connect -> ((ConnectScreenAccessor) connect).wayfarer$status();
            case GenericMessageScreen message -> message.getTitle();
            case LevelLoadingScreen level -> Component.translatable("loading.progress",
                    Mth.clamp(((LevelLoadingScreenAccessor) level).wayfarer$progress().getProgress(), 0, PERCENT));
            case ReceivingLevelScreen receiving -> TERRAIN;
            case null, default -> null;
        };
    }

    private static JsonObject kept() {
        if (kept == null) {
            kept = new JsonObject();
            if (Files.exists(file())) {
                try {
                    kept = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                } catch (IOException | JsonParseException | IllegalStateException exception) {
                    LOGGER.error("Cannot read the story's last pages from {}", file(), exception);
                }
            }
        }
        return kept;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Wayfarer.MODID).resolve(FILE);
    }

    private record Page(String title, List<Component> told) {
        static Page of(JsonObject page) {
            List<Component> told = new ArrayList<>();
            page.getAsJsonArray(LINES).forEach(line -> told.add(Component.literal(line.getAsString())));
            return new Page(page.get(TITLE).getAsString(), told);
        }

        List<FormattedCharSequence> tail(Font font, int width, int room) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : told) {
                if (!lines.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                }
                lines.addAll(font.split(line, width));
            }
            List<FormattedCharSequence> shown = new ArrayList<>(lines.subList(Math.max(0, lines.size() - room), lines.size()));
            while (!shown.isEmpty() && shown.getFirst() == FormattedCharSequence.EMPTY) {
                shown.removeFirst();
            }
            return shown;
        }
    }
}
