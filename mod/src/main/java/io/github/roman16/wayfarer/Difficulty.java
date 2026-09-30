package io.github.roman16.wayfarer;

import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class Difficulty {
    private static final String IMPROVED_MOBS = "improvedmobs";

    private Difficulty() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            catchUp(player);
            if (Journey.slain(player)) {
                hold(player);
            }
        }
    }

    public static void hold(ServerPlayer player) {
        if (ModList.get().isLoaded(IMPROVED_MOBS)) {
            run(player, "improvedmobs difficulty pause player " + player.getGameProfile().getName());
        }
    }

    public static void catchUp(ServerPlayer player) {
        int eyes = Journey.claimed(player).size();
        int applied = player.getData(Wayfarer.EYE_STEPS);
        if (eyes <= applied) {
            return;
        }
        player.setData(Wayfarer.EYE_STEPS, eyes);
        if (!ModList.get().isLoaded(IMPROVED_MOBS)) {
            return;
        }
        float added = (eyes - applied) * Balance.INSTANCE.difficulty().perEye();
        run(player, String.format(Locale.ROOT, "improvedmobs difficulty player %s add %.2f", player.getGameProfile().getName(), added));
    }

    private static void run(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
