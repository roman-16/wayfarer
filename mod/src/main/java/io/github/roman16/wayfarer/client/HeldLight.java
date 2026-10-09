package io.github.roman16.wayfarer.client;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

final class HeldLight {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD = "lambdynlights";
    private static String mode = "";
    private static boolean shaders;

    private HeldLight() {}

    static void tick() {
        if (ModList.get().isLoaded(MOD) && (mode.isEmpty() || Graphics.shaded() != shaders)) {
            follow();
        }
    }

    static void follow() {
        if (!ModList.get().isLoaded(MOD)) {
            return;
        }
        shaders = Graphics.shaded();
        String wanted = Graphics.heldLight();
        if (!wanted.equals(mode)) {
            set(wanted);
            mode = wanted;
        }
    }

    private static void set(String wanted) {
        try {
            Class<?> lights = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
            Class<?> modes = Class.forName("dev.lambdaurora.lambdynlights.DynamicLightsMode");
            Object config = lights.getField("config").get(lights.getMethod("get").invoke(null));
            Object value = ((Optional<?>) modes.getMethod("byId", String.class).invoke(null, wanted)).orElseThrow();
            config.getClass().getMethod("setDynamicLightsMode", modes).invoke(config, value);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error("held light failed: LambDynamicLights refused the mode {}", wanted, exception);
        }
    }
}
