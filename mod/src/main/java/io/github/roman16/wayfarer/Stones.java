package io.github.roman16.wayfarer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class Stones extends SavedData {
    private static final String DATA = Wayfarer.MODID + "_stones";
    final Map<UUID, Stone> all = new TreeMap<>();

    public record Stone(UUID player, ResourceKey<Level> dimension, BlockPos pos, int things) {}

    public static Stones of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Stones::new, Stones::load, null), DATA);
    }

    public static List<Stone> mine(ServerPlayer player) {
        return of(player.server).all.values().stream().filter(stone -> stone.player().equals(player.getUUID()))
                .sorted(Comparator.comparingInt(Stone::things).reversed()).toList();
    }

    private static Stones load(CompoundTag tag, HolderLookup.Provider registries) {
        Stones stones = new Stones();
        for (String key : tag.getAllKeys()) {
            CompoundTag entry = tag.getCompound(key);
            stones.all.put(UUID.fromString(key), new Stone(entry.getUUID("player"),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("dimension"))),
                    BlockPos.of(entry.getLong("pos")), entry.getInt("things")));
        }
        return stones;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        all.forEach((id, stone) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", stone.player());
            entry.putString("dimension", stone.dimension().location().toString());
            entry.putLong("pos", stone.pos().asLong());
            entry.putInt("things", stone.things());
            tag.put(id.toString(), entry);
        });
        return tag;
    }
}
