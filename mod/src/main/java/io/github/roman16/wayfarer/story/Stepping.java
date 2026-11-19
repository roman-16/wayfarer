package io.github.roman16.wayfarer.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class Stepping {
    private static final int OPERATOR = 2;
    private static final String QUEST = "quest";

    private Stepping() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("step")
                        .requires(source -> source.hasPermission(OPERATOR))
                        .executes(context -> show(context, null))
                        .then(Commands.argument(QUEST, StringArgumentType.word()).executes(context -> show(context, quest(context))))
                        .then(Commands.literal("again")
                                .executes(context -> again(context, null))
                                .then(Commands.argument(QUEST, StringArgumentType.word()).executes(context -> again(context, quest(context)))))
                        .then(Commands.literal("skip")
                                .executes(context -> skip(context, null))
                                .then(Commands.argument(QUEST, StringArgumentType.word()).executes(context -> skip(context, quest(context)))))));
    }

    private static String quest(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, QUEST);
    }

    private static Optional<Engine.Active> active(CommandSourceStack source, String quest) {
        MinecraftServer server = source.getServer();
        if (quest != null) {
            return Engine.active(server).stream().filter(open -> open.quest().id().equals(quest)).findFirst();
        }
        ServerPlayer player = source.getPlayer();
        return player == null ? Engine.main(server) : Engine.tracked(player).or(() -> Engine.main(server));
    }

    private static Optional<Engine.Active> found(CommandSourceStack source, String quest) {
        Optional<Engine.Active> active = active(source, quest);
        if (active.isEmpty()) {
            source.sendFailure(Component.translatable(quest == null ? "wayfarer.step.none" : "wayfarer.step.no_quest", quest));
        }
        return active;
    }

    private static Component heading(CommandSourceStack source, Engine.Active active) {
        Progress progress = Progress.of(source.getServer());
        ServerPlayer player = source.getPlayer();
        return Component.translatable("wayfarer.step.heading", Engine.title(progress, active.quest()), progress.step(active.quest().id()) + 1,
                active.quest().steps().size(), player == null ? active.step().text("objective") : Journal.objective(player, active));
    }

    private static int show(CommandContext<CommandSourceStack> context, String quest) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        return found(source, quest).map(active -> {
            Optional<String> goal = Steps.goal(active);
            Component where = goal.map(name -> Places.aim(Progress.of(server), name).map(pos -> place(source, name, pos))
                    .orElse(Component.translatable("wayfarer.step.unchosen", Component.translatable(Places.label(server, name)))))
                    .orElse(Component.translatable("wayfarer.step.nowhere"));
            source.sendSuccess(() -> Component.translatable("wayfarer.step.shown", heading(source, active), where), false);
            return 1;
        }).orElse(0);
    }

    private static Component place(CommandSourceStack source, String name, BlockPos pos) {
        BlockPos from = BlockPos.containing(source.getPosition());
        return Component.translatable("wayfarer.step.place", Component.translatable(Places.label(source.getServer(), name)), pos.getX(), pos.getY(), pos.getZ(),
                (int) Math.round(Math.sqrt(Steps.horizontal(from, pos))), Steps.direction(from, pos));
    }

    private static int again(CommandContext<CommandSourceStack> context, String quest) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        return found(source, quest).map(active -> {
            Optional<String> goal = Steps.goal(active);
            if (goal.isEmpty()) {
                source.sendFailure(Component.translatable("wayfarer.step.nowhere"));
                return 0;
            }
            String name = goal.get();
            Component label = Component.translatable(Places.label(server, name));
            if (Talk.busy()) {
                source.sendFailure(Component.translatable("wayfarer.step.again.talk", label));
                return 0;
            }
            if (Hold.active(server)) {
                source.sendFailure(Component.translatable("wayfarer.step.again.hold", label));
                return 0;
            }
            Places.Again again = Places.again(server, name);
            if (again == Places.Again.NEVER) {
                source.sendFailure(Component.translatable("wayfarer.step.again.never", label));
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(again == Places.Again.AHEAD ? "wayfarer.step.again.ahead" : "wayfarer.step.again.there", label), true);
            Journal.syncAll(server);
            return 1;
        }).orElse(0);
    }

    private static int skip(CommandContext<CommandSourceStack> context, String quest) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        return found(source, quest).map(active -> {
            Component heading = heading(source, active);
            Story.Step step = active.step();
            Talk.drop(server);
            if (Progress.of(server).choice(active.quest().id(), step.id()).isEmpty()) {
                step.choices().stream().filter(choice -> choice.refused() == null).findFirst()
                        .ifPresent(choice -> Engine.choose(server, active.quest().id(), step.id(), choice));
            }
            Engine.complete(server, active);
            source.sendSuccess(() -> Component.translatable("wayfarer.step.skipped", heading), true);
            return 1;
        }).orElse(0);
    }
}
