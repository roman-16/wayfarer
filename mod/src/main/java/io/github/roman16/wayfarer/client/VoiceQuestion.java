package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.client.stamp.Stamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class VoiceQuestion extends Screen {
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final int MARGIN = 50;
    private static final int SPACING = 12;
    private final Screen parent;
    private final Runnable then;

    private VoiceQuestion(Screen parent, Runnable then) {
        super(Component.translatable("wayfarer.options.voice"));
        this.parent = parent;
        this.then = then;
    }

    static void first(Screen parent, Runnable then) {
        if (YourVoice.answered()) {
            then.run();
        } else {
            Minecraft.getInstance().setScreen(new VoiceQuestion(parent, then));
        }
    }

    @Override
    protected void init() {
        LinearLayout layout = LinearLayout.vertical().spacing(SPACING);
        layout.defaultCellSetting().alignHorizontallyCenter();
        layout.addChild(new StringWidget(title, font).setColor(Stamp.BONE));
        layout.addChild(new MultiLineTextWidget(Component.translatable("wayfarer.options.voice.ask"), font).setMaxWidth(width - MARGIN).setCentered(true)
                .setColor(Stamp.BONE));
        LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(BUTTON_GAP));
        for (YourVoice voice : YourVoice.values()) {
            buttons.addChild(Button.builder(voice.label(), pressed -> {
                YourVoice.choose(voice);
                then.run();
            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, getRectangle());
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
