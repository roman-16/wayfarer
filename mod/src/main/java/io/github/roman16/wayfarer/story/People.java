package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.api.pose.ModelPoseAPI;
import de.markusbordihn.easynpc.data.display.DisplayAttributeType;
import de.markusbordihn.easynpc.data.display.NameVisibilityType;
import de.markusbordihn.easynpc.data.model.ModelPartType;
import de.markusbordihn.easynpc.data.scale.CustomScale;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DisplayAttributeDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.ModelScaleDataCapable;
import de.markusbordihn.easynpc.handler.SkinHandler;
import io.github.roman16.wayfarer.Bundled;
import io.github.roman16.wayfarer.Wayfarer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class People {
    public static final String TAG = Wayfarer.MODID + "_person";
    private static final String STANDING = "standing";
    private static final String HUMANOID = "humanoid";
    private static final Bundled<JsonObject> SKINS = Bundled.json("/wayfarer/skins.json");
    private static final Map<String, Long> leaving = new HashMap<>();
    private static final Map<String, Walk> walking = new HashMap<>();
    public static final double WALKING = 0.216;
    public static final double JOGGING = 0.28;
    private static final int CLIMB = 2;
    private static final int GROUND_BELOW = 16;
    private static final int AWAY_BLOCKS = 32;
    private static final int GONE_BLOCKS = 48;
    private static final int OFFSTAGE_BLOCKS = 48;
    private static final int OFFSTAGE_CLIMB = 4;
    private static final int OFFSTAGE_STEP = 8;
    private static final int OFFSTAGE_TURNS = 16;
    private static final int SEEN_BLOCKS = 64;
    private static final int REPLAN_TICKS = 40;
    private static final int STUCK_SECONDS = 30;
    private static final int PLAN_MARGIN = 16;
    private static final int PLAN_MOST = 128;
    private static final int NODES_PER_BLOCK = 64;
    private static final double GAIN = 0.5;
    private static final long NEVER = Long.MIN_VALUE;
    private static final double THERE = 1;
    private static final double EYES = 1.5;
    private static final int LEAVE_SECONDS = 64;
    private static final int TICKS_PER_SECOND = 20;
    private static final double ARROW_SHORT = 2;
    private static final double ARROW_DROP = 0.1;
    private static final double ARROW_LIFT = 0.2;
    private static final float ARROW_SPEED = 1.6f;

    private People() {}

    private static final class Walk {
        private final Vec3 to;
        @Nullable
        private final Arrival arrival;
        private final double pace;
        private List<Vec3> way = List.of();
        private int next;
        private long planned = NEVER;
        private double nearest = Double.MAX_VALUE;
        private long gained;

        private Walk(Vec3 to, @Nullable Arrival arrival, double pace, long now) {
            this.to = to;
            this.arrival = arrival;
            this.pace = pace;
            this.gained = now;
        }
    }

    private record Arrival(float yaw, String pose) {}

    public static Entity spawn(ServerLevel level, String skin, Vec3 at, float yaw, String pose, String... tags) {
        JsonObject look = SKINS.get().getAsJsonObject(skin);
        if (look == null) {
            throw new IllegalArgumentException("generator/skins.toml has no skin " + skin);
        }
        Entity body = EntityType.byString(look.get("model").getAsString()).map(type -> type.create(level))
                .orElseThrow(() -> new IllegalStateException(look.get("model").getAsString() + " is not in this game"));
        Vec3 seat = surface(level, at);
        body.moveTo(seat.x, seat.y, seat.z, yaw, 0);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.setInvulnerable(true);
        body.addTag(TAG);
        for (String tag : tags) {
            body.addTag(tag);
        }
        if (body instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(body);
        if (body instanceof EasyNPC<?> npc) {
            SkinHandler.setSkin(npc, SkinDataEntry.createResourceLocationSkin(Wayfarer.id("textures/entity/people/" + skin + ".png")));
            if (body instanceof ModelScaleDataCapable<?> scaled) {
                scaled.setModelPartScale(ModelPartType.ROOT, new CustomScale(look.get("scale").getAsFloat()));
            }
            pose(body, pose);
        }
        name(body, NameVisibilityType.NEVER);
        return body;
    }

    private static void name(Entity body, NameVisibilityType visibility) {
        if (body instanceof DisplayAttributeDataCapable<?> display) {
            display.setDisplayAttribute(DisplayAttributeType.NAME_VISIBILITY, visibility);
        }
    }

    private static Vec3 surface(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int drop = 0; drop <= GROUND_BELOW; drop++) {
            BlockPos below = pos.below(drop);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty() && !level.getBlockState(below).is(BlockTags.LEAVES)) {
                return new Vec3(at.x, drop == 0 ? Math.max(at.y, below.getY() + shape.max(Direction.Axis.Y)) : below.getY() + shape.max(Direction.Axis.Y), at.z);
            }
        }
        return at;
    }

    public static Entity person(ServerLevel level, String person, Vec3 at, float yaw, String pose) {
        Progress progress = Progress.of(level.getServer());
        find(level.getServer(), person).ifPresent(Entity::discard);
        leaving.remove(person);
        walking.remove(person);
        Entity body = spawn(level, person, at, yaw, pose, TAG + "_" + person);
        body.setCustomName(Component.literal(label(level.getServer(), person)));
        name(body, NameVisibilityType.MOUSE_OVER);
        progress.bodies.put(person, body.getUUID());
        progress.setDirty();
        return body;
    }

    public static Entity place(ServerLevel level, String person, Vec3 at, float yaw, String pose) {
        Optional<Entity> found = find(level.getServer(), person).filter(body -> body.level() == level);
        if (found.isPresent()) {
            Entity body = found.get();
            Vec3 seat = surface(level, at);
            if (body.position().distanceToSqr(seat) <= THERE * THERE) {
                leaving.remove(person);
                walking.remove(person);
                turn(body, yaw);
                pose(body, pose);
                return body;
            }
            if (seen(level, body.position()) || seen(level, seat)) {
                leaving.remove(person);
                pose(body, STANDING);
                walking.put(person, new Walk(seat, new Arrival(yaw, pose), WALKING, level.getGameTime()));
                return body;
            }
        } else {
            Vec3 seat = surface(level, at);
            Optional<Vec3> unseen = seen(level, seat) ? offstage(level, seat) : Optional.empty();
            if (unseen.isPresent()) {
                Entity body = person(level, person, unseen.get(), yaw, STANDING);
                walking.put(person, new Walk(seat, new Arrival(yaw, pose), WALKING, level.getGameTime()));
                return body;
            }
        }
        return person(level, person, at, yaw, pose);
    }

    private static Optional<Vec3> offstage(ServerLevel level, Vec3 seat) {
        for (int reach = OFFSTAGE_STEP; reach <= OFFSTAGE_BLOCKS; reach += OFFSTAGE_STEP) {
            for (int turn = 0; turn < OFFSTAGE_TURNS; turn++) {
                double angle = Math.PI * 2 * turn / OFFSTAGE_TURNS;
                BlockPos column = BlockPos.containing(seat.x + Math.cos(angle) * reach, seat.y, seat.z + Math.sin(angle) * reach);
                int feet = floor(level, column, seat.y);
                Vec3 spot = new Vec3(column.getX() + 0.5, feet, column.getZ() + 0.5);
                if (Math.abs(feet - seat.y) <= OFFSTAGE_CLIMB && !seen(level, spot)) {
                    return Optional.of(spot);
                }
            }
        }
        return Optional.empty();
    }

    static void catchUp(ServerLevel level, String person, Vec3 near, float yaw) {
        Optional<Entity> found = find(level.getServer(), person).filter(body -> body.level() == level);
        if (found.isPresent() && seen(level, found.get().position())) {
            return;
        }
        offstage(level, surface(level, near)).ifPresent(spot -> found.ifPresentOrElse(body -> {
            walking.remove(person);
            body.moveTo(spot.x, spot.y, spot.z, yaw, 0);
        }, () -> person(level, person, spot, yaw, STANDING)));
    }

    public static boolean seen(ServerLevel level, Vec3 at) {
        Vec3 eyes = at.add(0, EYES, 0);
        return level.players().stream().anyMatch(player -> player.position().distanceToSqr(at) <= (double) SEEN_BLOCKS * SEEN_BLOCKS
                && level.clip(new ClipContext(player.getEyePosition(), eyes, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS);
    }

    private static void turn(Entity body, float yaw) {
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
    }

    static Entity role(ServerLevel level, String role, String skin, Vec3 at, float yaw, String pose, String... tags) {
        String[] all = Arrays.copyOf(tags, tags.length + 1);
        all[tags.length] = TAG + "_" + role;
        Entity body = spawn(level, skin, at, yaw, pose, all);
        body.setCustomName(Component.literal(label(level.getServer(), role)));
        name(body, NameVisibilityType.MOUSE_OVER);
        return body;
    }

    public static String label(MinecraftServer server, String person) {
        Story.Person spec = Story.get().person(person);
        Progress progress = Progress.of(server);
        return spec.renamed().entrySet().stream().filter(entry -> progress.named(entry.getKey())).map(Map.Entry::getValue).reduce((first, second) -> second)
                .orElse(spec.unnamed() == null || progress.named(person) ? spec.name() : spec.unnamed());
    }

    static void introduce(MinecraftServer server, List<String> things) {
        Progress progress = Progress.of(server);
        for (String thing : things) {
            if (progress.named.add(thing)) {
                progress.setDirty();
                find(server, thing).ifPresent(body -> body.setCustomName(Component.literal(label(server, thing))));
            }
        }
    }

    public static void pose(Entity body, String pose) {
        if (body instanceof EasyNPC<?> npc) {
            if (pose.equals(STANDING)) {
                stepOff(body);
                ModelPoseAPI.resetPose(npc);
            } else {
                String model = BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).getPath();
                String folder = model.startsWith(HUMANOID) ? HUMANOID : model.replace("_", "");
                ModelPoseAPI.setPose(npc, ResourceLocation.fromNamespaceAndPath("easy_npc", "pose/" + folder + "/" + pose));
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        who(event.getEntity()).ifPresent(person -> {
            UUID body = Progress.of(level.getServer()).bodies.get(person);
            if (body != null && !body.equals(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        });
    }

    private static void stepOff(Entity body) {
        Level level = body.level();
        BlockPos seat = BlockPos.containing(body.getX(), body.getY() - 0.5, body.getZ());
        if (!level.getBlockState(seat).is(BlockTags.LOGS) || body.getY() < seat.getY() + 1) {
            return;
        }
        for (Direction aside : Direction.Plane.HORIZONTAL) {
            BlockPos feet = seat.relative(aside);
            if (level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty() && level.getBlockState(feet.above()).canBeReplaced()
                    && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                body.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, body.getYRot(), 0);
                return;
            }
        }
    }

    static void walk(MinecraftServer server, String person, Vec3 to, double pace) {
        find(server, person).ifPresent(body -> {
            pose(body, STANDING);
            walking.put(person, new Walk(to, null, pace, body.level().getGameTime()));
        });
    }

    static void shoot(MinecraftServer server, String person) {
        find(server, person).ifPresent(body -> Engine.players(server).stream().filter(player -> player.level() == body.level())
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(body))).ifPresent(target -> {
                    Vec3 feet = target.position().add(body.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(ARROW_SHORT));
                    Arrow arrow = new Arrow(body.level(), body.getX(), body.getEyeY() - ARROW_DROP, body.getZ(), new ItemStack(Items.ARROW), null);
                    arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                    arrow.setOwner(body);
                    Vec3 toward = feet.subtract(arrow.position());
                    arrow.shoot(toward.x, toward.y + toward.horizontalDistance() * ARROW_LIFT, toward.z, ARROW_SPEED, 0);
                    body.level().addFreshEntity(arrow);
                    body.level().playSound(null, body, SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1, 1);
                }));
    }

    static boolean skinned(String skin) {
        return SKINS.get().has(skin);
    }

    static boolean leaving(String person) {
        return leaving.containsKey(person);
    }

    static boolean walking(String person) {
        return walking.containsKey(person);
    }

    static void leave(MinecraftServer server, String person) {
        find(server, person).ifPresent(body -> {
            Vec3 from = Engine.players(server).stream().filter(player -> player.level() == body.level())
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(body))).map(Entity::position).orElse(body.position());
            Vec3 away = body.position().subtract(from).multiply(1, 0, 1);
            walk(server, person, body.position().add((away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize()).scale(AWAY_BLOCKS)), WALKING);
            leaving.put(person, body.level().getGameTime() + (long) LEAVE_SECONDS * TICKS_PER_SECOND);
        });
    }

    private static void step(MinecraftServer server) {
        for (Map.Entry<String, Walk> entry : List.copyOf(walking.entrySet())) {
            Optional<Entity> found = find(server, entry.getKey());
            if (found.isEmpty()) {
                walking.remove(entry.getKey());
                continue;
            }
            Entity body = found.get();
            Walk walk = entry.getValue();
            long now = body.level().getGameTime();
            double left = walk.to.subtract(body.position()).multiply(1, 0, 1).length();
            boolean unseen = walk.arrival != null && body.level() instanceof ServerLevel level && !seen(level, body.position()) && !seen(level, walk.to);
            boolean stuck = walk.arrival != null && now - walk.gained >= (long) STUCK_SECONDS * TICKS_PER_SECOND;
            if (left <= walk.pace || unseen || stuck) {
                walking.remove(entry.getKey());
                if (walk.arrival != null) {
                    body.moveTo(walk.to.x, walk.to.y, walk.to.z, walk.arrival.yaw(), 0);
                    turn(body, walk.arrival.yaw());
                    pose(body, walk.arrival.pose());
                }
                continue;
            }
            if (left < walk.nearest - GAIN || walk.planned == NEVER) {
                walk.nearest = left;
                walk.gained = now;
            }
            boolean ended = walk.next >= walk.way.size();
            if (walk.planned == NEVER || now - walk.planned >= REPLAN_TICKS && (ended || now - walk.gained >= REPLAN_TICKS)) {
                walk.way = way(body, walk.to);
                walk.next = 0;
                walk.planned = now;
            }
            if (walk.next >= walk.way.size()) {
                face(body, walk.to);
                continue;
            }
            Vec3 node = walk.way.get(walk.next);
            Vec3 flat = node.subtract(body.position()).multiply(1, 0, 1);
            Vec3 at = flat.length() <= walk.pace ? node : body.position().add(flat.normalize().scale(walk.pace));
            if (flat.length() <= walk.pace) {
                walk.next++;
            }
            face(body, node);
            body.moveTo(at.x, footing(body.level(), at, body.getY(), node.y), at.z, body.getYRot(), 0);
        }
    }

    private static List<Vec3> way(Entity body, Vec3 to) {
        if (!(body instanceof Mob mob) || !(body.level() instanceof ServerLevel level)) {
            return List.of();
        }
        int range = (int) Math.min(body.position().distanceTo(to) + PLAN_MARGIN, PLAN_MOST);
        BlockPos from = body.blockPosition();
        WalkNodeEvaluator nodes = new WalkNodeEvaluator();
        nodes.setCanPassDoors(true);
        nodes.setCanOpenDoors(false);
        nodes.setCanFloat(false);
        PathNavigationRegion region = new PathNavigationRegion(level, from.offset(-range, -range, -range), from.offset(range, range, range));
        Path path = new PathFinder(nodes, range * NODES_PER_BLOCK).findPath(region, mob, Set.of(BlockPos.containing(to)), range, 1, 1);
        if (path == null) {
            return List.of();
        }
        List<Vec3> way = new ArrayList<>();
        for (int index = 0; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNodePos(index);
            way.add(new Vec3(node.getX() + 0.5, node.getY(), node.getZ() + 0.5));
        }
        if (path.canReach()) {
            way.add(to);
        }
        return way;
    }

    private static double footing(Level level, Vec3 at, double from, double to) {
        int top = (int) Math.floor(Math.max(from, to)) + 1;
        int bottom = (int) Math.floor(Math.min(from, to)) - 1;
        for (int y = top; y >= bottom; y--) {
            BlockPos below = BlockPos.containing(at.x, y - 1, at.z);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty() && !level.getBlockState(below).is(BlockTags.LEAVES) && below.getY() + shape.max(Direction.Axis.Y) <= top) {
                return below.getY() + shape.max(Direction.Axis.Y);
            }
        }
        return to;
    }

    private static void face(Entity body, Vec3 toward) {
        Vec3 way = toward.subtract(body.position()).multiply(1, 0, 1);
        if (way.lengthSqr() > 0) {
            turn(body, (float) Math.toDegrees(Math.atan2(-way.x, way.z)));
        }
    }

    private static int floor(Level level, BlockPos at, double from) {
        int top = (int) Math.floor(from);
        for (int y = top + CLIMB; y >= top - CLIMB * 2; y--) {
            BlockPos feet = new BlockPos(at.getX(), y, at.getZ());
            if (level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty() && level.getBlockState(feet.above()).canBeReplaced()
                    && ground(level.getBlockState(feet.below()))) {
                return y;
            }
        }
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
        while (feet.getY() > level.getMinBuildHeight() && (!level.getFluidState(feet.below()).isEmpty() || !ground(level.getBlockState(feet.below())))) {
            feet.move(0, -1, 0);
        }
        return feet.getY();
    }

    private static boolean ground(BlockState state) {
        return !state.canBeReplaced() && !state.is(BlockTags.LEAVES);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        step(server);
        for (Map.Entry<String, Long> entry : List.copyOf(leaving.entrySet())) {
            Optional<Entity> body = find(server, entry.getKey());
            if (body.isEmpty()) {
                leaving.remove(entry.getKey());
            } else if (body.get().level().getGameTime() >= entry.getValue() && body.get().level() instanceof ServerLevel level && !seen(level, body.get().position())
                    || Engine.players(server).stream()
                    .noneMatch(player -> player.level() == body.get().level() && player.distanceToSqr(body.get()) <= (double) GONE_BLOCKS * GONE_BLOCKS)) {
                body.get().discard();
                leaving.remove(entry.getKey());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        leaving.clear();
        walking.clear();
    }

    public static Optional<Entity> find(MinecraftServer server, String person) {
        UUID id = Progress.of(server).bodies.get(person);
        if (id == null) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity body = level.getEntity(id);
            if (body != null && body.isAlive()) {
                return Optional.of(body);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> who(Entity body) {
        return body.getTags().stream().filter(tag -> tag.startsWith(TAG + "_")).map(tag -> tag.substring(TAG.length() + 1)).findFirst();
    }
}
