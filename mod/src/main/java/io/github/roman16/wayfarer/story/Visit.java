package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Balance;
import io.github.roman16.wayfarer.Clock;
import io.github.roman16.wayfarer.Fire;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class Visit {
    private static final String COMING = "coming";
    private static final String SEAT = "seat";
    private static final String LEFT = Wayfarer.MODID + "_left";
    private static final String SITTING = "sitting";
    private static final String VISITS = "visits";
    private static final String EVENING = "evening";
    private static final String MAIN = "main";
    private static final Set<String> FIRESIDE = Set.of("talk", "visit");
    private static final int ACROSS_BLOCKS = 3;
    private static final int SEAT_REACH = 4;
    private static final float FLAT = (float) Math.sqrt(0.5);
    private static final float LOAF_SCALE = 0.5f;
    private static final double LOAF_RISE = 0.03;

    private Visit() {}

    static void tick(MinecraftServer server, Engine.Active active) {
        String visitor = active.step().with();
        Optional<BlockPos> fire = Places.place(server, Caravan.NAME);
        if (visitor == null || fire.isEmpty() || Steps.started(active)) {
            return;
        }
        if (active.scratch().getBoolean(COMING)) {
            arrive(server, active, visitor, fire.get());
            return;
        }
        if (Clock.evening() && !Talk.busy() && !visited(server, active) && Engine.pursued(server, active.quest())
                && Engine.most(server, Engine.players(server).stream().filter(player -> sitting(player, fire.get())).count())) {
            come(server, active, visitor, fire.get());
        }
    }

    private static boolean visited(MinecraftServer server, Engine.Active active) {
        if (Progress.of(server).scratch(VISITS).getLong(EVENING) == today(server)) {
            return true;
        }
        boolean side = !active.quest().kind().equals(MAIN);
        return Engine.active(server).stream().anyMatch(other -> other.quest() != active.quest() && FIRESIDE.contains(other.step().kind())
                && (other.step().kind().equals("visit") && other.scratch().getBoolean(COMING) || Steps.started(other) || side && other.quest().kind().equals(MAIN) && FIRESIDE.contains(other.step().kind())));
    }

    private static long today(MinecraftServer server) {
        return Math.floorDiv(Engine.overworld(server).getDayTime(), Clock.DAY) + 1;
    }

    private static boolean sitting(ServerPlayer player, BlockPos fire) {
        int near = Balance.get().story().nearBlocks();
        return Fire.resting(player) && Steps.horizontal(player.blockPosition(), fire) <= (double) near * near;
    }

    private static void come(MinecraftServer server, Engine.Active active, String visitor, BlockPos fire) {
        ServerLevel level = Engine.overworld(server);
        Vec3 away = away(server, fire);
        Vec3 from = Vec3.atBottomCenterOf(fire).add(away.scale(Balance.get().story().visitBlocks()));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(from));
        float yaw = yaw(Vec3.atBottomCenterOf(ground), Vec3.atBottomCenterOf(fire));
        People.person(level, visitor, Vec3.atBottomCenterOf(ground), yaw, "standing");
        Vec3 seat = seat(level, fire, away);
        People.walk(server, visitor, seat);
        active.scratch().putBoolean(COMING, true);
        active.scratch().put(SEAT, position(seat));
        Progress.of(server).scratch(VISITS).putLong(EVENING, today(server));
        Progress.of(server).setDirty();
    }

    private static void arrive(MinecraftServer server, Engine.Active active, String visitor, BlockPos fire) {
        if (People.walking(visitor)) {
            return;
        }
        Optional<Entity> body = People.find(server, visitor);
        ListTag at = active.scratch().getList(SEAT, Tag.TAG_DOUBLE);
        if (body.isEmpty() || at.size() != 3) {
            active.scratch().remove(COMING);
            Progress.of(server).setDirty();
            return;
        }
        Vec3 seat = new Vec3(at.getDouble(0), at.getDouble(1), at.getDouble(2));
        if (body.get().position().multiply(1, 0, 1).distanceTo(seat.multiply(1, 0, 1)) > 1) {
            People.walk(server, visitor, seat);
            return;
        }
        body.get().moveTo(seat.x, seat.y, seat.z, body.get().getYRot(), 0);
        sit(server, active, visitor, fire);
    }

    private static ListTag position(Vec3 at) {
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(at.x));
        position.add(DoubleTag.valueOf(at.y));
        position.add(DoubleTag.valueOf(at.z));
        return position;
    }

    private static void sit(MinecraftServer server, Engine.Active active, String visitor, BlockPos fire) {
        People.find(server, visitor).ifPresent(body -> {
            float yaw = yaw(body.position(), Vec3.atBottomCenterOf(fire));
            body.moveTo(body.getX(), body.getY(), body.getZ(), yaw, 0);
            body.setYHeadRot(yaw);
            body.setYBodyRot(yaw);
            People.pose(body, SITTING);
        });
        active.scratch().remove(COMING);
        active.scratch().remove(SEAT);
        Steps.poses(server, active.step());
        Steps.start(server, active);
    }

    private static Vec3 away(MinecraftServer server, BlockPos fire) {
        List<ServerPlayer> players = Engine.players(server);
        Vec3 centre = Vec3.atBottomCenterOf(fire);
        Vec3 team = players.stream().map(Entity::position).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / Math.max(1, players.size()));
        Vec3 away = centre.subtract(team).multiply(1, 0, 1);
        return away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize();
    }

    private static Vec3 seat(ServerLevel level, BlockPos fire, Vec3 away) {
        Vec3 across = Vec3.atBottomCenterOf(fire).add(away.scale(ACROSS_BLOCKS));
        return BlockPos.betweenClosedStream(fire.offset(-SEAT_REACH, -1, -SEAT_REACH), fire.offset(SEAT_REACH, 1, SEAT_REACH))
                .filter(pos -> level.getBlockState(pos).is(Blocks.STRIPPED_SPRUCE_LOG) && level.getBlockState(pos.above()).isAir())
                .map(pos -> Vec3.atBottomCenterOf(pos.above()))
                .filter(spot -> level.getEntities((Entity) null, new AABB(spot, spot).inflate(0.5), entity -> !(entity instanceof ServerPlayer))
                        .isEmpty())
                .min(Comparator.comparingDouble(spot -> spot.distanceToSqr(across)))
                .orElse(across);
    }

    private static float yaw(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90;
    }

    static void vanish(MinecraftServer server, String person, @Nullable ResourceLocation left) {
        People.find(server, person).ifPresent(body -> {
            ServerLevel level = (ServerLevel) body.level();
            Vec3 at = body.position();
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 1, at.z, 24, 0.6, 0.8, 0.6, 0.01);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 48, 0.8, 0.8, 0.8, 0.02);
            Places.place(server, Caravan.NAME).ifPresent(fire -> level.sendParticles(ParticleTypes.FLAME, fire.getX() + 0.5, fire.getY() + 0.5,
                    fire.getZ() + 0.5, 32, 0.3, 0.6, 0.3, 0.04));
            body.discard();
            Progress progress = Progress.of(server);
            progress.bodies.remove(person);
            progress.setDirty();
            if (left != null) {
                leave(level, at, left);
            }
        });
    }

    private static void leave(ServerLevel level, Vec3 at, ResourceLocation item) {
        Display.ItemDisplay loaf = EntityType.ITEM_DISPLAY.create(level);
        if (loaf == null) {
            return;
        }
        CompoundTag tag = loaf.saveWithoutId(new CompoundTag());
        tag.put("item", new ItemStack(BuiltInRegistries.ITEM.get(item)).save(level.registryAccess()));
        tag.putString("item_display", "fixed");
        CompoundTag shape = new CompoundTag();
        shape.put("translation", floats(0, 0, 0));
        shape.put("left_rotation", floats(FLAT, 0, 0, FLAT));
        shape.put("scale", floats(LOAF_SCALE, LOAF_SCALE, LOAF_SCALE));
        shape.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", shape);
        tag.put("Pos", position(at.add(0, LOAF_RISE, 0)));
        loaf.load(tag);
        loaf.addTag(LEFT);
        level.addFreshEntity(loaf);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    static void clear(MinecraftServer server, ResourceLocation item) {
        ServerLevel level = Engine.overworld(server);
        level.getEntities(EntityTypeTest.forClass(Display.ItemDisplay.class), display -> display.getTags().contains(LEFT)
                && BuiltInRegistries.ITEM.getKey(display.getSlot(0).get().getItem()).equals(item)).forEach(Entity::discard);
    }
}
