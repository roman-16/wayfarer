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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final Map<String, Vec3> walking = new HashMap<>();
    private static final double STRIDE = 0.12;
    private static final int CLIMB = 2;
    private static final int GROUND_BELOW = 16;
    private static final int AWAY_BLOCKS = 32;
    private static final int GONE_BLOCKS = 48;
    private static final int LEAVE_SECONDS = 64;
    private static final int TICKS_PER_SECOND = 20;
    private static final double ARROW_SHORT = 2;
    private static final double ARROW_DROP = 0.1;
    private static final double ARROW_LIFT = 0.2;
    private static final float ARROW_SPEED = 1.6f;

    private People() {}

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

    private static Vec3 surface(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int drop = 0; drop <= GROUND_BELOW; drop++) {
            BlockPos below = pos.below(drop);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
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

    static void walk(MinecraftServer server, String person, Vec3 to) {
        find(server, person).ifPresent(body -> pose(body, STANDING));
        walking.put(person, to);
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
            walk(server, person, body.position().add((away.lengthSqr() < 1 ? new Vec3(1, 0, 0) : away.normalize()).scale(AWAY_BLOCKS)));
            leaving.put(person, body.level().getGameTime() + (long) LEAVE_SECONDS * TICKS_PER_SECOND);
        });
    }

    private static void step(MinecraftServer server) {
        for (Map.Entry<String, Vec3> entry : List.copyOf(walking.entrySet())) {
            Optional<Entity> found = find(server, entry.getKey());
            if (found.isEmpty()) {
                walking.remove(entry.getKey());
                continue;
            }
            Entity body = found.get();
            Vec3 left = entry.getValue().subtract(body.position()).multiply(1, 0, 1);
            if (left.length() <= STRIDE) {
                walking.remove(entry.getKey());
                continue;
            }
            Vec3 next = body.position().add(left.normalize().scale(STRIDE));
            int floor = floor(body.level(), BlockPos.containing(next), body.getY());
            float yaw = (float) Math.toDegrees(Math.atan2(-left.x, left.z));
            body.moveTo(next.x, floor, next.z, yaw, 0);
            body.setYHeadRot(yaw);
            body.setYBodyRot(yaw);
        }
    }

    private static int floor(Level level, BlockPos at, double from) {
        int top = (int) Math.floor(from);
        for (int y = top + CLIMB; y >= top - CLIMB * 2; y--) {
            BlockPos feet = new BlockPos(at.getX(), y, at.getZ());
            if (level.getBlockState(feet).canBeReplaced() && level.getFluidState(feet).isEmpty() && level.getBlockState(feet.above()).canBeReplaced()
                    && !level.getBlockState(feet.below()).canBeReplaced()) {
                return y;
            }
        }
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
        while (feet.getY() > level.getMinBuildHeight() && (!level.getFluidState(feet.below()).isEmpty() || level.getBlockState(feet.below()).canBeReplaced())) {
            feet.move(0, -1, 0);
        }
        return feet.getY();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        step(server);
        for (Map.Entry<String, Long> entry : List.copyOf(leaving.entrySet())) {
            Optional<Entity> body = find(server, entry.getKey());
            if (body.isEmpty()) {
                leaving.remove(entry.getKey());
            } else if (body.get().level().getGameTime() >= entry.getValue() || Engine.players(server).stream()
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
