package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class Journey extends SavedData {
    private static final String NAME = "wayfarer_journey";
    private static final String AWAKE = "awake";
    private static final String CLAIMS = "claims";
    private static final String SLAIN = "slain";

    private final List<ResourceLocation> claims = new ArrayList<>();
    private boolean awake;
    private boolean slain;

    public static Journey of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Journey::new, Journey::load, null), NAME);
    }

    public static List<ResourceLocation> claimed(MinecraftServer server) {
        return List.copyOf(of(server).claims);
    }

    public static boolean awake(MinecraftServer server) {
        return of(server).awake;
    }

    public static boolean slain(MinecraftServer server) {
        return of(server).slain;
    }

    public boolean claim(ResourceLocation shrine) {
        if (claims.contains(shrine)) {
            return false;
        }
        claims.add(shrine);
        setDirty();
        return true;
    }

    public boolean wake() {
        if (awake) {
            return false;
        }
        awake = true;
        setDirty();
        return true;
    }

    public boolean slay() {
        if (slain) {
            return false;
        }
        slain = true;
        setDirty();
        return true;
    }

    private static Journey load(CompoundTag tag, HolderLookup.Provider registries) {
        Journey journey = new Journey();
        tag.getList(CLAIMS, Tag.TAG_STRING).forEach(shrine -> journey.claims.add(ResourceLocation.parse(shrine.getAsString())));
        journey.awake = tag.getBoolean(AWAKE);
        journey.slain = tag.getBoolean(SLAIN);
        return journey;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag claimed = new ListTag();
        claims.stream().map(ResourceLocation::toString).map(StringTag::valueOf).forEach(claimed::add);
        tag.put(CLAIMS, claimed);
        tag.putBoolean(AWAKE, awake);
        tag.putBoolean(SLAIN, slain);
        return tag;
    }
}
