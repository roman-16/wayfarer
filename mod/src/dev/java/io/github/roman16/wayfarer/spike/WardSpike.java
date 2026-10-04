package io.github.roman16.wayfarer.spike;

import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class WardSpike {
    private static final double FACE = 0.75;
    private static final int GAMEMASTER = 2;
    private static final String GRAVES = "gravestone";
    private static final int GRIEF_REACH = 4;
    private static final int RUNES = 24;
    private static final double SPREAD = 0.3;
    private static final int SWEEP_TICKS = 20;
    private static final List<Ward> wards = new ArrayList<>();

    private WardSpike() {}

    private static final class Ward {
        private final ResourceKey<Level> dimension;
        private final BoundingBox box;
        private final BlockState[] snapshot;
        private boolean dirty;

        private Ward(ServerLevel level, BoundingBox box) {
            this.dimension = level.dimension();
            this.box = box;
            this.snapshot = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
            BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                    .forEach(pos -> snapshot[index(pos)] = level.getBlockState(pos));
        }

        private int index(BlockPos pos) {
            return ((pos.getY() - box.minY()) * box.getZSpan() + (pos.getZ() - box.minZ())) * box.getXSpan() + (pos.getX() - box.minX());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("ward")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("set").then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> set(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                BlockPosArgument.getLoadedBlockPos(context, "to"))))))
                        .then(Commands.literal("lift").executes(context -> lift(context.getSource())))
                        .then(Commands.literal("try")
                                .then(Commands.literal("break").then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> tryBreak(EntityArgument.getPlayer(context, "player"),
                                                        BlockPosArgument.getBlockPos(context, "pos"))))))
                                .then(Commands.literal("place").then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> tryPlace(EntityArgument.getPlayer(context, "player"),
                                                        BlockPosArgument.getBlockPos(context, "pos"))))))
                                .then(Commands.literal("bucket").then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> tryBucket(EntityArgument.getPlayer(context, "player"),
                                                        BlockPosArgument.getBlockPos(context, "pos")))))))));
    }

    private static int set(CommandSourceStack source, BlockPos from, BlockPos to) {
        BoundingBox box = BoundingBox.fromCorners(from, to);
        wards.add(new Ward(source.getLevel(), box));
        say(source.getServer(), "ward: set over %d by %d by %d blocks from %d %d %d".formatted(box.getXSpan(), box.getYSpan(), box.getZSpan(),
                box.minX(), box.minY(), box.minZ()));
        return 1;
    }

    private static int lift(CommandSourceStack source) {
        int lifted = wards.size();
        wards.clear();
        say(source.getServer(), "ward: %d lifted".formatted(lifted));
        return lifted;
    }

    private static int tryBreak(ServerPlayer player, BlockPos pos) {
        BlockState before = player.serverLevel().getBlockState(pos);
        boolean broke = player.gameMode.destroyBlock(pos);
        say(player.server, "ward: %s %s %s at %s".formatted(player.getScoreboardName(), broke ? "broke" : "could not break", name(before), at(pos)));
        return 1;
    }

    private static int tryPlace(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        InteractionResult result = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        say(player.server, "ward: %s placing at %s: %s, the block there is %s".formatted(player.getScoreboardName(), at(pos),
                result.consumesAction() ? "done" : "refused", name(player.serverLevel().getBlockState(pos))));
        return 1;
    }

    private static int tryBucket(ServerPlayer player, BlockPos pos) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos));
        InteractionResult result = player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        say(player.server, "ward: %s emptying a bucket towards %s: %s, the block there is %s".formatted(player.getScoreboardName(), at(pos),
                result.consumesAction() ? "done" : "refused", name(player.serverLevel().getBlockState(pos))));
        return 1;
    }

    private static Ward wardAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        for (Ward ward : wards) {
            if (ward.dimension == level.dimension() && ward.box.isInside(pos)) {
                return ward;
            }
        }
        return null;
    }

    private static boolean warded(Level level, BlockPos pos) {
        return wardAt(level, pos) != null;
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && event.getEntity() instanceof ServerPlayer player
                && warded(player.level(), event.getPos())) {
            event.setCanceled(true);
            holds(player, event.getPos(), "breaking");
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && warded(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                holds(player, event.getPos(), "breaking");
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && warded(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                holds(player, event.getPos(), "placing");
            } else if (level instanceof ServerLevel serverLevel) {
                say(serverLevel.getServer(), "ward: held against %s placing at %s".formatted(
                        event.getEntity() == null ? "something" : event.getEntity().getName().getString(), at(event.getPos())));
            }
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItemStack().getItem() instanceof BucketItem bucket)) {
            return;
        }
        HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle()
                .scale(player.blockInteractionRange())), ClipContext.Block.OUTLINE, bucket.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY
                : ClipContext.Fluid.NONE, player));
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos target = warded(player.level(), block.getBlockPos()) ? block.getBlockPos() : block.getBlockPos().relative(block.getDirection());
            if (warded(player.level(), target)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                holds(player, target, "a bucket");
            }
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> kept = event.getAffectedBlocks().stream().filter(pos -> warded(level, pos)).toList();
        if (!kept.isEmpty()) {
            event.getAffectedBlocks().removeAll(kept);
            kept.stream().limit(RUNES).forEach(pos -> runes(level, pos));
            say(level.getServer(), "ward: held against an explosion, %d blocks kept".formatted(kept.size()));
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> touched = new ArrayList<>(List.of(event.getFaceOffsetPos()));
        PistonStructureResolver helper = event.getStructureHelper();
        if (helper != null && helper.resolve()) {
            for (BlockPos pos : helper.getToPush()) {
                touched.add(pos);
                touched.add(pos.relative(helper.getPushDirection()));
            }
            touched.addAll(helper.getToDestroy());
        }
        touched.stream().filter(pos -> warded(level, pos)).findFirst().ifPresent(pos -> {
            event.setCanceled(true);
            runes(level, pos);
            say(level.getServer(), "ward: held against a piston at %s".formatted(at(event.getPos())));
        });
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && warded(level, event.getPos())) {
            event.setCanceled(true);
            say(level.getServer(), "ward: held against %s at %s".formatted(name(event.getNewState()), at(event.getPos())));
        }
    }

    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Creeper || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        for (Ward ward : wards) {
            if (ward.dimension == level.dimension() && AABB.of(ward.box).inflate(GRIEF_REACH).contains(entity.position())) {
                event.setCanGrief(false);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Ward ward = wardAt(level, event.getPos());
            if (ward != null) {
                ward.dirty = true;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean everyone = server.getTickCount() % SWEEP_TICKS == 0;
        for (Ward ward : wards) {
            ServerLevel level = server.getLevel(ward.dimension);
            if (level != null && (ward.dirty || everyone)) {
                ward.dirty = false;
                sweep(level, ward);
            }
        }
    }

    private static void sweep(ServerLevel level, Ward ward) {
        Map<String, Integer> restored = new TreeMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(ward.box.minX(), ward.box.minY(), ward.box.minZ(), ward.box.maxX(), ward.box.maxY(),
                ward.box.maxZ())) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            int index = ward.index(pos);
            BlockState was = ward.snapshot[index];
            if (now.getBlock() == was.getBlock()) {
                continue;
            }
            if (BuiltInRegistries.BLOCK.getKey(now.getBlock()).getNamespace().equals(GRAVES)) {
                ward.snapshot[index] = now;
                say(level.getServer(), "ward: let a grave stand at %s".formatted(at(pos)));
                continue;
            }
            level.setBlock(pos, was, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            runes(level, pos);
            restored.merge(name(now) + " where " + name(was) + " stood", 1, Integer::sum);
        }
        restored.forEach((what, count) -> say(level.getServer(), "ward: put back %d, %s".formatted(count, what)));
    }

    private static void holds(ServerPlayer player, BlockPos pos, String what) {
        Vec3 face = Vec3.atCenterOf(pos).add(player.getEyePosition().subtract(Vec3.atCenterOf(pos)).normalize().scale(FACE));
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, face.x, face.y, face.z, RUNES, SPREAD, SPREAD, SPREAD, 0.4);
        player.displayClientMessage(Component.literal("The ward holds").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        say(player.server, "ward: held against %s %s at %s".formatted(player.getScoreboardName(), what, at(pos)));
    }

    private static void runes(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RUNES, FACE, FACE, FACE, 0.4);
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static String at(BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }
}
