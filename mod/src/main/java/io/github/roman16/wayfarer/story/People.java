package io.github.roman16.wayfarer.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import io.github.roman16.wayfarer.Wayfarer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class People {
    public static final String TAG = Wayfarer.MODID + "_person";
    private static final String STANDING = "standing";
    private static final JsonObject SKINS = skins();

    private People() {}

    public static Entity spawn(ServerLevel level, String skin, Vec3 at, float yaw, String pose, String... tags) {
        JsonObject look = SKINS.getAsJsonObject(skin);
        if (look == null) {
            throw new IllegalArgumentException("generator/skins.toml has no skin " + skin);
        }
        Entity body = EntityType.byString(look.get("model").getAsString()).map(type -> type.create(level))
                .orElseThrow(() -> new IllegalStateException(look.get("model").getAsString() + " is not in this game"));
        body.moveTo(at.x, at.y, at.z, yaw, 0);
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

    public static Entity person(ServerLevel level, String person, Vec3 at, float yaw, String pose) {
        Progress progress = Progress.of(level.getServer());
        find(level.getServer(), person).ifPresent(Entity::discard);
        Entity body = spawn(level, person, at, yaw, pose, TAG + "_" + person);
        body.setCustomName(Component.literal(Story.INSTANCE.person(person).name()));
        name(body, NameVisibilityType.MOUSE_OVER);
        progress.bodies.put(person, body.getUUID());
        progress.setDirty();
        return body;
    }

    public static void pose(Entity body, String pose) {
        if (body instanceof EasyNPC<?> npc) {
            if (pose.equals(STANDING)) {
                ModelPoseAPI.resetPose(npc);
            } else {
                ModelPoseAPI.setPose(npc, ResourceLocation.fromNamespaceAndPath("easy_npc", "pose/humanoid/" + pose));
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

    private static JsonObject skins() {
        try (var stream = People.class.getResourceAsStream("/wayfarer/skins.json")) {
            if (stream == null) {
                throw new IllegalStateException("skins.json is missing from the mod jar; run just generate");
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
