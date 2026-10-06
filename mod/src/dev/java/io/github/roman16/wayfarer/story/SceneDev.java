package io.github.roman16.wayfarer.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.roman16.wayfarer.Wayfarer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class SceneDev {
    private static final int GAMEMASTER = 2;

    private SceneDev() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("scene")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("kind", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    String kind = StringArgumentType.getString(context, "kind");
                                    if (Scenes.stage(player, kind)) {
                                        context.getSource().sendSuccess(() -> Component.literal("scene: " + kind + " staged ahead of " + player.getScoreboardName()), false);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal("scene failed: no ground ahead of " + player.getScoreboardName()
                                            + " fits " + kind + " out of every player's sight"));
                                    return 0;
                                })))));
    }
}
