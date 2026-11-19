package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.CompassPayload;
import io.github.roman16.wayfarer.GazePayload;
import io.github.roman16.wayfarer.HudState;
import io.github.roman16.wayfarer.LastUse;
import io.github.roman16.wayfarer.Music;
import io.github.roman16.wayfarer.NightPayload;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.client.stamp.Stamp;
import io.github.roman16.wayfarer.client.stamp.Tooltips;
import io.github.roman16.wayfarer.story.Sense;
import io.github.roman16.wayfarer.story.Tasks;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ChestedHorseModel;
import net.minecraft.client.model.GoatModel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICuriosScreen;

@Mod(value = Wayfarer.MODID, dist = Dist.CLIENT)
public final class WayfarerClient {
    public static final KeyMapping JOURNAL = new KeyMapping("key.wayfarer.journal", GLFW.GLFW_KEY_J, "key.categories.wayfarer");
    private static final KeyMapping MAP = new KeyMapping("key.wayfarer.map", GLFW.GLFW_KEY_M, "key.categories.wayfarer");
    private static final KeyMapping SENSE = new KeyMapping(Sense.KEY, GLFW.GLFW_KEY_Z, "key.categories.wayfarer");
    static final KeyMapping AGAIN = new KeyMapping("key.wayfarer.again", GLFW.GLFW_KEY_0, "key.categories.wayfarer");
    private static final String INVMOVE = "invmove";
    private static final String JEI = "jei";
    private static final String JOURNEYMAP = "journeymap";
    private static final String TITLE = "title";

    public WayfarerClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if (ModList.get().isLoaded(INVMOVE) && ModList.get().isLoaded(JEI)) {
            InvMoveLink.register();
        }
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(JOURNAL);
            event.register(MAP);
            event.register(SENSE);
            event.register(AGAIN);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> keys(Minecraft.getInstance()));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> {
            event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY, Wayfarer.id("hud"), Hud::render);
            event.registerBelow(VanillaGuiLayers.SUBTITLE_OVERLAY, Wayfarer.id("talk"), Conversation::render);
            event.registerAboveAll(Wayfarer.id("flash"), Flash::render);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> Conversation.tick(Minecraft.getInstance()));
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, event -> {
            event.registerLayerDefinition(FallenRenderer.GOAT, GoatModel::createBodyLayer);
            event.registerLayerDefinition(FallenRenderer.MULE, ChestedHorseModel::createBodyLayer);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(Wayfarer.CALLING_STONE_ENTITY.get(), CallingStoneRenderer::new);
            event.registerEntityRenderer(Wayfarer.DEEP.get(), DeepRenderer::new);
            event.registerEntityRenderer(Wayfarer.FALLEN.get(), FallenRenderer::new);
            if (ModList.get().isLoaded("geckolib")) {
                WormLook.register(event);
            }
        });
        modBus.addListener(EntityRenderersEvent.AddLayers.class, Eyes::register);
        modBus.addListener(RegisterItemDecorationsEvent.class, Worn::register);
        modBus.addListener(FMLClientSetupEvent.class, event -> {
            CuriosRendererRegistry.register(Items.LANTERN, BeltLanternRenderer::new);
            CuriosRendererRegistry.register(Items.SOUL_LANTERN, BeltLanternRenderer::new);
            SoundClock.start();
        });
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> {
            event.registerReloadListener((ResourceManagerReloadListener) DeepRenderer::reload);
            event.registerReloadListener((ResourceManagerReloadListener) Eyes::reload);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Eyes.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> HeldLight.tick());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Smoke.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, Frames::frame);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, TentOutlines::render);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Frames::report);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, FarSight::zoom);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.RenderFog.class, LastSeaFog::render);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, FarSight::mark);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Ending::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Speech.tick(Minecraft.getInstance());
            SoundClock.ticked();
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Reading.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, Reading::init);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, AutoSort::loggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, YourVoice::loggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            if (event.getPlayer() != null) {
                Speech.stop();
                Reading.leave();
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, Ending::leave);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> OwnInventory.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, event -> HeldLight.follow());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Sight.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, Screens::opening);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, Arrival::loggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.Clone.class, Arrival::cloned);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Arrival::tick);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, event -> {
            if (event.getNewScreen() instanceof ICuriosScreen) {
                PacketDistributor.sendToServer(new Tasks.Did("charms"));
            }
        });
        NeoForge.EVENT_BUS.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
            if (MAP.matches(event.getKeyCode(), event.getScanCode()) && ModList.get().isLoaded(JOURNEYMAP) && MapLink.shown(event.getScreen())) {
                event.getScreen().onClose();
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, FrontDoor::init);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, RecipeBooks::hide);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Pre.class, RecipeBooks::close);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Pre.class, Recap::render);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Pre.class, WakingScreen::behind);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> WakingScreen.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, CraftingHint::render);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> Recap.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Color.class, Tooltips::color);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, event -> {
            if (event.getItemStack().is(Items.ENCHANTED_BOOK)) {
                event.getToolTip().add(Component.translatable("item.wayfarer.enchanted_book.smith").withColor(Stamp.QUIET));
            }
            if (LastUse.lastUse(event.getItemStack())) {
                event.getToolTip().add(1, Component.translatable("wayfarer.gear.bare").withColor(Stamp.QUIET));
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ItemTooltipEvent.class, Tooltips::maker);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, Hud::beforeLayer);
        NeoForge.EVENT_BUS.addListener(CustomizeGuiOverlayEvent.BossEventProgress.class, Hud::bossBar);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR) || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && minecraft.screen == null && MusicPlayer.playing(TITLE)) {
                MusicPlayer.play(new Music.Play("", false));
            } else if (minecraft.level == null && minecraft.screen != null && !MusicPlayer.playing(TITLE) && MusicPlayer.audible()) {
                MusicPlayer.play(new Music.Play(TITLE, true));
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            HudState.compass = CompassPayload.hidden(List.of());
            HudState.gaze = GazePayload.CLOSED;
            HudState.lastSea = 0;
            HudState.night = NightPayload.CALM;
            HudState.panel = Tasks.Panel.EMPTY;
            HudState.smoke = Optional.empty();
            Conversation.forget();
            Hud.clearBanners();
            JournalScreen.forget();
            OwnInventory.forget();
            WakingScreen.leave();
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
                MapLink.open(false);
            }
        }
        while (SENSE.consumeClick()) {
            Hud.sensed();
            PacketDistributor.sendToServer(new Sense.Payload());
        }
        if (journeymap) {
            MapLink.show(JournalScreen.places());
            MapLink.tick(minecraft);
        }
    }
}
