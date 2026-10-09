package io.github.roman16.wayfarer.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.Profile;
import net.irisshaders.iris.shaderpack.option.ProfileSet;

final class ShaderProfile {
    private ShaderProfile() {}

    static boolean enabled() {
        return IrisApi.getInstance().getConfig().areShadersEnabled();
    }

    static void enable(boolean on) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(on);
    }

    static Optional<String> current() {
        return loaded().map(pack -> scan(pack).current.map(profile -> profile.name).orElse(""));
    }

    static void choose(String name) {
        loaded().filter(pack -> !scan(pack).current.map(profile -> profile.name.equals(name)).orElse(false)).ifPresent(pack -> {
            Map<String, Profile> profiles = new HashMap<>();
            pack.getMenuContainer().getProfiles().forEach(profiles::put);
            Optional.ofNullable(profiles.get(name)).ifPresent(profile -> {
                Iris.queueShaderPackOptionsFromProfile(profile);
                enable(true);
            });
        });
    }

    private static Optional<ShaderPack> loaded() {
        return enabled() ? Iris.getCurrentPack() : Optional.empty();
    }

    private static ProfileSet.ProfileResult scan(ShaderPack pack) {
        return pack.getMenuContainer().getProfiles().scan(pack.getShaderPackOptions().getOptionSet(), pack.getShaderPackOptions().getOptionValues());
    }
}
