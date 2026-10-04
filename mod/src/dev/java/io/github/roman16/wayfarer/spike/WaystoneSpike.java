package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class WaystoneSpike {
    private static final int GAMEMASTER = 2;
    private static final String OWN = "wayfarer_own_stone";
    private static final int REACH = 8;
    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("waystones", "waystone");
    private static final String WAYSTONES = "waystones";
    private static final Map<UUID, BlockPos> placing = new HashMap<>();
    private static final Map<UUID, UUID> own = new HashMap<>();
    private static final Map<UUID, Vec3> leaving = new HashMap<>();

    private WaystoneSpike() {}

    @EventBusSubscriber(modid = Wayfarer.MODID)
    public static final class WithWaystones {
        private WithWaystones() {}

        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            if (ModList.get().isLoaded(WAYSTONES)) {
                NeoForge.EVENT_BUS.register(WaystoneSpike.class);
                Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, WaystoneSpike::onLeave);
                Balm.getEvents().onEvent(WaystoneTeleportEvent.Post.class, WaystoneSpike::onArrive);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("waystone")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> give(EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("use").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> use(EntityArgument.getPlayer(context, "player"), BlockPosArgument.getBlockPos(context, "pos"))))))
                        .then(Commands.literal("world").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> world(context.getSource(), BlockPosArgument.getBlockPos(context, "pos")))))
                        .then(Commands.literal("fold").then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> fold(EntityArgument.getPlayer(context, "player")))))));
    }

    private static int give(ServerPlayer player) {
        ItemStack stone = new ItemStack(BuiltInRegistries.ITEM.get(STONE));
        CustomData.update(DataComponents.CUSTOM_DATA, stone, tag -> tag.putUUID(OWN, player.getUUID()));
        stone.set(DataComponents.CUSTOM_NAME, Component.literal("Your Stone"));
        player.getInventory().add(stone);
        say(player.server, "waystone: %s carries a stone of their own".formatted(player.getScoreboardName()));
        return 1;
    }

    private static int use(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        say(player.server, "waystone: %s used %s on %d %d %d%s: %s".formatted(player.getScoreboardName(),
                player.getMainHandItem().isEmpty() ? "a bare hand" : player.getMainHandItem().getHoverName().getString(), pos.getX(), pos.getY(), pos.getZ(),
                player.isShiftKeyDown() ? ", crouching" : "", result));
        return 1;
    }

    private static int world(CommandSourceStack source, BlockPos pos) {
        Optional<Waystone> placed = WaystonesAPI.placeWaystone(source.getLevel(), pos, WaystoneStyles.DEFAULT);
        placed.ifPresent(waystone -> {
            if (waystone instanceof MutableWaystone named) {
                named.setName(Component.literal("Eastmarch"));
            }
        });
        say(source.getServer(), placed.map(waystone -> "waystone: the world's waystone %s stands at %d %d %d, origin %s".formatted(
                waystone.getName().getString(), pos.getX(), pos.getY(), pos.getZ(), waystone.getOrigin())).orElse("waystone failed: none placed"));
        return 1;
    }

    private static int fold(ServerPlayer player) {
        Optional<Waystone> mine = Optional.ofNullable(own.get(player.getUUID())).flatMap(uid -> WaystonesAPI.getWaystone(player.server, uid));
        Optional<Waystone> from = WaystonesAPI.getActivatedWaystones(player).stream()
                .filter(waystone -> waystone.getDimension() == player.level().dimension() && waystone.getPos().closerToCenterThan(player.position(), REACH))
                .findFirst();
        if (mine.isEmpty() || from.isEmpty()) {
            say(player.server, "waystone failed: %s %s".formatted(player.getScoreboardName(),
                    mine.isEmpty() ? "has no stone of their own standing" : "stands at no waystone they touched"));
            return 0;
        }
        WaystonesAPI.createUncheckedDefaultTeleportContext(player, mine.get(), context -> context.setFromWaystone(from.get()))
                .ifLeft(context -> WaystonesAPI.tryTeleportAsync(context).thenAccept(result -> result
                        .ifRight(error -> say(player.server, "waystone failed: %s".formatted(error.getComponent().getString())))))
                .ifRight(error -> say(player.server, "waystone failed: %s".formatted(error.getComponent().getString())));
        return 1;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals(WAYSTONES) && held.is(BuiltInRegistries.ITEM.get(STONE))) {
            CustomData data = held.get(DataComponents.CUSTOM_DATA);
            if (data == null || !data.contains(OWN)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("Only a stone of your own can be set down.").withStyle(ChatFormatting.GRAY), true);
                say(player.server, "waystone: %s cannot set down a waystone that is not their own".formatted(player.getScoreboardName()));
                return;
            }
            placing.put(player.getUUID(), event.getPos());
            return;
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            WaystonesAPI.getWaystoneAt(player.serverLevel(), event.getPos())
                    .filter(waystone -> waystone.getWaystoneUid().equals(own.get(player.getUUID())))
                    .ifPresent(waystone -> {
                        event.setCanceled(true);
                        takeUp(player, waystone, "by hand");
                    });
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && placing.containsKey(player.getUUID())) {
            placing.put(player.getUUID(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Map.Entry<UUID, BlockPos> entry : Map.copyOf(placing).entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            placing.remove(entry.getKey());
            if (player == null) {
                continue;
            }
            for (BlockPos pos : new BlockPos[] {entry.getValue(), entry.getValue().above(), entry.getValue().below()}) {
                Optional<Waystone> placed = WaystonesAPI.getWaystoneAt(player.serverLevel(), pos);
                if (placed.isPresent()) {
                    own.put(player.getUUID(), placed.get().getWaystoneUid());
                    if (placed.get() instanceof MutableWaystone named) {
                        named.setName(Component.literal(player.getScoreboardName() + "'s stone"));
                    }
                    BlockPos at = placed.get().getPos();
                    say(event.getServer(), "waystone: %s set their stone down at %d %d %d".formatted(player.getScoreboardName(), at.getX(), at.getY(), at.getZ()));
                    break;
                }
            }
        }
    }

    private static void onLeave(WaystoneTeleportEvent.Pre event) {
        if (event.getContext().getEntity() instanceof ServerPlayer player) {
            leaving.put(player.getUUID(), player.position());
        }
    }

    private static void onArrive(WaystoneTeleportEvent.Post event) {
        if (!(event.getContext().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Vec3 from = leaving.remove(player.getUUID());
        if (from != null) {
            double distance = from.distanceTo(player.position());
            int cost = (int) Math.round(distance / Balance.INSTANCE.waystones().blocksPerFood());
            int before = player.getFoodData().getFoodLevel();
            player.getFoodData().setFoodLevel(Math.max(0, before - cost));
            player.getFoodData().setSaturation(0);
            say(player.server, "waystone: %s folded %.0f blocks and is hungrier, food %d to %d".formatted(player.getScoreboardName(), distance, before,
                    player.getFoodData().getFoodLevel()));
        }
        Waystone target = event.getContext().getTargetWaystone();
        if (target.getWaystoneUid().equals(own.get(player.getUUID()))) {
            takeUp(player, target, "on arriving");
        }
    }

    private static void takeUp(ServerPlayer player, Waystone waystone, String how) {
        ServerLevel level = player.server.getLevel(waystone.getDimension());
        if (level == null) {
            return;
        }
        BlockPos pos = waystone.getPos();
        for (BlockPos part : new BlockPos[] {pos.above(), pos, pos.below()}) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(part).getBlock()).getNamespace().equals(WAYSTONES)) {
                level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        WaystonesAPI.removeWaystoneFromDatabase(player.server, waystone);
        own.remove(player.getUUID());
        ItemStack stone = new ItemStack(BuiltInRegistries.ITEM.get(STONE));
        CustomData.update(DataComponents.CUSTOM_DATA, stone, tag -> tag.putUUID(OWN, player.getUUID()));
        stone.set(DataComponents.CUSTOM_NAME, Component.literal("Your Stone"));
        if (!player.getInventory().add(stone)) {
            player.drop(stone, false);
        }
        say(player.server, "waystone: %s took their stone up %s, back into their pack".formatted(player.getScoreboardName(), how));
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
