package io.github.roman16.wayfarer.spike;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.roman16.wayfarer.Wayfarer;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Wayfarer.MODID)
public final class EmberSpike {
    private static final int AHEAD = 16;
    private static final int AMBER = 0xFFB040;
    private static final String CLUE = Wayfarer.MODID + "_clue";
    private static final int CLUES_NEEDED = 2;
    private static final Field FLAGS = field();
    private static final int GAMEMASTER = 2;
    private static final int GLOWING = 1 << 6;
    private static final int MARKS = 8;
    private static final int PASSED = 8;
    private static final int SENSE_BLOCKS = 24;
    private static final int SENSE_TICKS = 3 * 20;
    private static final int SPACING = 4;
    private static final int STRIDE = 1;
    private static final String TRACK = Wayfarer.MODID + "_track";
    private static final Map<UUID, Sensing> sensing = new HashMap<>();
    private static final List<String> found = new ArrayList<>();
    private static final List<UUID> trail = new ArrayList<>();
    private static Vec3 trailEnd = Vec3.ZERO;

    private EmberSpike() {}

    public record Sense() implements CustomPacketPayload {
        public static final Type<Sense> TYPE = new Type<>(Wayfarer.id("spike_ember_sense"));
        public static final StreamCodec<ByteBuf, Sense> STREAM_CODEC = StreamCodec.unit(new Sense());

        @Override
        public Type<Sense> type() {
            return TYPE;
        }
    }

    private record Sensing(List<Entity> glowing, long until) {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Wayfarer.MODID)
                .then(Commands.literal("track")
                        .requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("clue").executes(context -> clue(context.getSource())))
                        .then(Commands.literal("lay").executes(context -> lay(context.getSource())))));
    }

    private static int lay(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 way = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-way.z, 0, way.x);
        for (int mark = 0; mark < MARKS; mark++) {
            double along = AHEAD + mark * SPACING;
            double bend = Math.sin(mark * 0.6) * 1.5;
            Vec3 at = player.position().add(way.scale(along)).add(side.scale(bend));
            for (int foot = -1; foot <= 1; foot += 2) {
                Vec3 print = at.add(side.scale(0.3 * foot)).add(way.scale(foot * 0.4 * STRIDE));
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(print));
                Entity entity = display(level, "minecraft:block_display", new Vec3(print.x, ground.getY() + 0.01, print.z), player.getYRot(),
                        tag -> {
                            CompoundTag state = new CompoundTag();
                            state.putString("Name", "minecraft:coarse_dirt");
                            tag.put("block_state", state);
                            tag.put("transformation", transformation(0.25f, 0.02f, 0.45f));
                        });
                entity.addTag(TRACK);
                trail.add(entity.getUUID());
            }
            trailEnd = at;
        }
        say(level.getServer(), "track: %d prints laid from %d to %d blocks ahead, bending with the land".formatted(trail.size(), AHEAD,
                AHEAD + (MARKS - 1) * SPACING));
        return 1;
    }

    private static int clue(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(3));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        Vec3 on = new Vec3(at.x, ground.getY(), at.z);
        Entity item = display(level, "minecraft:item_display", on.add(0, 0.05, 0), 0, tag -> {
            CompoundTag stack = new CompoundTag();
            stack.putString("id", "minecraft:leather");
            stack.putInt("count", 1);
            tag.put("item", stack);
            tag.put("transformation", transformation(0.5f, 0.5f, 0.5f));
        });
        item.addTag(CLUE);
        Entity box = EntityType.INTERACTION.create(level);
        box.moveTo(on.x, on.y, on.z);
        box.addTag(CLUE);
        level.addFreshEntity(box);
        say(level.getServer(), "track: a clue lies at %d %d %d".formatted(ground.getX(), ground.getY(), ground.getZ()));
        return 1;
    }

    private static Entity display(ServerLevel level, String id, Vec3 at, float yaw, Consumer<CompoundTag> shape) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("glow_color_override", AMBER);
        shape.accept(tag);
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> {
            loaded.moveTo(at.x, at.y, at.z, yaw, 0);
            return loaded;
        });
        level.addFreshEntity(entity);
        return entity;
    }

    private static CompoundTag transformation(float x, float y, float z) {
        CompoundTag tag = new CompoundTag();
        tag.put("left_rotation", floats(0, 0, 0, 1));
        tag.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("scale", floats(x, y, z));
        tag.put("translation", floats(-x / 2, 0, -z / 2));
        return tag;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    public static void sense(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Entity> near = level.getEntities((Entity) null, new AABB(player.blockPosition()).inflate(SENSE_BLOCKS),
                entity -> entity.getTags().contains(TRACK) || entity.getTags().contains(CLUE) && !(entity instanceof Interaction));
        for (Entity entity : near) {
            glow(player, entity, true);
        }
        sensing.put(player.getUUID(), new Sensing(near, level.getGameTime() + SENSE_TICKS));
        long clues = near.stream().filter(entity -> entity.getTags().contains(CLUE)).count();
        say(player.server, "ember: %s holds up the ember; %d prints and %d clues glow for %d s, for them alone".formatted(player.getScoreboardName(),
                near.size() - clues, clues, SENSE_TICKS / 20));
    }

    @SuppressWarnings("unchecked")
    private static void glow(ServerPlayer player, Entity entity, boolean on) {
        try {
            EntityDataAccessor<Byte> flags = (EntityDataAccessor<Byte>) FLAGS.get(null);
            byte real = entity.getEntityData().get(flags);
            byte shown = (byte) (on ? real | GLOWING : real);
            player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), List.of(SynchedEntityData.DataValue.create(flags, shown))));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Field field() {
        try {
            Field field = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getTarget().getTags().contains(CLUE)) {
            return;
        }
        event.setCanceled(true);
        String line = "A scrap of hide, cut clean. Someone dressed a kill here and walked north-east.";
        if (found.contains(line)) {
            return;
        }
        found.add(line);
        say(player.server, "journal: %s (%d of %d clues)%s".formatted(line, found.size(), CLUES_NEEDED,
                found.size() >= CLUES_NEEDED ? "; the step is done" : ""));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Sensing> entry : List.copyOf(sensing.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.serverLevel().getGameTime() >= entry.getValue().until) {
                sensing.remove(entry.getKey());
                if (player != null) {
                    entry.getValue().glowing.forEach(entity -> glow(player, entity, false));
                    say(server, "ember: the glow fades for " + player.getScoreboardName());
                }
            }
        }
        if (trail.isEmpty() || server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        boolean passed = !level.players().isEmpty() && level.players().stream()
                .allMatch(player -> player.position().subtract(trailEnd).horizontalDistance() < PASSED);
        if (passed) {
            int removed = 0;
            for (UUID id : trail) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                    removed++;
                }
            }
            trail.clear();
            say(server, "track: the trail's end is reached; %d prints taken away".formatted(removed));
        }
    }

    private static void say(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    @EventBusSubscriber(modid = Wayfarer.MODID, value = Dist.CLIENT)
    public static final class Key {
        private static final KeyMapping SENSE = new KeyMapping("key.wayfarer.ember_sense", GLFW.GLFW_KEY_F8, "key.categories.misc");

        private Key() {}

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(SENSE);
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            while (SENSE.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    PacketDistributor.sendToServer(new Sense());
                }
            }
        }
    }
}
