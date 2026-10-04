package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
public final class EndingClient {
    private static final int BLACK = 0xFF000000;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int LINE_HEIGHT = 12;
    private static final Field SKIN_LOOKUP = skinLookup();
    private static final int WHITE = 0xFFFFFF;
    private static final Map<UUID, ResourceLocation> skins = new HashMap<>();
    private static List<String> ending;

    private EndingClient() {}

    private static Field skinLookup() {
        try {
            Field field = PlayerInfo.class.getDeclaredField("skinLookup");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    static void show(String lines) {
        ending = List.of(lines.split("\n", -1));
    }

    static void skin(String player, String texture) {
        skins.put(UUID.fromString(player), ResourceLocation.parse(texture));
        apply();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!skins.isEmpty()) {
            apply();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (ending != null && minecraft.player != null && !(minecraft.screen instanceof EndingScreen)) {
            minecraft.setScreen(new EndingScreen(ending));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ending = null;
    }

    private static void apply() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        skins.forEach((uuid, texture) -> {
            PlayerInfo info = connection.getPlayerInfo(uuid);
            if (info != null && !info.getSkin().texture().equals(texture)) {
                PlayerSkin pip = new PlayerSkin(texture, null, null, null, PlayerSkin.Model.WIDE, true);
                Supplier<PlayerSkin> lookup = () -> pip;
                try {
                    SKIN_LOOKUP.set(info, lookup);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static final class EndingScreen extends Screen {
        private final List<String> lines;

        private EndingScreen(List<String> lines) {
            super(Component.literal("The end"));
            this.lines = lines;
        }

        @Override
        protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Start a new road"), button -> {
                ending = null;
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    minecraft.level.disconnect();
                }
                minecraft.disconnect(new TitleScreen());
            }).bounds((width - BUTTON_WIDTH) / 2, height / 2 + lines.size() * LINE_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, BLACK);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int top = height / 2 - lines.size() * LINE_HEIGHT;
            for (int index = 0; index < lines.size(); index++) {
                graphics.drawCenteredString(font, lines.get(index), width / 2, top + index * LINE_HEIGHT, WHITE);
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
