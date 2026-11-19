package io.github.roman16.wayfarer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.Narration;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class Ending {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int LINE_HEIGHT = 12;
    private static final int MARGIN = 32;
    private static final String PIECE = "ending";
    private static final String QUOTE = "\"";
    private static final int MILLIS_PER_SECOND = 1000;
    private static final Field SKIN_LOOKUP = skinLookup();
    private static final Bundled<JsonObject> CREDITS = Bundled.json("/wayfarer/credits.json");
    private static final Map<UUID, ResourceLocation> skins = new HashMap<>();
    @Nullable
    private static Supplier<Screen> gone;

    private Ending() {}

    private static Field skinLookup() {
        try {
            Field field = PlayerInfo.class.getDeclaredField("skinLookup");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private record Leaf(@Nullable Component title, List<Component> body, List<Narration.Clip> voices) {
        Leaf(@Nullable Component title, List<Component> body) {
            this(title, body, body.stream().flatMap(line -> Narration.said(null, line.getString(), false).stream()).toList());
        }
    }

    private record Line(FormattedCharSequence text, int color) {}

    public static void show(String afterwards, String epilogue) {
        List<JsonObject> pages = objects(afterwards);
        List<Leaf> told = told(objects(epilogue));
        gone = () -> new Leaves(Component.translatable("wayfarer.ending.title"), gone(pages, told), 1 + pages.size(),
                Component.translatable("wayfarer.ending.new_road"), Ending::newRoad);
    }

    public static void skin(String player, String texture) {
        skins.put(UUID.fromString(player), ResourceLocation.parse(texture));
        wear();
    }

    static void tick(ClientTickEvent.Post event) {
        if (!skins.isEmpty()) {
            wear();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (gone != null && minecraft.player != null && !(minecraft.screen instanceof Leaves)) {
            minecraft.setScreen(gone.get());
        }
    }

    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        gone = null;
        skins.clear();
    }

    private static void wear() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        skins.forEach((uuid, texture) -> {
            PlayerInfo info = connection.getPlayerInfo(uuid);
            if (info != null && !info.getSkin().texture().equals(texture)) {
                PlayerSkin grown = new PlayerSkin(texture, null, null, null, PlayerSkin.Model.WIDE, true);
                Supplier<PlayerSkin> lookup = () -> grown;
                try {
                    SKIN_LOOKUP.set(info, lookup);
                } catch (IllegalAccessException denied) {
                    throw new IllegalStateException(denied);
                }
            }
        });
    }

    public static void credits(String epilogue) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Leaf> leaves = new ArrayList<>(told(objects(epilogue)));
        leaves.addAll(credits());
        minecraft.setScreen(new Leaves(Component.translatable("wayfarer.credits.title"), leaves, 0, Component.translatable("wayfarer.credits.walk"),
                () -> minecraft.setScreen(null)));
    }

    private static List<Leaf> credits() {
        JsonObject credits = CREDITS.get();
        return List.of(new Leaf(Stamp.display(Component.translatable("wayfarer.credits.title").getString()), List.of()),
                new Leaf(null, List.of(Component.translatable("wayfarer.credits.made"))),
                new Leaf(Component.translatable("wayfarer.credits.music"), credits.getAsJsonArray("music").asList().stream()
                        .map(piece -> (Component) Component.literal(piece.getAsString())).toList()),
                new Leaf(null, List.of(Component.translatable("wayfarer.credits.with", credits.get("mods").getAsInt()))),
                new Leaf(null, List.of(Component.translatable("wayfarer.credits.last"))));
    }

    private static List<Leaf> told(List<JsonObject> epilogue) {
        return epilogue.stream().map(leaf -> {
            String text = leaf.get("text").getAsString();
            List<Narration.Clip> voices = Narration.said(leaf.get("voice").getAsString(), text, false).stream().toList();
            return leaf.has("by") ? new Leaf(Component.literal(leaf.get("by").getAsString()), List.of(Component.literal(QUOTE + text + QUOTE)), voices)
                    : new Leaf(null, List.of(Component.literal(text)), voices);
        }).toList();
    }

    private static List<Leaf> gone(List<JsonObject> pages, List<Leaf> told) {
        List<Leaf> leaves = new ArrayList<>();
        leaves.add(new Leaf(null, List.of(Component.translatable("wayfarer.ending.frame"))));
        pages.forEach(page -> leaves.add(new Leaf(Stamp.display(Afterward.title(page).getString()),
                List.of(Afterward.hall(page), Component.empty(), Afterward.door(page)))));
        leaves.addAll(told);
        leaves.add(new Leaf(null, List.of(Component.translatable("wayfarer.ending.deep"))));
        leaves.addAll(credits());
        return leaves;
    }

    private static List<JsonObject> objects(String json) {
        return JsonParser.parseString(json).getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject).toList();
    }

    private static void newRoad() {
        gone = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.disconnect(new TitleScreen());
    }

    private static final class Leaves extends Screen {
        private final List<Leaf> leaves;
        private final int music;
        private final Component label;
        private final Runnable done;
        private List<Line> wrapped = List.of();
        private int leaf;
        private long leafFrom;
        @Nullable
        private Button road;

        private Leaves(Component title, List<Leaf> leaves, int music, Component label, Runnable done) {
            super(title);
            this.leaves = List.copyOf(leaves);
            this.music = music;
            this.label = label;
            this.done = done;
        }

        @Override
        protected void init() {
            if (leafFrom == 0) {
                leafFrom = Util.getMillis();
                score();
                speak();
            }
            road = addRenderableWidget(Button.builder(label, button -> {
                MusicPlayer.stop();
                done.run();
            }).bounds((width - BUTTON_WIDTH) / 2, height - MARGIN - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            wrap();
        }

        private void score() {
            if (leaf == music) {
                MusicPlayer.play(new Music.Play(PIECE, false));
            }
        }

        private void wrap() {
            Leaf shown = leaves.get(leaf);
            int room = width - MARGIN * 2;
            List<Line> lines = new ArrayList<>();
            if (shown.title() != null) {
                font.split(shown.title(), room).forEach(line -> lines.add(new Line(line, Stamp.GOLD)));
                if (!shown.body().isEmpty()) {
                    lines.add(new Line(FormattedCharSequence.EMPTY, Stamp.BONE));
                }
            }
            for (Component body : shown.body()) {
                if (body.getString().isEmpty()) {
                    lines.add(new Line(FormattedCharSequence.EMPTY, Stamp.BONE));
                } else {
                    font.split(body, room).forEach(line -> lines.add(new Line(line, Stamp.BONE)));
                }
            }
            wrapped = lines;
            if (road != null) {
                road.visible = last();
            }
        }

        private boolean last() {
            return leaf == leaves.size() - 1;
        }

        private long length() {
            Leaf shown = leaves.get(leaf);
            int characters = (shown.title() == null ? 0 : shown.title().getString().length())
                    + shown.body().stream().mapToInt(line -> line.getString().length()).sum();
            long voiced = (long) (shown.voices().stream().mapToDouble(Narration.Clip::seconds).sum() * MILLIS_PER_SECOND) + Stamp.VOICE_FADE_MILLIS;
            return Math.max(shown.voices().isEmpty() ? Stamp.LINGER_MILLIS : voiced, (long) characters * Stamp.READ_MILLIS);
        }

        private void turn() {
            leaf++;
            leafFrom = Util.getMillis();
            score();
            speak();
            wrap();
        }

        @Override
        public void removed() {
            Speech.stopPages();
        }

        private void speak() {
            List<Narration.Clip> voices = leaves.get(leaf).voices();
            if (voices.isEmpty()) {
                Speech.stopPages();
            } else {
                Speech.pages(voices.stream().map(Narration.Clip::path).toList());
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!last()) {
                turn();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!last()) {
                turn();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void tick() {
            if (!last() && Util.getMillis() - leafFrom > length()) {
                turn();
            }
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Stamp.INK);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            long shown = Util.getMillis() - leafFrom;
            float kindled = Math.min(1, shown / (float) Stamp.KINDLE_MILLIS);
            float burning = last() ? 1 : Math.min(1, (length() - shown) / (float) Stamp.BURN_OUT_MILLIS);
            int alpha = Math.max(Stamp.FAINTEST_TEXT, Math.round(Math.max(0, Math.min(kindled, burning)) * 255));
            int top = (height - wrapped.size() * LINE_HEIGHT) / 2;
            for (int index = 0; index < wrapped.size(); index++) {
                Line line = wrapped.get(index);
                graphics.drawString(font, line.text(), (width - font.width(line.text())) / 2, top + index * LINE_HEIGHT, Stamp.withAlpha(line.color(), alpha), false);
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
