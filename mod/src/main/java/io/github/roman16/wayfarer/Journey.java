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
    private static final String OVER = "over";

    private final List<ResourceLocation> claims = new ArrayList<>();
    private boolean awake;
    private boolean over;

    public static Journey of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Journey::new, Journey::load, null), NAME);
    }

    public static List<ResourceLocation> claimed(MinecraftServer server) {
        return List.copyOf(of(server).claims);
    }

    public static boolean awake(MinecraftServer server) {
        return of(server).awake;
    }

    public static boolean over(MinecraftServer server) {
        return of(server).over;
    }

    public boolean claim(ResourceLocation hall) {
        if (claims.contains(hall)) {
            return false;
        }
        claims.add(hall);
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

    public boolean end() {
        if (over) {
            return false;
        }
        over = true;
        setDirty();
        return true;
    }

    private static Journey load(CompoundTag tag, HolderLookup.Provider registries) {
        Journey journey = new Journey();
        tag.getList(CLAIMS, Tag.TAG_STRING).forEach(hall -> journey.claims.add(ResourceLocation.parse(hall.getAsString())));
        journey.awake = tag.getBoolean(AWAKE);
        journey.over = tag.getBoolean(OVER);
        return journey;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag claimed = new ListTag();
        claims.stream().map(ResourceLocation::toString).map(StringTag::valueOf).forEach(claimed::add);
        tag.put(CLAIMS, claimed);
        tag.putBoolean(AWAKE, awake);
        tag.putBoolean(OVER, over);
        return tag;
    }
}
