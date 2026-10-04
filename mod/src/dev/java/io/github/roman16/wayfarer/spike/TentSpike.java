package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import nomadictents.NTSavedData;
import nomadictents.block.TentDoorBlock;
import nomadictents.dimension.DynamicDimensionHelper;
import nomadictents.item.TentItem;
import nomadictents.registries.NTDataComponents;
import nomadictents.structure.TentPlacer;
import nomadictents.tileentity.TentDoorBlockEntity;
import nomadictents.util.Tent;
import nomadictents.util.TentSize;

public final class TentSpike {
    private static final int GAMEMASTER = 2;
    private static final BlockPos INSIDE_DOOR = Tent.calculatePos(0);
    private static final String NOMADIC_TENTS = "nomadictents";

    private TentSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithNomadicTents {
        private WithNomadicTents() {}

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            if (ModList.get().isLoaded(NOMADIC_TENTS)) {
                NeoForge.EVENT_BUS.register(TentSpike.class);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("tent")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("use").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> use(EntityArgument.getPlayer(context, "player"), BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(Commands.literal("door").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> door(EntityArgument.getPlayer(context, "player"), BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(Commands.literal("wake").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> wake(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("sleep").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> sleep(EntityArgument.getPlayer(context, "player"),
                                                BlockPosArgument.getBlockPos(context, "pos"))))))));
    }

    private static int use(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        say(player.server, "tent: %s used %s on %d %d %d%s: %s, now in %s".formatted(player.getScoreboardName(),
                player.getMainHandItem().isEmpty() ? "a bare hand" : player.getMainHandItem().getHoverName().getString(), pos.getX(), pos.getY(),
                pos.getZ(), player.isShiftKeyDown() ? ", crouching" : "", result, player.level().dimension().location()));
        return 1;
    }

    private static int sleep(ServerPlayer player, BlockPos bed) {
        player.startSleepInBed(bed).ifLeft(problem -> say(player.server, "tent: %s cannot sleep: %s".formatted(player.getScoreboardName(), problem)))
                .ifRight(slept -> say(player.server, "tent: %s sleeps".formatted(player.getScoreboardName())));
        return 1;
    }

    private static int door(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof TentDoorBlockEntity door) {
            say(player.server, "tent: %s at the door of tent %d: enter %s, pack up %s".formatted(player.getScoreboardName(), door.getTent().getId(),
                    door.canEnter(player), door.canRemove(player)));
            door.onEnter(player);
            say(player.server, "tent: %s is now in %s".formatted(player.getScoreboardName(), player.level().dimension().location()));
        } else {
            say(player.server, "tent: no tent door at %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
        }
        return 1;
    }

    private static int wake(ServerPlayer player) {
        player.stopSleepInBed(true, true);
        say(player.server, "tent: %s wakes".formatted(player.getScoreboardName()));
        return 1;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof TentItem && event.getHand() == InteractionHand.MAIN_HAND) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (player instanceof ServerPlayer serverPlayer) {
                pitch(serverPlayer, held, event);
            }
        } else if (player.isShiftKeyDown() && held.isEmpty() && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof TentDoorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (player instanceof ServerPlayer serverPlayer && event.getLevel().getBlockEntity(event.getPos()) instanceof TentDoorBlockEntity door) {
                packUp(serverPlayer, door);
            }
        }
    }

    private static void pitch(ServerPlayer player, ItemStack held, PlayerInteractEvent.RightClickBlock event) {
        ServerLevel level = player.serverLevel();
        if (DynamicDimensionHelper.isInsideTent(level)) {
            return;
        }
        UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
        BlockPos door = level.getBlockState(event.getPos()).canBeReplaced(new BlockPlaceContext(context)) ? event.getPos()
                : event.getPos().relative(event.getFace() == null ? Direction.UP : event.getFace());
        Direction facing = player.getDirection();
        if (held.getOrDefault(NTDataComponents.TENT_ID.get(), 0) == 0) {
            held.set(NTDataComponents.TENT_ID.get(), NTSavedData.get(player.server).getNextTentId());
        }
        Tent tent = Tent.from(held, ((TentItem) held.getItem()).getTentType(), size(held));
        TentSize size = TentPlacer.getOverworldSize(tent.getSize());
        long started = System.nanoTime();
        if (!TentPlacer.getInstance().canPlaceTentFrame(level, door, tent.getType(), tent.getSize(), facing)
                || !TentPlacer.getInstance().placeTent(level, door, tent.getType(), size, facing, tent.getColor())
                || !(level.getBlockEntity(door) instanceof TentDoorBlockEntity entrance)) {
            player.displayClientMessage(Component.translatable("tent.build.deny.space"), true);
            say(player.server, "tent: no room for %s's tent at %d %d %d".formatted(player.getScoreboardName(), door.getX(), door.getY(), door.getZ()));
            return;
        }
        entrance.setTent(tent);
        entrance.setDirection(facing);
        entrance.setOwner(player.getUUID());
        entrance.setChanged();
        held.shrink(1);
        say(player.server, "tent: %s pitched tent %d, %s %s, at %d %d %d in %.1f ms".formatted(player.getScoreboardName(), tent.getId(),
                tent.getSize().getSerializedName(), tent.getType().getSerializedName(), door.getX(), door.getY(), door.getZ(),
                (System.nanoTime() - started) / 1e6));
    }

    private static TentSize size(ItemStack held) {
        String path = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        return Arrays.stream(TentSize.values()).filter(size -> path.startsWith(size.getSerializedName() + "_")).findFirst().orElseThrow();
    }

    private static void packUp(ServerPlayer player, TentDoorBlockEntity door) {
        TentDoorBlockEntity.TentDoorResult result = door.canRemove(player);
        if (!result.isAllow()) {
            if (result.hasMessage()) {
                player.displayClientMessage(Component.translatable(result.getRemoveTranslationKey()), true);
            }
            say(player.server, "tent: %s cannot pack up tent %d: %s".formatted(player.getScoreboardName(), door.getTent().getId(), result));
            return;
        }
        Tent pitched = door.getTent();
        BlockPos pos = door.getBlockPos();
        TentPlacer.getInstance().removeTent(player.level(), pos, pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()), door.getDirection());
        ItemStack item = pitched.asItem();
        boolean carried = player.getInventory().add(item);
        if (!carried) {
            player.drop(item, false);
        }
        say(player.server, "tent: %s packed up tent %d %s".formatted(player.getScoreboardName(), pitched.getId(),
                carried ? "into their inventory" : "at their feet"));
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() != null) {
            return;
        }
        Level level = player.level();
        if (!level.dimension().location().getNamespace().equals(NOMADIC_TENTS)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.literal("A bed is only furniture out here.").withStyle(ChatFormatting.GRAY), true);
            say(player.server, "tent: %s's bed is outside a tent".formatted(player.getScoreboardName()));
            return;
        }
        Optional<BlockPos> fire = Optional.empty();
        if (level.getBlockEntity(INSIDE_DOOR) instanceof TentDoorBlockEntity inside && inside.getSpawnDimension() != null) {
            fire = Fire.nearest(inside.getSpawnDimension(), inside.getSpawnpoint(), Balance.INSTANCE.tent().fireBlocks());
        }
        if (fire.isEmpty()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.literal("No fire burns outside the tent.").withStyle(ChatFormatting.GRAY), true);
            say(player.server, "tent: %s has no lit fire within %d blocks outside".formatted(player.getScoreboardName(),
                    Balance.INSTANCE.tent().fireBlocks()));
        } else {
            say(player.server, "tent: a fire burns outside %s's tent at %d %d %d".formatted(player.getScoreboardName(), fire.get().getX(),
                    fire.get().getY(), fire.get().getZ()));
        }
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
