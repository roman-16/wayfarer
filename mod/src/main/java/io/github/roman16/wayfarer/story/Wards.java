package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Graves;
import io.github.roman16.wayfarer.Halls;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

public final class Wards {
    private static final int GRIEF_REACH = 4;
    private static final double FOOT = 0.25;
    private static final String BUILD = ".build";
    private static final String FOOTING = "footing";
    private static final String HALL = "hall";
    private static final int GROWTH_REACH = 8;
    private static final int NEAR_BLOCKS = 8;
    private static final int HELD_QUIET_BLOCKS = 128;
    private static final TagKey<Block> HAND_LIGHTS = TagKey.create(Registries.BLOCK, Wayfarer.id("hand_lights"));

    private Wards() {}

    private record Ward(String place, BoundingBox box) {}

    private static List<Ward> wards(MinecraftServer server) {
        List<Ward> wards = new ArrayList<>();
        if (!Clock.storied(server)) {
            return wards;
        }
        Progress progress = Progress.of(server);
        progress.wards.forEach((place, box) -> {
            if (!place.equals(Tollbridge.NAME) || !progress.done(Tollbridge.QUEST)) {
                wards.add(new Ward(place, box));
            }
        });
        Caravan.boxes(server).forEach(box -> wards.add(new Ward(Caravan.NAME, box)));
        return wards;
    }

    static boolean near(ServerLevel level, BlockPos pos) {
        return wards(level.getServer()).stream().anyMatch(ward -> !ward.place().equals(Caravan.NAME) && ward.box().inflatedBy(NEAR_BLOCKS).isInside(pos));
    }

    static boolean within(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD && wards(player.server).stream()
                .anyMatch(ward -> !ward.place().equals(Caravan.NAME) && ward.box().isInside(player.blockPosition()));
    }

    public static boolean guarded(LevelAccessor accessor, BlockPos pos) {
        return at(accessor, pos).isPresent();
    }

    private static Optional<Ward> at(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return Optional.empty();
        }
        return wards(level.getServer()).stream().filter(ward -> ward.box().isInside(pos)).findFirst()
                .or(() -> Clock.storied(level.getServer()) && Halls.built(level, pos) ? Optional.of(new Ward(HALL, new BoundingBox(pos))) : Optional.empty());
    }

    private static Optional<Ward> footing(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level)) {
            return Optional.empty();
        }
        AABB above = new AABB(pos.above()).deflate(FOOT, 0, FOOT);
        boolean stood = !level.getEntitiesOfClass(Entity.class, above, body -> body.getTags().contains(People.TAG)
                && Math.abs(body.getBoundingBox().minY - (pos.getY() + 1)) < FOOT).isEmpty();
        return stood ? Optional.of(new Ward(FOOTING, new BoundingBox(pos))) : Optional.empty();
    }

    private static boolean lit(LevelAccessor accessor, BlockPos pos) {
        return accessor instanceof ServerLevel level && Progress.of(level.getServer()).lit.contains(pos.asLong());
    }

    private static void refuse(Entity entity, Ward ward) {
        refuse(entity, "wayfarer.ward." + ward.place());
    }

    private static void refuse(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && !event.getEntity().isCreative()
                && !Graves.grave(event.getLevel().getBlockState(event.getPos())) && !lit(event.getLevel(), event.getPos())) {
            at(event.getLevel(), event.getPos()).or(() -> footing(event.getLevel(), event.getPos())).ifPresent(ward -> {
                event.setCanceled(true);
                refuse(event.getEntity(), ward);
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (lit(event.getLevel(), event.getPos()) && event.getLevel() instanceof ServerLevel level) {
            Progress progress = Progress.of(level.getServer());
            progress.lit.remove(event.getPos().asLong());
            progress.setDirty();
            return;
        }
        if (!event.getPlayer().isCreative() && !Graves.grave(event.getState())) {
            at(event.getLevel(), event.getPos()).or(() -> footing(event.getLevel(), event.getPos())).ifPresent(ward -> {
                event.setCanceled(true);
                refuse(event.getPlayer(), ward);
            });
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isCreative()) {
            return;
        }
        if (event.getPlacedBlock().is(HAND_LIGHTS) && event.getLevel() instanceof ServerLevel level && at(level, event.getPos()).isPresent()) {
            Progress progress = Progress.of(level.getServer());
            progress.lit.add(event.getPos().asLong());
            progress.setDirty();
            return;
        }
        at(event.getLevel(), event.getPos()).ifPresent(ward -> {
            event.setCanceled(true);
            refuse(event.getEntity(), "wayfarer.ward." + ward.place() + BUILD);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.server.execute(() -> player.containerMenu.sendAllDataToRemote());
            }
        });
    }

    @SubscribeEvent
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (!event.isSimulated() && event.getPlayer() != null && !event.getPlayer().isCreative()) {
            at(event.getLevel(), event.getPos()).ifPresent(ward -> {
                event.setCanceled(true);
                refuse(event.getPlayer(), ward);
            });
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isCreative() && !event.getItemStack().isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof DecoratedPotBlock) {
            at(event.getLevel(), event.getPos()).ifPresent(ward -> {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                refuse(player, ward);
            });
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || !(event.getItemStack().getItem() instanceof BucketItem bucket)) {
            return;
        }
        HitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle()
                .scale(player.blockInteractionRange())), ClipContext.Block.OUTLINE, bucket.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY
                : ClipContext.Fluid.NONE, player));
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            Optional<Ward> ward = at(player.level(), block.getBlockPos()).or(() -> at(player.level(), block.getBlockPos().relative(block.getDirection())));
            ward.ifPresent(found -> {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                refuse(player, found);
            });
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> at(event.getLevel(), pos).isPresent());
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        List<BlockPos> touched = new ArrayList<>(List.of(event.getFaceOffsetPos()));
        PistonStructureResolver helper = event.getStructureHelper();
        if (helper != null && helper.resolve()) {
            for (BlockPos pos : helper.getToPush()) {
                touched.add(pos);
                touched.add(pos.relative(helper.getPushDirection()));
            }
            touched.addAll(helper.getToDestroy());
        }
        if (touched.stream().anyMatch(pos -> at(event.getLevel(), pos).isPresent())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (at(event.getLevel(), event.getPos()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && at(event.getLevel(), event.getPos()).filter(ward -> ward.place().equals(Frost.HALL) || ward.place().equals(ReadingHouse.NAME)).isPresent()
                || event.getMobCategory() == MobCategory.MONSTER && event.getLevel() instanceof ServerLevel level && (Act3.quiet(level, event.getPos()) || held(level, event.getPos()))) {
            event.setCanceled(true);
        }
    }

    private static boolean held(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || !Clock.storied(level.getServer())) {
            return false;
        }
        Progress progress = Progress.of(level.getServer());
        return progress.held.stream().map(progress::place).flatMap(Optional::stream)
                .anyMatch(at -> at.distSqr(pos.atY(at.getY())) <= (double) HELD_QUIET_BLOCKS * HELD_QUIET_BLOCKS);
    }

    @SubscribeEvent
    public static void onGrow(BlockGrowFeatureEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && wards(level.getServer()).stream().anyMatch(ward -> !ward.place().equals(Caravan.NAME) && ward.box().inflatedBy(GROWTH_REACH).isInside(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && wards(level.getServer()).stream().anyMatch(ward -> AABB.of(ward.box()).inflate(GRIEF_REACH).contains(entity.position()))) {
            event.setCanGrief(false);
        }
    }
}
