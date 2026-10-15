package io.github.roman16.wayfarer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import java.util.stream.Stream;
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
    private static final Field SKIN_LOOKUP = skinLookup();
    private static final Map<UUID, ResourceLocation> skins = new HashMap<>();
    @Nullable
    private static List<JsonObject> afterwards;

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

    public static void show(String pages) {
        afterwards = JsonParser.parseString(pages).getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject).toList();
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
        if (afterwards != null && minecraft.player != null && !(minecraft.screen instanceof Gone)) {
            minecraft.setScreen(new Gone(afterwards));
        }
    }

    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        afterwards = null;
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

    private static final class Gone extends Screen {
        private final List<List<Component>> leaves = new ArrayList<>();
        private List<FormattedCharSequence> wrapped = List.of();
        private int leaf;
        private long leafFrom;
        @Nullable
        private Button road;

        private Gone(List<JsonObject> pages) {
            super(Component.translatable("wayfarer.ending.title"));
            leaves.add(List.of(Component.translatable("wayfarer.ending.frame")));
            pages.forEach(page -> leaves.add(List.of(Stamp.display(Afterward.title(page).getString()), Component.empty(), Afterward.hall(page), Component.empty(),
                    Afterward.door(page))));
            leaves.add(List.of(Component.translatable("wayfarer.ending.deep")));
        }

        @Override
        protected void init() {
            if (leafFrom == 0) {
                leafFrom = Util.getMillis();
            }
            road = addRenderableWidget(Button.builder(Component.translatable("wayfarer.ending.new_road"), button -> {
                afterwards = null;
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    minecraft.level.disconnect();
                }
                minecraft.disconnect(new TitleScreen());
            }).bounds((width - BUTTON_WIDTH) / 2, height - MARGIN - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            wrap();
        }

        private void wrap() {
            wrapped = leaves.get(leaf).stream()
                    .flatMap(line -> line.getString().isEmpty() ? Stream.of(FormattedCharSequence.EMPTY) : font.split(line, width - MARGIN * 2).stream())
                    .toList();
            if (road != null) {
                road.visible = last();
            }
        }

        private boolean last() {
            return leaf == leaves.size() - 1;
        }

        private long length() {
            int characters = leaves.get(leaf).stream().mapToInt(line -> line.getString().length()).sum();
            return Math.max(Stamp.LINGER_MILLIS, (long) characters * Stamp.READ_MILLIS);
        }

        private void turn() {
            leaf++;
            leafFrom = Util.getMillis();
            wrap();
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
                FormattedCharSequence line = wrapped.get(index);
                int color = Stamp.withAlpha(index == 0 && leaf > 0 && !last() ? Stamp.GOLD : Stamp.BONE, alpha);
                graphics.drawString(font, line, (width - font.width(line)) / 2, top + index * LINE_HEIGHT, color, false);
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
