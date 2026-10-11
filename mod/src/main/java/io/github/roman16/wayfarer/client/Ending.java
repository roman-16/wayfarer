package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.client.stamp.Stamp;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
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
    private static List<String> lines;

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

    public static void show(String text) {
        lines = List.of(text.split("\n", -1));
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
        if (lines != null && minecraft.player != null && !(minecraft.screen instanceof Gone)) {
            minecraft.setScreen(new Gone(lines));
        }
    }

    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        lines = null;
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
        private final List<String> text;
        private List<FormattedCharSequence> wrapped = List.of();

        private Gone(List<String> text) {
            super(Component.translatable("wayfarer.ending.title"));
            this.text = text;
        }

        @Override
        protected void init() {
            wrapped = text.stream()
                    .flatMap(line -> line.isEmpty() ? Stream.of(FormattedCharSequence.EMPTY) : font.split(Component.literal(line), width - MARGIN * 2).stream())
                    .toList();
            addRenderableWidget(Button.builder(Component.translatable("wayfarer.ending.new_road"), button -> {
                lines = null;
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    minecraft.level.disconnect();
                }
                minecraft.disconnect(new TitleScreen());
            }).bounds((width - BUTTON_WIDTH) / 2, height / 2 + wrapped.size() * LINE_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, Stamp.INK);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int top = height / 2 - wrapped.size() * LINE_HEIGHT;
            for (int index = 0; index < wrapped.size(); index++) {
                graphics.drawCenteredString(font, wrapped.get(index), width / 2, top + index * LINE_HEIGHT, Stamp.BONE);
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
