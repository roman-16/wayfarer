package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Joining {
    private static final int CHECK_TICKS = 20;
    private static final String DATA = Wayfarer.MODID + "_joining";
    private static final int HEIGHT = 4;
    private static final String NOMADIC_TENTS = "nomadictents";
    private static final int[] RINGS = {2, 3, 4};
    private static final int STEPS = 8;
    private static final Map<UUID, Long> since = new HashMap<>();
    private static final Map<UUID, Place> outside = new HashMap<>();
    private static long joins;

    private Joining() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        State state = State.of(server);
        boolean known = state.known.contains(player.getUUID());
        ServerPlayer anchor = server.getPlayerList().getPlayers().stream().filter(other -> other != player)
                .min(Comparator.comparingLong(other -> since.getOrDefault(other.getUUID(), Long.MAX_VALUE))).orElse(null);
        since.put(player.getUUID(), joins++);
        Place at = null;
        int away = Balance.INSTANCE.join().awayBlocks();
        if (anchor != null) {
            Place beside = inTent(anchor) ? outside.getOrDefault(anchor.getUUID(), Place.of(anchor)) : Place.of(anchor);
            boolean far = !player.level().dimension().equals(beside.dimension) || player.blockPosition().distSqr(beside.pos) > (double) away * away;
            if (!known || far) {
                at = ground(server, beside);
            }
        } else if (!known && state.left != null) {
            at = ground(server, state.left);
        }
        if (at != null) {
            player.teleportTo(server.getLevel(at.dimension), at.pos.getX() + 0.5, at.pos.getY(), at.pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            outside.put(player.getUUID(), at);
        } else if (!known) {
            player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), player.serverLevel().getSharedSpawnAngle(), 0);
        }
        state.known.add(player.getUUID());
        state.setDirty();
        if (ModList.get().isLoaded(NOMADIC_TENTS)) {
            TentPitch.recall(player, state.tents);
        }
    }

    public static void leave(MinecraftServer server, Place place) {
        State state = State.of(server);
        state.left = place;
        state.setDirty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            since.remove(player.getUUID());
            State state = State.of(player.server);
            state.left = inTent(player) ? outside.getOrDefault(player.getUUID(), Place.of(player)) : Place.of(player);
            state.setDirty();
            outside.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        boolean tents = ModList.get().isLoaded(NOMADIC_TENTS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!inTent(player)) {
                outside.put(player.getUUID(), Place.of(player));
                if (tents) {
                    TentPitch.track(player, State.of(server));
                }
            }
        }
    }

    private static boolean inTent(ServerPlayer player) {
        return player.level().dimension().location().getNamespace().equals(NOMADIC_TENTS);
    }

    private static Place ground(MinecraftServer server, Place near) {
        ServerLevel level = server.getLevel(near.dimension);
        for (int ring : RINGS) {
            for (int step = 0; step < STEPS; step++) {
                double angle = Math.PI * 2 * step / STEPS;
                int x = near.pos.getX() + (int) Math.round(Math.cos(angle) * ring);
                int z = near.pos.getZ() + (int) Math.round(Math.sin(angle) * ring);
                for (int dy = HEIGHT; dy >= -HEIGHT; dy--) {
                    BlockPos feet = new BlockPos(x, near.pos.getY() + dy, z);
                    if (safe(level, feet)) {
                        return new Place(near.dimension, feet);
                    }
                }
            }
        }
        return near;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && level.getBlockStates(new AABB(feet).inflate(1)).noneMatch(state -> state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA));
    }

    public record Place(ResourceKey<Level> dimension, BlockPos pos) {
        public static Place of(ServerPlayer player) {
            return new Place(player.level().dimension(), player.blockPosition());
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension.location().toString());
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            return tag;
        }

        static Place load(CompoundTag tag) {
            return new Place(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
                    NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO));
        }
    }

    public record Pitched(UUID owner, Place place, int id) {}

    public static final class State extends SavedData {
        private final Set<UUID> known = new HashSet<>();
        public final List<Pitched> tents = new ArrayList<>();
        @Nullable
        private Place left;

        static State of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(State::new, State::load, null), DATA);
        }

        private static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            tag.getList("known", Tag.TAG_STRING).forEach(entry -> state.known.add(UUID.fromString(entry.getAsString())));
            for (Tag entry : tag.getList("tents", Tag.TAG_COMPOUND)) {
                CompoundTag tent = (CompoundTag) entry;
                state.tents.add(new Pitched(tent.getUUID("owner"), Place.load(tent.getCompound("place")), tent.getInt("id")));
            }
            if (tag.contains("left")) {
                state.left = Place.load(tag.getCompound("left"));
            }
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag known = new ListTag();
            this.known.forEach(id -> known.add(StringTag.valueOf(id.toString())));
            tag.put("known", known);
            ListTag tents = new ListTag();
            for (Pitched pitched : this.tents) {
                CompoundTag tent = new CompoundTag();
                tent.putUUID("owner", pitched.owner);
                tent.put("place", pitched.place.save());
                tent.putInt("id", pitched.id);
                tents.add(tent);
            }
            tag.put("tents", tents);
            if (left != null) {
                tag.put("left", left.save());
            }
            return tag;
        }
    }
}
