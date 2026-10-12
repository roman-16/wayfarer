package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Caravan;
import io.github.roman16.wayfarer.story.Talk;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import nomadictents.NTSavedData;
import nomadictents.block.TentDoorBlock;
import nomadictents.dimension.DimensionFactory;
import nomadictents.dimension.DynamicDimensionHelper;
import nomadictents.item.TentItem;
import nomadictents.registries.NTDataComponents;
import nomadictents.structure.TentPlacer;
import nomadictents.tileentity.TentDoorBlockEntity;
import nomadictents.util.Tent;
import nomadictents.util.TentSize;

public final class TentPitch {
    private static final BlockPos INSIDE_DOOR = Tent.calculatePos(0);
    private static final Map<UUID, Long> REMINDED = new HashMap<>();
    private static final Set<ResourceKey<Level>> KEPT = new HashSet<>();
    private static final ChunkPos INSIDE = new ChunkPos(INSIDE_DOOR);
    private static final TicketType<ChunkPos> KEEP = TicketType.create("wayfarer_tent", Comparator.comparingLong(ChunkPos::toLong));
    private static final int KEEP_RADIUS = 3;
    private static final int KEEP_TICKS = 64;
    private static final String TENT_PACKED = "tent_packed";
    private static final String TENT_PITCHED = "tent_pitched";

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
        Joining.State state = Joining.State.of(player.server);
        state.tents.removeIf(pitched -> pitched.id() == tent.getId());
        state.tents.add(new Joining.Pitched(player.getUUID(), new Joining.Place(level.dimension(), door.immutable()), tent.getId()));
        state.setDirty();
        Sounds.play(level, door.getCenter(), TENT_PITCHED);
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
        Sounds.play(player.serverLevel(), door.getBlockPos().getCenter(), TENT_PACKED);
        TentPlacer.getInstance().removeTent(player.level(), door.getBlockPos(), pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()),
                door.getDirection());
        ItemStack item = pitched.asItem();
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        Joining.State state = Joining.State.of(player.server);
        state.tents.removeIf(tent -> tent.id() == pitched.getId());
        state.setDirty();
    }

    public static List<Joining.Pitched> mine(ServerPlayer player) {
        return Joining.State.of(player.server).tents.stream().filter(tent -> tent.owner().equals(player.getUUID())).toList();
    }

    public static void remind(ServerPlayer player) {
        long today = player.server.overworld().getDayTime() / Clock.DAY;
        if (Tents.isTent(player.level().dimension()) || !Clock.morning(player.serverLevel()) || REMINDED.getOrDefault(player.getUUID(), -1L) == today) {
            return;
        }
        int reach = Balance.get().tent().reminderBlocks();
        if (mine(player).stream().anyMatch(tent -> tent.place().dimension().equals(player.level().dimension())
                && tent.place().pos().distSqr(player.blockPosition()) > (double) reach * reach
                && !Caravan.within(player.server, player.level(), Vec3.atCenterOf(tent.place().pos())))) {
            REMINDED.put(player.getUUID(), today);
            Talk.tell(player, Component.translatable("wayfarer.tent.behind"));
        }
    }

    public static boolean pitched(ServerPlayer player) {
        return !mine(player).isEmpty();
    }

    public static void bringBack(MinecraftServer server, Joining.Place camp) {
        int away = Balance.get().join().awayBlocks();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Joining.Pitched> far = mine(player).stream().filter(tent -> !tent.place().dimension().equals(camp.dimension())
                    || tent.place().pos().distSqr(camp.pos()) > (double) away * away).toList();
            if (!far.isEmpty() && recall(player, far, Joining.State.of(server).tents)) {
                Talk.tell(player, Component.translatable("wayfarer.tent.brought"));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % KEEP_TICKS != 0) {
            return;
        }
        NTSavedData tents = NTSavedData.get(server);
        Set<ResourceKey<Level>> pitched = new HashSet<>();
        for (Joining.Pitched tent : Joining.State.of(server).tents) {
            ResourceKey<Level> key = tents.getOrCreateKey(server, tent.id());
            pitched.add(key);
            if (KEPT.add(key)) {
                ServerLevel inside = DynamicDimensionHelper.getOrCreateWorld(server, key, DimensionFactory::createDimension);
                inside.getChunkSource().addRegionTicket(KEEP, INSIDE, KEEP_RADIUS, INSIDE);
            }
        }
        for (ResourceKey<Level> key : List.copyOf(KEPT)) {
            if (!pitched.contains(key)) {
                KEPT.remove(key);
                ServerLevel inside = server.getLevel(key);
                if (inside != null) {
                    inside.getChunkSource().removeRegionTicket(KEEP, INSIDE, KEEP_RADIUS, INSIDE);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        KEPT.clear();
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
            int reach = Balance.get().tent().fireBlocks();
            BlockPos outside = BlockPos.containing(inside.getSpawnpoint());
            for (int x = (outside.getX() - reach) >> 4; x <= (outside.getX() + reach) >> 4; x++) {
                for (int z = (outside.getZ() - reach) >> 4; z <= (outside.getZ() + reach) >> 4; z++) {
                    inside.getSpawnDimension().getChunk(x, z);
                }
            }
            fire = Fire.nearest(inside.getSpawnDimension(), inside.getSpawnpoint(), reach);
            if (fire.isEmpty()) {
                Caravan.standing(player.server).ifPresent(camp -> inside.getSpawnDimension().getChunk(camp));
                if (Caravan.within(player.server, inside.getSpawnDimension(), inside.getSpawnpoint())) {
                    fire = Caravan.fire(player.server);
                }
            }
        }
        if (fire.isEmpty()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            String refusal = Caravan.met(player.server) ? "wayfarer.tent.fire_caravan" : "wayfarer.tent.fire";
            player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.GRAY), true);
        } else if (Talk.busy()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("wayfarer.tent.story").withStyle(ChatFormatting.GRAY), true);
        }
    }

    static void recall(ServerPlayer player, List<Joining.Pitched> tents) {
        int away = Balance.get().join().awayBlocks();
        recall(player, tents.stream().filter(tent -> tent.owner().equals(player.getUUID())).filter(tent -> !tent.place().dimension().equals(player.level().dimension())
                || tent.place().pos().distSqr(player.blockPosition()) > (double) away * away).toList(), tents);
    }

    private static boolean recall(ServerPlayer player, List<Joining.Pitched> far, List<Joining.Pitched> tents) {
        MinecraftServer server = player.server;
        boolean brought = false;
        for (Joining.Pitched tent : far) {
            ServerLevel level = server.getLevel(tent.place().dimension());
            tents.remove(tent);
            Joining.State.of(server).setDirty();
            if (level == null) {
                continue;
            }
            BlockPos pos = tent.place().pos();
            level.getChunk(pos);
            if (!(level.getBlockEntity(pos) instanceof TentDoorBlockEntity door) || !player.getUUID().equals(door.getOwner())) {
                continue;
            }
            Tent pitched = door.getTent();
            TentPlacer.getInstance().removeTent(level, pos, pitched.getType(), TentPlacer.getOverworldSize(pitched.getSize()), door.getDirection());
            ItemStack item = pitched.asItem();
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
            brought = true;
        }
        return brought;
    }
}
