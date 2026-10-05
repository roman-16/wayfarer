package io.github.roman16.wayfarer;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import io.github.roman16.wayfarer.story.Talk;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
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

public final class TentPitch {
    private static final BlockPos INSIDE_DOOR = Tent.calculatePos(0);

    private TentPitch() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof TentDoorBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            BlockPos lower = state.getValue(TentDoorBlock.HALF) == DoubleBlockHalf.UPPER ? event.getPos().below() : event.getPos();
            if (event.getHand() == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
                    && event.getLevel().getBlockEntity(lower) instanceof TentDoorBlockEntity door) {
                if (player.isShiftKeyDown()) {
                    packUp(serverPlayer, door);
                } else {
                    door.use(event.getLevel().getBlockState(lower), event.getLevel(), lower, player, InteractionHand.MAIN_HAND);
                }
            }
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof TentItem && event.getHand() == InteractionHand.MAIN_HAND) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (player instanceof ServerPlayer serverPlayer) {
                pitch(serverPlayer, held, event);
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
        if (!TentPlacer.getInstance().canPlaceTentFrame(level, door, tent.getType(), tent.getSize(), facing)
                || !TentPlacer.getInstance().placeTent(level, door, tent.getType(), size, facing, tent.getColor())
                || !(level.getBlockEntity(door) instanceof TentDoorBlockEntity entrance)) {
            player.displayClientMessage(Component.translatable("tent.build.deny.space"), true);
            return;
        }
        entrance.setTent(tent);
        entrance.setDirection(facing);
        entrance.setOwner(player.getUUID());
        entrance.setChanged();
        held.shrink(1);
    }

    public static boolean carries(ServerPlayer player) {
        return player.getInventory().items.stream().anyMatch(stack -> stack.getItem() instanceof TentItem);
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
            return;
        }
        Tent pitched = door.getTent();
        TentPlacer.getInstance().removeTent(player.level(), door.getBlockPos(), pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()),
                door.getDirection());
        ItemStack item = pitched.asItem();
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        Level level = player.level();
        if (event.getProblem() != null || !Tents.isTent(level.dimension())) {
            return;
        }
        Optional<BlockPos> fire = Optional.empty();
        if (level.getBlockEntity(INSIDE_DOOR) instanceof TentDoorBlockEntity inside && inside.getSpawnDimension() != null) {
            fire = Fire.nearest(inside.getSpawnDimension(), inside.getSpawnpoint(), Balance.INSTANCE.tent().fireBlocks());
        }
        if (fire.isEmpty()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("wayfarer.tent.fire").withStyle(ChatFormatting.GRAY), true);
        } else if (Talk.busy()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("wayfarer.tent.story").withStyle(ChatFormatting.GRAY), true);
        }
    }

    static void track(ServerPlayer player, Joining.State state) {
        ServerLevel level = player.serverLevel();
        ChunkPos at = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(at.x + dx, at.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TentDoorBlockEntity door && player.getUUID().equals(door.getOwner())
                            && state.tents.stream().noneMatch(tent -> tent.id() == door.getTent().getId())) {
                        state.tents.add(new Joining.Pitched(player.getUUID(), new Joining.Place(level.dimension(), door.getBlockPos()), door.getTent().getId()));
                        state.setDirty();
                    }
                }
            }
        }
    }

    static void recall(ServerPlayer player, List<Joining.Pitched> tents) {
        MinecraftServer server = player.server;
        int away = Balance.INSTANCE.join().awayBlocks();
        for (Joining.Pitched tent : List.copyOf(tents)) {
            if (!tent.owner().equals(player.getUUID())) {
                continue;
            }
            ServerLevel level = server.getLevel(tent.place().dimension());
            BlockPos pos = tent.place().pos();
            if (level == null || level == player.level() && pos.distSqr(player.blockPosition()) <= (double) away * away) {
                continue;
            }
            level.getChunk(pos);
            tents.remove(tent);
            if (!(level.getBlockEntity(pos) instanceof TentDoorBlockEntity door) || !player.getUUID().equals(door.getOwner())) {
                continue;
            }
            Tent pitched = door.getTent();
            TentPlacer.getInstance().removeTent(level, pos, pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()), door.getDirection());
            ItemStack item = pitched.asItem();
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
        }
    }
}
