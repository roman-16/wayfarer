package io.github.roman16.wayfarer;

import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class WaystoneDev {
    private static final int GAMEMASTER = 2;

    private WaystoneDev() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!ModList.get().isLoaded("waystones")) {
            return;
        }
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("waystone")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
                            return WaystonesAPI.placeWaystone(context.getSource().getLevel(), pos, WaystoneStyles.DEFAULT).map(waystone -> {
                                if (waystone instanceof MutableWaystone named) {
                                    named.setName(Component.literal("Eastmarch"));
                                }
                                context.getSource().sendSuccess(() -> Component.literal("waystone: Eastmarch stands at " + pos.toShortString()), false);
                                return 1;
                            }).orElse(0);
                        }))));
    }
}
