package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.story.Tasks;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class BugReport extends Screen {
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final String FORM = "/new?template=bug.yml";
    private static final String ISSUES = "issueTrackerURL";
    private static final String LOGS = "logs";
    private static final int MARGIN = 50;
    private static final String NEOFORGE = "neoforge";
    private static final int SPACING = 12;
    private final Screen parent;

    private BugReport(Screen parent) {
        super(Component.translatable("wayfarer.bug"));
        this.parent = parent;
    }

    static Button button(Screen parent, int x, int y, int width, int height) {
        return Button.builder(Component.translatable("wayfarer.bug"), pressed -> Minecraft.getInstance().setScreen(new BugReport(parent)))
                .bounds(x, y, width, height).build();
    }

    @Override
    protected void init() {
        LinearLayout layout = LinearLayout.vertical().spacing(SPACING);
        layout.defaultCellSetting().alignHorizontallyCenter();
        layout.addChild(new StringWidget(title, font).setColor(Stamp.BONE));
        layout.addChild(new MultiLineTextWidget(Component.translatable("wayfarer.bug.message"), font).setMaxWidth(width - MARGIN).setCentered(true)
                .setColor(Stamp.BONE));
        LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(BUTTON_GAP));
        buttons.addChild(Button.builder(Component.translatable("wayfarer.bug.form"), pressed -> Util.getPlatform().openUri(form()))
                .size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
        buttons.addChild(Button.builder(Component.translatable("wayfarer.bug.logs"),
                pressed -> Util.getPlatform().openPath(minecraft.gameDirectory.toPath().resolve(LOGS))).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
        buttons.addChild(Button.builder(CommonComponents.GUI_BACK, pressed -> onClose()).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, getRectangle());
        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private URI form() {
        String issues = ModList.get().getModFileById(Wayfarer.MODID).getConfig().<String>getConfigElement(ISSUES).orElseThrow();
        String fields = facts().entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return URI.create(issues + FORM + "&" + fields);
    }

    private Map<String, String> facts() {
        Map<String, String> facts = new TreeMap<>();
        facts.put("version", "Wayfarer " + version(Wayfarer.MODID) + ", Minecraft " + SharedConstants.getCurrentVersion().getName() + ", NeoForge "
                + version(NEOFORGE));
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return facts;
        }
        facts.put("game", minecraft.getSingleplayerServer() == null ? "Server"
                : minecraft.getSingleplayerServer().isPublished() ? "Singleplayer open to LAN" : "Singleplayer");
        BlockPos at = player.blockPosition();
        facts.put("place", player.level().dimension().location() + " at " + at.getX() + ", " + at.getY() + ", " + at.getZ());
        Tasks.Panel panel = HudState.panel;
        String quest = panel.title().getString();
        String objective = panel.objective().getString();
        if (!quest.isEmpty()) {
            facts.put("quest", objective.isEmpty() ? quest : quest + " - " + objective);
        }
        return facts;
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).orElseThrow().getModInfo().getVersion().toString();
    }
}
