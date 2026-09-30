package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final String AWAKE = "awake";
    private static final String CLAIMS = "claims";
    private static final String SLAYERS = "slayers";

    private final Set<UUID> awake = new HashSet<>();
    private final Map<UUID, List<ResourceLocation>> claims = new HashMap<>();
    private final Set<UUID> slayers = new HashSet<>();

    public static Journey of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Journey::new, Journey::load, null), NAME);
    }

    public static List<ResourceLocation> claimed(ServerPlayer player) {
        return List.copyOf(of(player.server).claims.getOrDefault(Teams.id(player), List.of()));
    }

    public static boolean awake(ServerPlayer player) {
        return of(player.server).awake.contains(Teams.id(player));
    }

    public static boolean slain(ServerPlayer player) {
        return of(player.server).slayers.contains(Teams.id(player));
    }

    public boolean claim(UUID team, ResourceLocation shrine) {
        List<ResourceLocation> shrines = claims.computeIfAbsent(team, key -> new ArrayList<>());
        if (shrines.contains(shrine)) {
            return false;
        }
        shrines.add(shrine);
        setDirty();
        return true;
    }

    public boolean wake(UUID team) {
        boolean added = awake.add(team);
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
            List<ResourceLocation> shrines = new ArrayList<>();
            for (Tag shrine : claims.getList(team, Tag.TAG_STRING)) {
                shrines.add(ResourceLocation.parse(shrine.getAsString()));
            }
            journey.claims.put(UUID.fromString(team), shrines);
        }
        for (Tag team : tag.getList(AWAKE, Tag.TAG_STRING)) {
            journey.awake.add(UUID.fromString(team.getAsString()));
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
            shrines.stream().map(ResourceLocation::toString).map(StringTag::valueOf).forEach(list::add);
            claimsTag.put(team.toString(), list);
        });
        tag.put(CLAIMS, claimsTag);
        tag.put(AWAKE, teams(awake));
        tag.put(SLAYERS, teams(slayers));
        return tag;
    }

    private static ListTag teams(Set<UUID> teams) {
        ListTag list = new ListTag();
        teams.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(list::add);
        return list;
    }
}
