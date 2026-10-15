package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Balance;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

public enum Graphics {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA,
    CUSTOM;

    private static final List<Graphics> STEPS = List.of(LOW, MEDIUM, HIGH, ULTRA);
    private static final String FAR_VIEW = "distanthorizons";
    private static final String OFF = "off";
    private static final String SHADERS = "iris";

    static Graphics current() {
        Balance.Graphics steps = Balance.get().graphics();
        int render = Minecraft.getInstance().options.renderDistance().get();
        int simulation = Minecraft.getInstance().options.simulationDistance().get();
        boolean farView = ModList.get().isLoaded(FAR_VIEW);
        Optional<Integer> far = farView ? Optional.of(FarViewSetting.chunks()) : Optional.empty();
        Optional<String> detail = farView ? Optional.of(FarViewSetting.detail()) : Optional.empty();
        Optional<String> profile = ModList.get().isLoaded(SHADERS) ? ShaderProfile.current() : Optional.empty();
        return STEPS.stream()
                .filter(step -> render == steps.renderChunks().get(step.ordinal()))
                .filter(step -> simulation == steps.simulationChunks().get(step.ordinal()))
                .filter(step -> far.map(steps.farViewChunks().get(step.ordinal())::equals).orElse(true))
                .filter(step -> detail.map(steps.farViewDetail().get(step.ordinal())::equals).orElse(true))
                .filter(step -> profile.map(steps.profiles().get(step.ordinal())::equals).orElse(true))
                .findFirst().orElse(CUSTOM);
    }

    public static void choose(Graphics step) {
        if (step == CUSTOM) {
            return;
        }
        Balance.Graphics steps = Balance.get().graphics();
        Minecraft.getInstance().options.renderDistance().set(steps.renderChunks().get(step.ordinal()));
        Minecraft.getInstance().options.simulationDistance().set(steps.simulationChunks().get(step.ordinal()));
        if (ModList.get().isLoaded(FAR_VIEW)) {
            FarViewSetting.set(steps.farViewChunks().get(step.ordinal()), steps.farViewDetail().get(step.ordinal()), steps.farViewThreads().get(step.ordinal()));
        }
        if (ModList.get().isLoaded(SHADERS)) {
            ShaderProfile.choose(steps.profiles().get(step.ordinal()));
        }
    }

    static String heldLight() {
        if (shaded()) {
            return OFF;
        }
        Graphics step = current();
        return Balance.get().graphics().heldLight().get((step == CUSTOM ? HIGH : step).ordinal());
    }

    public static boolean shaded() {
        return ModList.get().isLoaded(SHADERS) && ShaderProfile.drawing();
    }

    static boolean shaders() {
        return ShaderProfile.enabled();
    }

    public static void shaders(boolean on) {
        Graphics step = current();
        ShaderProfile.enable(on);
        if (on) {
            choose(step);
        }
    }
}
