package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.NightPayload;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Tooltips;
import io.github.roman16.wayfarer.story.Sense;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.ChestedHorseModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.client.ICuriosScreen;

@Mod(value = Wayfarer.MODID, dist = Dist.CLIENT)
public final class WayfarerClient {
    public static final KeyMapping JOURNAL = new KeyMapping("key.wayfarer.journal", GLFW.GLFW_KEY_J, "key.categories.wayfarer");
    private static final KeyMapping MAP = new KeyMapping("key.wayfarer.map", GLFW.GLFW_KEY_M, "key.categories.wayfarer");
    private static final KeyMapping SENSE = new KeyMapping(Sense.KEY, GLFW.GLFW_KEY_Z, "key.categories.wayfarer");
    private static final String JOURNEYMAP = "journeymap";
    private static final String TITLE = "title";

    public WayfarerClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, TalkSize.SPEC);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(JOURNAL);
            event.register(MAP);
            event.register(SENSE);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> keys(Minecraft.getInstance()));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY, Wayfarer.id("hud"), Hud::render);
            event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY, Wayfarer.id("talk"), Conversation::render);
            event.registerAboveAll(Wayfarer.id("flash"), Flash::render);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> Conversation.tick(Minecraft.getInstance()));
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                event -> event.registerLayerDefinition(FallenRenderer.MULE, ChestedHorseModel::createBodyLayer));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(Wayfarer.SHRINE_PEDESTAL_ENTITY.get(), ShrinePedestalRenderer::new);
            event.registerEntityRenderer(Wayfarer.FALLEN.get(), FallenRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Sight.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, event -> {
            if (event.getNewScreen() instanceof TitleScreen) {
                MusicPlayer.play(new Music.Play(TITLE, true));
            }
            if (event.getNewScreen() instanceof ICuriosScreen) {
                PacketDistributor.sendToServer(new Tasks.Did("charms"));
            }
        });
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, FrontDoor::init);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Pre.class, Recap::render);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Recap.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Color.class, Tooltips::color);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, Hud::beforeLayer);
        NeoForge.EVENT_BUS.addListener(CustomizeGuiOverlayEvent.BossEventProgress.class, Hud::bossBar);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR) || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> MusicPlayer.play(new Music.Play("", false)));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HudState.compass = CompassPayload.hidden(List.of());
            HudState.gaze = GazePayload.CLOSED;
            HudState.night = NightPayload.CALM;
            HudState.panel = Tasks.Panel.EMPTY;
            Conversation.forget();
            Hud.clearBanners();
            JournalScreen.forget();
        });
    }

    private static void keys(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        boolean journeymap = ModList.get().isLoaded(JOURNEYMAP);
        while (JOURNAL.consumeClick()) {
            JournalScreen.open();
        }
        while (MAP.consumeClick()) {
            if (journeymap) {
                MapLink.open();
            }
        }
        while (SENSE.consumeClick()) {
            PacketDistributor.sendToServer(new Sense.Payload());
        }
        if (journeymap) {
            MapLink.show(JournalScreen.places());
        }
    }
}
