package io.github.roman16.wayfarer;

import io.netty.buffer.ByteBuf;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import nomadictents.item.TentItem;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class Pitch {
    private static final int RADIUS = 24;
    static final int STAND = 2;
    private static final int FIRE_CLEAR = 2;
    private static final int FLOOR_REACH = 4;
    private static final String UNDERFOOT = "wayfarer.tent.underfoot";
    @Nullable
    private static volatile Found found;

    private Pitch() {}

    public record Find(int slot) implements CustomPacketPayload {
        public static final Type<Find> TYPE = new Type<>(Wayfarer.id("driver_pitch_find"));
        public static final StreamCodec<ByteBuf, Find> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Find::new, Find::slot);

        @Override
        public Type<Find> type() {
            return TYPE;
        }
    }

    public record Found(BlockPos door, int facing, String why) implements CustomPacketPayload {
        public static final Type<Found> TYPE = new Type<>(Wayfarer.id("driver_pitch_found"));
        public static final StreamCodec<ByteBuf, Found> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Found::door, ByteBufCodecs.VAR_INT,
                Found::facing, ByteBufCodecs.STRING_UTF8, Found::why, Found::new);

        @Override
        public Type<Found> type() {
            return TYPE;
        }

        boolean room() {
            return why.isEmpty();
        }

        Direction direction() {
            return Direction.from2DDataValue(facing);
        }

        BlockPos stand() {
            return door.relative(direction().getOpposite(), STAND);
        }
    }

    static void ask(int slot) {
        found = null;
        PacketDistributor.sendToServer(new Find(slot));
    }

    @Nullable
    static Found answer() {
        return found;
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Find.TYPE, Find.STREAM_CODEC, (payload, context) -> context.reply(search((ServerPlayer) context.player(),
                ((ServerPlayer) context.player()).getInventory().getItem(payload.slot()))));
        registrar.playToClient(Found.TYPE, Found.STREAM_CODEC, (payload, context) -> found = payload);
    }

    private static Found search(ServerPlayer player, ItemStack held) {
        if (!(held.getItem() instanceof TentItem)) {
            return new Found(BlockPos.ZERO, 0, "the slot holds no tent");
        }
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        List<BlockPos> columns = IntStream.rangeClosed(-RADIUS, RADIUS).boxed()
                .flatMap(dx -> IntStream.rangeClosed(-RADIUS, RADIUS).mapToObj(dz -> at.offset(dx, 0, dz)))
                .filter(column -> column.distSqr(at) <= RADIUS * RADIUS && level.isLoaded(column))
                .sorted(Comparator.comparingDouble(column -> column.distSqr(at)))
                .toList();
        for (BlockPos column : columns) {
            for (BlockPos door : doors(level, column, at)) {
                if (door.distSqr(at) > RADIUS * RADIUS || !level.getFluidState(door).isEmpty() || !level.getFluidState(door.below()).isEmpty()) {
                    continue;
                }
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    Optional<Component> refused = TentPitch.refusal(player, held, door, facing).map(TentPitch.Refusal::why);
                    if (standable(level, door.relative(facing.getOpposite(), STAND)) && clear(level, door, facing)
                            && (refused.isEmpty() || underfoot(refused.get()))) {
                        return new Found(door, facing.get2DDataValue(), "");
                    }
                }
            }
        }
        return new Found(BlockPos.ZERO, 0, "no ground flat and clear enough for the tent within " + RADIUS + " blocks");
    }

    private static List<BlockPos> doors(ServerLevel level, BlockPos column, BlockPos at) {
        BlockPos surface = column.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()));
        Stream<BlockPos> floors = IntStream.rangeClosed(-FLOOR_REACH, FLOOR_REACH).mapToObj(dy -> column.atY(at.getY() + dy))
                .sorted(Comparator.comparingInt(door -> Math.abs(door.getY() - at.getY())))
                .filter(door -> !door.equals(surface) && level.getBlockState(door).isAir()
                        && !level.getBlockState(door.below()).getCollisionShape(level, door.below()).isEmpty());
        return Stream.concat(floors, Stream.of(surface)).toList();
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return BlockPos.betweenClosedStream(feet.offset(-FIRE_CLEAR, -1, -FIRE_CLEAR), feet.offset(FIRE_CLEAR, 1, FIRE_CLEAR))
                .noneMatch(pos -> level.getBlockState(pos).is(BlockTags.FIRE) || level.getBlockState(pos).is(BlockTags.CAMPFIRES))
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static boolean clear(ServerLevel level, BlockPos door, Direction facing) {
        BlockPos between = door.relative(facing.getOpposite());
        AABB line = new AABB(Vec3.atLowerCornerOf(door), Vec3.atLowerCornerOf(door.relative(facing.getOpposite(), STAND)).add(1, 2, 1)).inflate(1);
        return level.getBlockState(door.above()).isAir() && level.getBlockState(between).isAir() && level.getBlockState(between.above()).isAir()
                && level.getEntities((Entity) null, line, entity -> entity instanceof LivingEntity && !(entity instanceof Player)).isEmpty();
    }

    private static boolean underfoot(Component refusal) {
        return refusal.getContents() instanceof TranslatableContents contents && contents.getKey().equals(UNDERFOOT);
    }
}
