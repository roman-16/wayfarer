package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class GateSpike {
    private static final float CORRECT_TOOL_DIVISOR = 30;
    private static final int GAMEMASTER = 2;
    private static final TagKey<Block> GATE_STONE = BlockTags.create(Wayfarer.id("gate_stone"));
    private static final float TICKS_PER_SECOND = 20;
    private static final float WRONG_TOOL_DIVISOR = 100;
    private static final List<Spent> spent = new ArrayList<>();

    private GateSpike() {}

    private record Spent(ServerPlayer player, ItemStack pick) {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("gate")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("dig").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> dig(EntityArgument.getPlayer(context, "player"),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"))))))));
    }

    private static int dig(ServerPlayer player, BlockPos pos) {
        BlockState state = player.serverLevel().getBlockState(pos);
        float progress = state.getDestroyProgress(player, player.serverLevel(), pos);
        String tool = player.getMainHandItem().isEmpty() ? "a bare hand" : BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
        boolean drops = player.hasCorrectToolForDrops(state, player.serverLevel(), pos);
        say(player.server, "gate: %s takes %.1f s to break %s with %s, %s".formatted(player.getScoreboardName(), 1 / progress / TICKS_PER_SECOND,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), tool, drops ? "and it drops" : "and nothing drops"));
        player.gameMode.destroyBlock(pos);
        return 1;
    }

    private static boolean lesserPick(Player player, BlockState state, BlockPos pos) {
        return state.is(GATE_STONE)
                && player.getMainHandItem().canPerformAction(ItemAbilities.PICKAXE_DIG)
                && !LastUseSpike.lastUse(player.getMainHandItem())
                && !player.hasCorrectToolForDrops(state, player.level(), pos);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockPos pos = event.getPosition().orElse(event.getEntity().blockPosition());
        if (lesserPick(event.getEntity(), event.getState(), pos)) {
            float slowdown = Balance.INSTANCE.toolGate().slowdown();
            event.setNewSpeed(event.getNewSpeed() * WRONG_TOOL_DIVISOR / (CORRECT_TOOL_DIVISOR * slowdown));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && lesserPick(player, event.getState(), event.getPos())
                && player.getMainHandItem().isDamageableItem()) {
            spent.add(new Spent(player, player.getMainHandItem()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Spent each : spent) {
            ItemStack pick = each.pick();
            if (!pick.isEmpty()) {
                pick.setDamageValue(pick.getMaxDamage() - 1);
                say(each.player().server, "gate: %s's %s is at its last use, %d of %d".formatted(each.player().getScoreboardName(),
                        BuiltInRegistries.ITEM.getKey(pick.getItem()).getPath(), pick.getMaxDamage() - pick.getDamageValue(), pick.getMaxDamage()));
            } else {
                say(each.player().server, "gate: %s's pick is gone".formatted(each.player().getScoreboardName()));
            }
        }
        spent.clear();
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
