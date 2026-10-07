package io.github.roman16.wayfarer.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.roman16.wayfarer.Wayfarer;
import io.github.roman16.wayfarer.story.Tasks;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.client.JourneymapClient;
import journeymap.client.properties.CoreProperties;
import journeymap.client.ui.UIManager;
import journeymap.common.Journeymap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

@JourneyMapPlugin(apiVersion = "2.0.0")
public final class MapLink implements IClientPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ICON = ResourceLocation.withDefaultNamespace("textures/item/filled_map.png");
    private static final int ICON_SIZE = 16;
    private static IClientAPI api;
    private static String placed = "";

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
    }

    @Override
    public String getModId() {
        return Wayfarer.MODID;
    }

    static void show(JsonArray places) {
        if (api == null || places.toString().equals(placed)) {
            return;
        }
        placed = places.toString();
        api.removeAll(Wayfarer.MODID);
        for (JsonElement element : places) {
            JsonObject place = element.getAsJsonObject();
            BlockPos at = new BlockPos(place.get("x").getAsInt(), 0, place.get("z").getAsInt());
            String name = Component.translatable(place.get("name").getAsString()).getString();
            MapImage icon = new MapImage(ICON, ICON_SIZE, ICON_SIZE).setAnchorX(ICON_SIZE / 2.0).setAnchorY(ICON_SIZE / 2.0);
            MarkerOverlay marker = new MarkerOverlay(Wayfarer.MODID, at, icon);
            marker.setDimension(Level.OVERWORLD).setTitle(name).setLabel(name);
            try {
                api.show(marker);
            } catch (Exception exception) {
                LOGGER.error("JourneyMap refused the place {}", place, exception);
            }
        }
    }

    static void open() {
        CoreProperties core = JourneymapClient.getInstance().getCoreProperties();
        String version = Journeymap.JM_VERSION.toString();
        if (!version.equals(core.splashViewed.get())) {
            core.splashViewed.set(version).save();
        }
        Minecraft.getInstance().setScreen(null);
        UIManager.INSTANCE.getOrOpenFullscreenMap();
        PacketDistributor.sendToServer(new Tasks.Did("map"));
    }
}
