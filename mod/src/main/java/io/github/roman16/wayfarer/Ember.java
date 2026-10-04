package io.github.roman16.wayfarer;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class Ember {
    private static final int GAMEMASTER = 2;

    private Ember() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("ember")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    wake(EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                }))));
    }

    public static void wake(ServerPlayer player) {
        if (Journey.of(player.server).wake()) {
            Component message = Component.translatable("wayfarer.ember.awake").withStyle(ChatFormatting.GOLD);
            player.server.getPlayerList().getPlayers().forEach(member -> member.sendSystemMessage(message));
        }
    }
}
