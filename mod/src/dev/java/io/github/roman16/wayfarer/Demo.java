package io.github.roman16.wayfarer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Demo {
    private static final ResourceKey<Level> SHOWROOM = ResourceKey.create(Registries.DIMENSION, Wayfarer.id("showroom"));
    private static final int TICKS_PER_SECOND = 20;
    private static final JsonObject SPEC = spec();
    private static final Compass.Aim TARGET = target(SPEC.getAsJsonObject("target"));
    private static final JsonObject COMPANION = SPEC.getAsJsonObject("companion");
    private static final JsonArray GRAVE = SPEC.getAsJsonArray("grave");

    private Demo() {}

    @SubscribeEvent
    public static void onCompass(CompassEvent event) {
        ServerPlayer player = event.player();
        if (player.level().dimension() != SHOWROOM) {
            return;
        }
        event.show();
        event.wake();
        event.aim(TARGET);
        JsonArray centre = COMPANION.getAsJsonArray("centre");
        double radius = COMPANION.get("radius").getAsDouble();
        double turn = (double) player.level().getGameTime() / (COMPANION.get("seconds").getAsInt() * TICKS_PER_SECOND);
        int x = (int) Math.round(centre.get(0).getAsInt() + radius * Math.cos(turn * 2 * Math.PI));
        int z = (int) Math.round(centre.get(1).getAsInt() + radius * Math.sin(turn * 2 * Math.PI));
        event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.TEAMMATE, COMPANION.get("name").getAsString(), x, z));
        event.markers().add(new CompassPayload.Marker(CompassPayload.Kind.GRAVE, "", GRAVE.get(0).getAsInt(), GRAVE.get(1).getAsInt()));
    }

    private static Compass.Aim target(JsonObject target) {
        JsonArray at = target.getAsJsonArray("at");
        return new Compass.Aim(new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()));
    }

    private static JsonObject spec() {
        try (InputStream stream = Demo.class.getResourceAsStream("/wayfarer/showroom.json")) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("demo");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
