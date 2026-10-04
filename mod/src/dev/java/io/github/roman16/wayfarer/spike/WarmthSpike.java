package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import toughasnails.api.temperature.ITemperature;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;

public final class WarmthSpike {
    private static final int GAMEMASTER = 2;
    private static final String TOUGH_AS_NAILS = "toughasnails";
    private static final Set<UUID> warmed = new HashSet<>();

    private WarmthSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithToughAsNails {
        private WithToughAsNails() {}

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            if (ModList.get().isLoaded(TOUGH_AS_NAILS)) {
                TemperatureHelper.registerPlayerTemperatureModifier(WarmthSpike::warm);
                NeoForge.EVENT_BUS.register(WarmthSpike.class);
            }
        }
    }

    private static TemperatureLevel warm(Player player, TemperatureLevel level) {
        return warmed.contains(player.getUUID()) && level.ordinal() < TemperatureLevel.NEUTRAL.ordinal() ? TemperatureLevel.NEUTRAL : level;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("warmth")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("gift").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> gift(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("feel").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> feel(context.getSource(), EntityArgument.getPlayer(context, "player")))))));
    }

    private static int gift(CommandSourceStack source, ServerPlayer player) {
        boolean on = warmed.add(player.getUUID());
        if (!on) {
            warmed.remove(player.getUUID());
        }
        say(source, "warmth: %s %s the Warmth gift".formatted(player.getScoreboardName(), on ? "has" : "no longer has"));
        return 1;
    }

    private static int feel(CommandSourceStack source, ServerPlayer player) {
        ITemperature temperature = TemperatureHelper.getTemperatureData(player);
        say(source, "warmth: %s is %s, heading for %s; the land there is %s".formatted(player.getScoreboardName(), name(temperature.getLevel()),
                name(temperature.getTargetLevel()), name(TemperatureHelper.getTemperatureAtPos(player.level(), player.blockPosition()))));
        return 1;
    }

    private static String name(TemperatureLevel level) {
        return level.name().toLowerCase(Locale.ROOT);
    }

    private static void say(CommandSourceStack source, String text) {
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
