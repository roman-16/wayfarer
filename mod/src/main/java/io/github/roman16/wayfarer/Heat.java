package io.github.roman16.wayfarer;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;

public final class Heat {
    private Heat() {}

    public static Optional<String> feels(ServerPlayer player) {
        return switch (TemperatureHelper.getTemperatureForPlayer(player)) {
            case ICY, COLD -> Optional.of("cold");
            case WARM, HOT -> Optional.of("hot");
            default -> Optional.empty();
        };
    }

    public static Optional<String> task(ServerPlayer player) {
        TemperatureLevel level = TemperatureHelper.getTemperatureForPlayer(player);
        return switch (level) {
            case HOT -> Optional.of("heat");
            case ICY -> Optional.of("cold");
            default -> Optional.empty();
        };
    }
}
