package io.github.roman16.wayfarer.spike;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class JournalScreen extends Screen {
    private static final KeyMapping OPEN = new KeyMapping("key.wayfarer.spike_journal", GLFW.GLFW_KEY_F7, "key.categories.misc");
    private static final int MARGIN = 16;
    private static final int LINE = 12;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TITLE = 0xFFD27A;
    private static final int TEXT = 0xFFFFFF;
    private static final int QUIET = 0xA0A0A0;
    private String shown = "";

    private JournalScreen() {
        super(Component.literal("Journal"));
    }

    @Override
    protected void init() {
        shown = JournalState.view();
        JsonObject view = JsonParser.parseString(shown).getAsJsonObject();
        int y = height - MARGIN - BUTTON_HEIGHT;
        if (view.has("rewards")) {
            for (JsonElement element : view.getAsJsonArray("rewards")) {
                JsonObject reward = element.getAsJsonObject();
                if (!reward.get("claimed").getAsBoolean()) {
                    String id = reward.get("id").getAsString();
                    addRenderableWidget(Button.builder(Component.literal("Claim ").append(item(reward)), button -> PacketDistributor.sendToServer(new Claim(id)))
                            .bounds(MARGIN, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                            .build());
                    y -= BUTTON_HEIGHT + MARGIN / 4;
                }
            }
        }
    }

    @Override
    public void tick() {
        if (!JournalState.view().equals(shown)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        JsonObject view = JsonParser.parseString(shown).getAsJsonObject();
        int y = MARGIN;
        graphics.drawString(font, "Quests", MARGIN, y, TITLE);
        y += LINE;
        JsonObject quest = view.has("quest") ? view.getAsJsonObject("quest") : null;
        if (quest == null || !quest.get("started").getAsBoolean()) {
            graphics.drawString(font, "Nothing yet.", MARGIN, y, QUIET);
            y += LINE;
        } else {
            String state = quest.get("failed").getAsBoolean() ? " (failed, try again)" : quest.get("done").getAsBoolean() ? " (done)" : "";
            graphics.drawString(font, quest.get("title").getAsString() + state, MARGIN, y, TEXT);
            y += LINE;
            graphics.drawString(font, quest.get("step").getAsString(), MARGIN * 2, y, QUIET);
            y += LINE;
        }
        y += LINE;
        graphics.drawString(font, "Codex: The Outpost", MARGIN, y, TITLE);
        y += LINE;
        if (view.has("codex")) {
            for (JsonElement line : view.getAsJsonArray("codex")) {
                graphics.drawString(font, line.getAsString(), MARGIN * 2, y, TEXT);
                y += LINE;
            }
        }
        y += LINE;
        graphics.drawString(font, "Rewards", MARGIN, y, TITLE);
        y += LINE;
        if (view.has("rewards")) {
            for (JsonElement element : view.getAsJsonArray("rewards")) {
                JsonObject reward = element.getAsJsonObject();
                Component state = Component.literal(reward.get("claimed").getAsBoolean() ? " (claimed)" : " (waiting)");
                graphics.drawString(font, item(reward).copy().append(state), MARGIN * 2, y, TEXT);
                y += LINE;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component item(JsonObject reward) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.get("item").getAsString())).getDescription();
    }

    @EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            while (OPEN.consumeClick()) {
                Minecraft.getInstance().setScreen(new JournalScreen());
            }
        }
    }
}
