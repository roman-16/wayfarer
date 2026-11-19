package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.api.pose.ModelPoseAPI;
import de.markusbordihn.easynpc.data.display.DisplayAttributeType;
import de.markusbordihn.easynpc.data.display.NameVisibilityType;
import de.markusbordihn.easynpc.data.model.ModelPartType;
import de.markusbordihn.easynpc.data.scale.CustomScale;
import de.markusbordihn.easynpc.data.skin.SkinDataEntry;
import de.markusbordihn.easynpc.data.sound.SoundDataEntry;
import de.markusbordihn.easynpc.data.sound.SoundDataSet;
import de.markusbordihn.easynpc.data.sound.SoundType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DisplayAttributeDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.ModelScaleDataCapable;
import de.markusbordihn.easynpc.entity.easynpc.data.SoundDataCapable;
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
import java.util.stream.Stream;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class People {
    public static final String TAG = Wayfarer.MODID + "_person";
    public static final String APPEARS = Wayfarer.MODID + "_appears";
    private static final String STANDING = "standing";
    private static final String HUMANOID = "humanoid";
    private static final Bundled<JsonObject> SKINS = Bundled.json("/wayfarer/skins.json");
    private static final Map<String, Long> leaving = new HashMap<>();
    private static final Map<Entity, Walk> figures = new HashMap<>();
    private static final Map<String, Walk> walking = new HashMap<>();
    private static final Map<UUID, Glow> glowing = new HashMap<>();
    private static final int GLOW_LIGHT = 8;
    public static final double WALKING = 0.216;
    public static final double JOGGING = 0.28;
    private static final int AWAY_BLOCKS = 32;
    private static final int GONE_BLOCKS = 48;
    private static final int OFFSTAGE_BLOCKS = 48;
    private static final int OFFSTAGE_CLIMB = 4;
    private static final int OFFSTAGE_STEP = 8;
    private static final int OFFSTAGE_TURNS = 16;
    private static final int SEEN_BLOCKS = 64;
    private static final int VANISH_BLOCKS = 24;
    private static final int VANISH_SECONDS = 4;
    private static final int REPLAN_TICKS = 40;
    private static final int STUCK_SECONDS = 30;
    private static final int YIELD_SECONDS = 4;
    private static final double HEAD_ON = 0.5;
    private static final double[] SIDESTEPS = {45, -45, 90, -90};
    private static final double STEP_UP = 0.6;
    private static final int PLAN_MARGIN = 16;
    private static final int PLAN_MOST = 128;
    private static final int NODES_PER_BLOCK = 64;
    private static final double GAIN = 0.5;
    private static final long NEVER = Long.MIN_VALUE;
    private static final double THERE = 1;
    static final String POSE = Wayfarer.MODID + "_pose_";
    private static final Bundled<JsonObject> LIES = Bundled.json("/wayfarer/lying.json");
    private static final String ACROSS = "across";
    private static final double LYING_HEIGHT = 0.5;
    private static final double LYING_REACH = 0.9;
    private static final double LYING_SLACK = 0.05;
    private static final double LYING_WIDTH = 0.3;
    private static final float QUARTER_TURN = 90;
    private static final double ROOM = 0.75;
    private static final int ROOM_REACH = 2;
    private static final double GAP = 2;
    private static final int RING_TURNS = 12;
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
        private long yielding = NEVER;
        private long unseenSince = NEVER;

        private Walk(Vec3 to, @Nullable Arrival arrival, double pace, long now) {
            this.to = to;
            this.arrival = arrival;
            this.pace = pace;
            this.gained = now;
        }
    }

    private record Arrival(float yaw, String pose) {}

    private record Glow(ServerLevel level, @Nullable BlockPos at) {}

    public static Entity spawn(ServerLevel level, String skin, Vec3 at, float yaw, String pose, String... tags) {
        JsonObject look = SKINS.get().getAsJsonObject(skin);
        if (look == null) {
            throw new IllegalArgumentException("generator/skins.toml has no skin " + skin);
        }
        Entity body = EntityType.byString(look.get("model").getAsString()).map(type -> type.create(level))
                .orElseThrow(() -> new IllegalStateException(look.get("model").getAsString() + " is not in this game"));
        Vec3 feet = Ground.stand(level, at);
        Laid laid = lying(pose) ? lie(level, feet, yaw, pose) : new Laid(feet, yaw);
        body.moveTo(laid.feet().x, laid.feet().y, laid.feet().z, laid.yaw(), 0);
        body.setYHeadRot(laid.yaw());
        body.setYBodyRot(laid.yaw());
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
        if (body instanceof SoundDataCapable<?> voiced) {
            silence(voiced);
        }
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

    private static void silence(SoundDataCapable<?> body) {
        SoundDataSet own = new SoundDataSet();
        SoundDataEntry step = body.getResolvedSoundDataSet().getSound(SoundType.STEP);
        if (step != null && step.getSoundEvent() != null) {
            own.addSound(SoundType.STEP, step.getSoundEvent());
        }
        own.addSound(SoundType.AMBIENT, SoundEvents.EMPTY.getLocation(), 0, 1, false);
        body.setSoundDataSet(own);
    }

    private static void name(Entity body, NameVisibilityType visibility) {
        if (body instanceof DisplayAttributeDataCapable<?> display) {
            display.setDisplayAttribute(DisplayAttributeType.NAME_VISIBILITY, visibility);
        }
    }

    public static Entity person(ServerLevel level, String person, Vec3 at, float yaw, String pose) {
        return person(level, person, at, yaw, pose, TAG + "_" + person);
    }

    static Entity appear(ServerLevel level, String person, Vec3 at, float yaw, String pose) {
        Entity body = person(level, person, at, yaw, pose, TAG + "_" + person, APPEARS);
        glowing.put(body.getUUID(), new Glow(level, null));
        return body;
    }

    private static Entity person(ServerLevel level, String person, Vec3 at, float yaw, String pose, String... tags) {
        Progress progress = Progress.of(level.getServer());
        find(level.getServer(), person).ifPresent(Entity::discard);
        leaving.remove(person);
        walking.remove(person);
        Entity body = spawn(level, person, at, yaw, pose, tags);
        body.setCustomName(Component.literal(label(level.getServer(), person)));
        name(body, NameVisibilityType.MOUSE_OVER);
        progress.bodies.put(person, body.getUUID());
        progress.setDirty();
        return body;
    }

    public static Entity place(ServerLevel level, String person, Vec3 wanted, float yaw, String pose) {
        Vec3 at = room(level, person, wanted);
        Optional<Entity> found = find(level.getServer(), person).filter(body -> body.level() == level);
        if (found.isPresent()) {
            Entity body = found.get();
            Vec3 seat = Ground.stand(level, at);
            if (body.position().distanceToSqr(seat) <= THERE * THERE) {
                leaving.remove(person);
                walking.remove(person);
                turn(body, yaw);
                pose(body, pose);
                return body;
            }
            boolean far = body.position().distanceToSqr(seat) > (double) OFFSTAGE_BLOCKS * OFFSTAGE_BLOCKS;
            if (far && vanished(level, body.position())) {
                Optional<Vec3> unseen = seen(level, seat) ? offstage(level, seat) : Optional.of(seat);
                if (unseen.isPresent()) {
                    Entity moved = person(level, person, unseen.get(), yaw, unseen.get() == seat ? pose : STANDING);
                    if (unseen.get() != seat) {
                        walking.put(person, new Walk(seat, new Arrival(yaw, pose), WALKING, level.getGameTime()));
                    }
                    return moved;
                }
            }
            if (seen(level, body.position()) || seen(level, seat)) {
                leaving.remove(person);
                pose(body, STANDING);
                walking.put(person, new Walk(seat, new Arrival(yaw, pose), WALKING, level.getGameTime()));
                return body;
            }
        } else {
            Vec3 seat = Ground.stand(level, at);
            Optional<Vec3> unseen = seen(level, seat) ? offstage(level, seat) : Optional.empty();
            if (unseen.isPresent()) {
                Entity body = person(level, person, unseen.get(), yaw, STANDING);
                walking.put(person, new Walk(seat, new Arrival(yaw, pose), WALKING, level.getGameTime()));
                return body;
            }
        }
        return person(level, person, at, yaw, pose);
    }

    public static boolean lying(String pose) {
        return LIES.get().has(pose);
    }

    public static AABB length(Vec3 feet, float yaw, String pose) {
        Vec3 along = Vec3.directionFromRotation(0, LIES.get().get(pose).getAsString().equals(ACROSS) ? yaw + QUARTER_TURN : yaw).scale(LYING_REACH);
        return new AABB(feet.x - Math.abs(along.x) - LYING_WIDTH, feet.y, feet.z - Math.abs(along.z) - LYING_WIDTH, feet.x + Math.abs(along.x) + LYING_WIDTH,
                feet.y + LYING_HEIGHT, feet.z + Math.abs(along.z) + LYING_WIDTH);
    }

    private record Laid(Vec3 feet, float yaw) {}

    private static Laid lie(ServerLevel level, Vec3 feet, float yaw, String pose) {
        Optional<Float> here = clear(level, feet, yaw, pose);
        if (here.isPresent()) {
            return new Laid(feet, here.get());
        }
        for (int reach = 1; reach <= ROOM_REACH; reach++) {
            for (int turn = 0; turn < OFFSTAGE_TURNS; turn++) {
                double angle = Math.PI * 2 * turn / OFFSTAGE_TURNS;
                Optional<Vec3> spot = Ground.feet(level, column(feet, angle, reach)).filter(found -> Math.abs(found.y - feet.y) <= 1 && free(level, null, found));
                Optional<Float> fits = spot.flatMap(found -> clear(level, found, yaw, pose));
                if (fits.isPresent()) {
                    return new Laid(spot.get(), fits.get());
                }
            }
        }
        return new Laid(feet, yaw);
    }

    private static Optional<Float> clear(ServerLevel level, Vec3 feet, float yaw, String pose) {
        float square = Math.round(yaw / QUARTER_TURN) * QUARTER_TURN;
        for (int turn = 0; turn < 8; turn++) {
            float turned = (turn < 4 ? yaw : square) + turn % 4 * QUARTER_TURN;
            AABB length = length(feet, turned, pose);
            if (level.noCollision(length.deflate(LYING_SLACK)) && level.getEntities((Entity) null, length, body -> body.getTags().contains(TAG)).isEmpty()) {
                return Optional.of(turned);
            }
        }
        return Optional.empty();
    }

    static Vec3 room(ServerLevel level, @Nullable String person, Vec3 at) {
        if (free(level, person, at)) {
            return at;
        }
        for (int reach = 1; reach <= ROOM_REACH; reach++) {
            for (int turn = 0; turn < OFFSTAGE_TURNS; turn++) {
                double angle = Math.PI * 2 * turn / OFFSTAGE_TURNS;
                Optional<Vec3> spot = Ground.feet(level, column(at, angle, reach)).filter(found -> Math.abs(found.y - at.y) <= 1 && free(level, person, found));
                if (spot.isPresent()) {
                    return spot.get();
                }
            }
        }
        return at;
    }

    private static boolean free(ServerLevel level, @Nullable String person, Vec3 at) {
        return level.getEntities((Entity) null, new AABB(at, at).inflate(ROOM, 1, ROOM),
                body -> body.getTags().contains(TAG) && body.isAlive() && (person == null || !who(body).filter(person::equals).isPresent())).isEmpty();
    }

    static Optional<Vec3> offstage(ServerLevel level, Vec3 seat) {
        for (int reach = OFFSTAGE_STEP; reach <= OFFSTAGE_BLOCKS; reach += OFFSTAGE_STEP) {
            for (int turn = 0; turn < OFFSTAGE_TURNS; turn++) {
                double angle = Math.PI * 2 * turn / OFFSTAGE_TURNS;
                Optional<Vec3> spot = Ground.feet(level, column(seat, angle, reach)).filter(found -> Math.abs(found.y - seat.y) <= OFFSTAGE_CLIMB && !seen(level, found));
                if (spot.isPresent()) {
                    return spot;
                }
            }
        }
        return Optional.empty();
    }

    static List<Vec3> ring(ServerLevel level, Vec3 centre, Vec3 viewer, double radius, int count) {
        Vec3 away = centre.subtract(viewer).multiply(1, 0, 1);
        double facing = away.lengthSqr() < 1e-4 ? 0 : Math.atan2(away.z, away.x);
        List<Vec3> spots = new ArrayList<>();
        for (int tried = 0; spots.size() < count && tried < RING_TURNS; tried++) {
            double angle = facing + (tried % 2 == 0 ? 1 : -1) * ((tried + 1) / 2) * Math.PI * 2 / RING_TURNS;
            Ground.feet(level, centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius))
                    .filter(feet -> Math.abs(feet.y - centre.y) <= OFFSTAGE_CLIMB && Ground.standing(level, feet) && apart(level, feet, spots)).ifPresent(spots::add);
        }
        while (spots.size() < count) {
            double angle = facing + Math.PI * 2 * spots.size() / count;
            spots.add(Ground.stand(level, centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius)));
        }
        return spots;
    }

    private static boolean apart(ServerLevel level, Vec3 feet, List<Vec3> taken) {
        return taken.stream().noneMatch(spot -> spot.distanceToSqr(feet) < GAP * GAP)
                && level.players().stream().noneMatch(player -> player.position().distanceToSqr(feet) < GAP * GAP)
                && level.getEntities((Entity) null, new AABB(feet, feet).inflate(GAP / 2, 1, GAP / 2), body -> body instanceof LivingEntity && !(body instanceof Player)).isEmpty();
    }

    private static Vec3 column(Vec3 from, double angle, int reach) {
        BlockPos column = BlockPos.containing(from.x + Math.cos(angle) * reach, from.y, from.z + Math.sin(angle) * reach);
        return new Vec3(column.getX() + 0.5, from.y, column.getZ() + 0.5);
    }

    static void catchUp(ServerLevel level, String person, Vec3 near, float yaw) {
        Optional<Entity> found = find(level.getServer(), person).filter(body -> body.level() == level);
        if (found.isPresent() && !vanished(level, found.get().position())) {
            return;
        }
        offstage(level, Ground.stand(level, near)).ifPresent(spot -> found.ifPresentOrElse(body -> {
            walking.remove(person);
            body.moveTo(spot.x, spot.y, spot.z, yaw, 0);
        }, () -> person(level, person, spot, yaw, STANDING)));
    }

    private static boolean vanished(ServerLevel level, Vec3 at) {
        return !seen(level, at) && level.players().stream().noneMatch(player -> player.position().distanceToSqr(at) <= (double) VANISH_BLOCKS * VANISH_BLOCKS);
    }

    public static boolean seen(ServerLevel level, Vec3 at) {
        Vec3 eyes = at.add(0, EYES, 0);
        return level.players().stream().anyMatch(player -> player.position().distanceToSqr(at) <= (double) SEEN_BLOCKS * SEEN_BLOCKS && Sight.sees(player, eyes));
    }

    private static void turn(Entity body, float yaw) {
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
    }

    static Entity role(ServerLevel level, String role, String skin, Vec3 at, float yaw, String pose, String... tags) {
        String[] all = Arrays.copyOf(tags, tags.length + 1);
        all[tags.length] = TAG + "_" + role;
        Entity body = spawn(level, skin, room(level, null, at), yaw, pose, all);
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

    public static boolean renamed(MinecraftServer server, String person) {
        Progress progress = Progress.of(server);
        return Story.get().person(person).renamed().keySet().stream().anyMatch(progress::named);
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
        body.getTags().removeIf(tag -> tag.startsWith(POSE));
        body.addTag(POSE + pose);
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

    static void approach(Entity body, Vec3 to, float yaw) {
        figures.put(body, new Walk(to, new Arrival(yaw, STANDING), WALKING, body.level().getGameTime()));
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
            if (found.isEmpty() || advance(found.get(), entry.getKey(), entry.getValue())) {
                walking.remove(entry.getKey());
            }
        }
        figures.entrySet().removeIf(entry -> !entry.getKey().isAlive() || advance(entry.getKey(), null, entry.getValue()));
    }

    private static boolean advance(Entity body, @Nullable String person, Walk walk) {
        long now = body.level().getGameTime();
        double left = walk.to.subtract(body.position()).multiply(1, 0, 1).length();
        boolean out = walk.arrival != null && body.level() instanceof ServerLevel level && vanished(level, body.position()) && vanished(level, walk.to);
        walk.unseenSince = out ? walk.unseenSince == NEVER ? now : walk.unseenSince : NEVER;
        boolean unseen = out && now - walk.unseenSince >= (long) VANISH_SECONDS * TICKS_PER_SECOND;
        boolean stuck = now - walk.gained >= (long) STUCK_SECONDS * TICKS_PER_SECOND;
        if (left <= walk.pace || unseen) {
            if (walk.arrival != null) {
                Vec3 to = body.level() instanceof ServerLevel level ? room(level, person, walk.to) : walk.to;
                body.moveTo(to.x, to.y, to.z, walk.arrival.yaw(), 0);
                turn(body, walk.arrival.yaw());
                pose(body, walk.arrival.pose());
            }
            return true;
        }
        if (stuck) {
            if (walk.arrival == null) {
                return true;
            }
            if (body.level() instanceof ServerLevel level && vanished(level, body.position())) {
                Optional<Vec3> nearer = offstage(level, walk.to).filter(spot -> spot.distanceToSqr(walk.to) < body.position().distanceToSqr(walk.to));
                if (nearer.isPresent()) {
                    body.moveTo(nearer.get().x, nearer.get().y, nearer.get().z, body.getYRot(), 0);
                    walk.gained = now;
                    walk.nearest = Double.MAX_VALUE;
                    walk.planned = NEVER;
                    return false;
                }
            }
            face(body, walk.to);
            return false;
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
            return false;
        }
        while (walk.next < walk.way.size() - 1 && taken(body, walk.way.get(walk.next))) {
            walk.next++;
        }
        Vec3 node = walk.way.get(walk.next);
        Vec3 flat = node.subtract(body.position()).multiply(1, 0, 1);
        Vec3 at = flat.length() <= walk.pace ? node : body.position().add(flat.normalize().scale(walk.pace));
        if (flat.length() <= walk.pace) {
            walk.next++;
        }
        face(body, node);
        Vec3 next = new Vec3(at.x, footing(body.level(), at, body.getY(), node.y), at.z);
        Optional<Entity> blocker = blocker(body, next);
        if (blocker.isPresent()) {
            Optional<Vec3> aside = aside(body, flat, walk.pace);
            if (aside.isPresent()) {
                next = aside.get();
            } else {
                walk.yielding = walk.yielding == NEVER ? now : walk.yielding;
                if (moving(blocker.get()) || now - walk.yielding < (long) YIELD_SECONDS * TICKS_PER_SECOND) {
                    return false;
                }
            }
        } else {
            walk.yielding = NEVER;
        }
        Vec3 level = new Vec3(next.x, Math.max(next.y, body.getY()), next.z);
        Stream.of(next, level).filter(to -> body.level().noCollision(body, stepped(body, to))).findFirst()
                .ifPresent(to -> body.moveTo(to.x, to.y, to.z, body.getYRot(), 0));
        return false;
    }

    private static final class Footholds extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            PathType type = super.getPathType(context, x, y, z);
            BlockPos at = new BlockPos(x, y, z);
            VoxelShape shape = context.getBlockState(at).getCollisionShape(context.level(), at);
            return (type == PathType.OPEN || type == PathType.WALKABLE) && !shape.isEmpty() && shape.max(Direction.Axis.Y) > STEP_UP ? PathType.BLOCKED : type;
        }
    }

    private static AABB stepped(Entity body, Vec3 to) {
        return body.getBoundingBox().move(to.subtract(body.position())).deflate(LYING_SLACK);
    }

    private static Optional<Vec3> aside(Entity body, Vec3 heading, double pace) {
        if (heading.lengthSqr() == 0) {
            return Optional.empty();
        }
        for (double turn : SIDESTEPS) {
            Vec3 way = heading.normalize().yRot((float) Math.toRadians(turn)).scale(pace);
            Vec3 at = body.position().add(way);
            Vec3 to = new Vec3(at.x, footing(body.level(), at, body.getY(), body.getY()), at.z);
            if (Math.abs(to.y - body.getY()) <= STEP_UP && blocker(body, to).isEmpty() && body.level().noCollision(body, stepped(body, to))
                    && Ground.standable(body.level(), to)) {
                return Optional.of(to);
            }
        }
        return Optional.empty();
    }

    private static Optional<Entity> blocker(Entity body, Vec3 to) {
        return body.level().getEntities(body, stepped(body, to), People::someone).stream()
                .filter(other -> flat(to, other) < flat(body.position(), other) && !(coming(other, body) && body.getUUID().compareTo(other.getUUID()) < 0)).findFirst();
    }

    private static boolean taken(Entity body, Vec3 node) {
        return !body.level().getEntities(body, new AABB(node, node.add(0, 1, 0)).inflate(body.getBbWidth() / 2, 0, body.getBbWidth() / 2), People::someone).isEmpty();
    }

    private static boolean someone(Entity other) {
        return who(other).isPresent() || figures.containsKey(other) || other instanceof Player && !other.isSpectator();
    }

    private static double flat(Vec3 at, Entity other) {
        return at.subtract(other.position()).horizontalDistance();
    }

    private static boolean moving(Entity other) {
        return who(other).filter(walking::containsKey).isPresent() || figures.containsKey(other);
    }

    private static boolean coming(Entity walker, Entity body) {
        Vec3 between = body.position().subtract(walker.position()).multiply(1, 0, 1);
        return moving(walker) && between.lengthSqr() > 0
                && Vec3.directionFromRotation(0, walker.getYRot()).dot(between.normalize()) > HEAD_ON;
    }

    private static List<Vec3> way(Entity body, Vec3 to) {
        if (!(body instanceof Mob mob) || !(body.level() instanceof ServerLevel level)) {
            return List.of();
        }
        int range = (int) Math.min(body.position().distanceTo(to) + PLAN_MARGIN, PLAN_MOST);
        BlockPos from = body.blockPosition();
        WalkNodeEvaluator nodes = new Footholds();
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        step(server);
        glow(server);
        for (Map.Entry<String, Long> entry : List.copyOf(leaving.entrySet())) {
            Optional<Entity> body = find(server, entry.getKey());
            if (body.isEmpty()) {
                leaving.remove(entry.getKey());
            } else if (body.get().level().getGameTime() >= entry.getValue() && body.get().level() instanceof ServerLevel level && vanished(level, body.get().position())
                    || Engine.players(server).stream()
                    .noneMatch(player -> player.level() == body.get().level() && player.distanceToSqr(body.get()) <= (double) GONE_BLOCKS * GONE_BLOCKS)) {
                body.get().discard();
                leaving.remove(entry.getKey());
            }
        }
    }

    private static void glow(MinecraftServer server) {
        for (Map.Entry<UUID, Glow> entry : List.copyOf(glowing.entrySet())) {
            Glow glow = entry.getValue();
            Entity body = glow.level().getEntity(entry.getKey());
            BlockPos head = body != null && body.isAlive() ? BlockPos.containing(body.getEyePosition()) : null;
            if (head != null && head.equals(glow.at())) {
                continue;
            }
            unlight(glow);
            if (head == null) {
                glowing.remove(entry.getKey());
                continue;
            }
            boolean lit = glow.level().getBlockState(head).isAir();
            if (lit) {
                glow.level().setBlock(head, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, GLOW_LIGHT), Block.UPDATE_ALL);
            }
            glowing.put(entry.getKey(), new Glow(glow.level(), lit ? head : null));
        }
    }

    private static void unlight(Glow glow) {
        if (glow.at() != null && glow.level().getBlockState(glow.at()).is(Blocks.LIGHT)) {
            glow.level().setBlock(glow.at(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        glowing.values().forEach(People::unlight);
        glowing.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        leaving.clear();
        walking.clear();
        figures.clear();
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

    public static boolean story(Entity body) {
        return body instanceof EasyNPC<?> || body.getTags().contains(TAG);
    }

    public static Optional<String> who(Entity body) {
        return body.getTags().stream().filter(tag -> tag.startsWith(TAG + "_")).map(tag -> tag.substring(TAG.length() + 1)).findFirst();
    }
}
