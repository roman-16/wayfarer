package io.github.roman16.wayfarer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public class Journey extends SavedData {
    private static final String NAME = "wayfarer_journey";
    private static final String CLAIMS = "claims";
    private static final String SLAYERS = "slayers";

    private final Map<UUID, Set<ResourceLocation>> claims = new HashMap<>();
    private final Set<UUID> slayers = new HashSet<>();

    public static Journey of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Journey::new, Journey::load, null), NAME);
    }

    public static Set<ResourceLocation> claimed(ServerPlayer player) {
        return Set.copyOf(of(player.server).claims.getOrDefault(Teams.id(player), Set.of()));
    }

    public static boolean slain(ServerPlayer player) {
        return of(player.server).slayers.contains(Teams.id(player));
    }

    public boolean claim(UUID team, ResourceLocation shrine) {
        boolean added = claims.computeIfAbsent(team, key -> new HashSet<>()).add(shrine);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean slay(UUID team) {
        boolean added = slayers.add(team);
        if (added) {
            setDirty();
        }
        return added;
    }

    private static Journey load(CompoundTag tag, HolderLookup.Provider registries) {
        Journey journey = new Journey();
        CompoundTag claims = tag.getCompound(CLAIMS);
        for (String team : claims.getAllKeys()) {
            Set<ResourceLocation> shrines = new HashSet<>();
            for (Tag shrine : claims.getList(team, Tag.TAG_STRING)) {
                shrines.add(ResourceLocation.parse(shrine.getAsString()));
            }
            journey.claims.put(UUID.fromString(team), shrines);
        }
        for (Tag team : tag.getList(SLAYERS, Tag.TAG_STRING)) {
            journey.slayers.add(UUID.fromString(team.getAsString()));
        }
        return journey;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag claimsTag = new CompoundTag();
        claims.forEach((team, shrines) -> {
            ListTag list = new ListTag();
            shrines.stream().map(ResourceLocation::toString).sorted().map(StringTag::valueOf).forEach(list::add);
            claimsTag.put(team.toString(), list);
        });
        tag.put(CLAIMS, claimsTag);
        ListTag slayersTag = new ListTag();
        slayers.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(slayersTag::add);
        tag.put(SLAYERS, slayersTag);
        return tag;
    }
}
