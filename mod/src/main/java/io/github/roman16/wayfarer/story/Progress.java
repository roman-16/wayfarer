package io.github.roman16.wayfarer.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

public final class Progress extends SavedData {
    private static final String NAME = "wayfarer_story";

    final Map<String, Integer> steps = new LinkedHashMap<>();
    final Map<String, CompoundTag> scratch = new LinkedHashMap<>();
    final Map<String, String> choices = new LinkedHashMap<>();
    final Map<String, List<String>> told = new LinkedHashMap<>();
    final Map<String, List<String>> people = new LinkedHashMap<>();
    final Map<String, List<String>> words = new LinkedHashMap<>();
    final List<String> rewards = new ArrayList<>();
    final Map<String, Set<UUID>> claims = new LinkedHashMap<>();
    final Map<String, BlockPos> places = new LinkedHashMap<>();
    final Map<String, Float> yaws = new LinkedHashMap<>();
    final Map<String, UUID> bodies = new LinkedHashMap<>();
    final Map<String, BoundingBox> wards = new LinkedHashMap<>();
    final Set<String> gifts = new LinkedHashSet<>();
    final Set<String> named = new LinkedHashSet<>();
    final Map<UUID, Set<String>> seen = new LinkedHashMap<>();
    final Map<UUID, String> tracking = new LinkedHashMap<>();
    String stone = "";

    public static Progress of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Progress::new, Progress::load, null), NAME);
    }

    public boolean open(String quest) {
        return steps.containsKey(quest) && !done(quest);
    }

    public boolean done(String quest) {
        return Story.INSTANCE.quest(quest).map(found -> steps.getOrDefault(quest, -1) >= found.steps().size()).orElse(false);
    }

    public int step(String quest) {
        return steps.getOrDefault(quest, -1);
    }

    public CompoundTag scratch(String quest) {
        return scratch.computeIfAbsent(quest, key -> new CompoundTag());
    }

    public Optional<String> choice(String quest, String step) {
        return Optional.ofNullable(choices.get(quest + "/" + step));
    }

    public Optional<BlockPos> place(String name) {
        return Optional.ofNullable(places.get(name));
    }

    public void place(String name, BlockPos pos) {
        places.put(name, pos.immutable());
        setDirty();
    }

    void ward(String name, BoundingBox box) {
        wards.put(name, box);
        setDirty();
    }

    public boolean gift(String gift) {
        return gifts.contains(gift);
    }

    public boolean named(String thing) {
        return named.contains(thing);
    }

    public boolean met(String person) {
        return people.containsKey(person);
    }

    public boolean claimed(String reward, UUID player) {
        return claims.getOrDefault(reward, Set.of()).contains(player);
    }

    private static Progress load(CompoundTag tag, HolderLookup.Provider registries) {
        Progress progress = new Progress();
        CompoundTag steps = tag.getCompound("steps");
        steps.getAllKeys().forEach(key -> progress.steps.put(key, steps.getInt(key)));
        CompoundTag scratch = tag.getCompound("scratch");
        scratch.getAllKeys().forEach(key -> progress.scratch.put(key, scratch.getCompound(key)));
        CompoundTag choices = tag.getCompound("choices");
        choices.getAllKeys().forEach(key -> progress.choices.put(key, choices.getString(key)));
        lines(tag.getList("told", Tag.TAG_COMPOUND), progress.told);
        lines(tag.getList("people", Tag.TAG_COMPOUND), progress.people);
        lines(tag.getList("words", Tag.TAG_COMPOUND), progress.words);
        progress.rewards.addAll(strings(tag.getList("rewards", Tag.TAG_STRING)));
        CompoundTag claims = tag.getCompound("claims");
        claims.getAllKeys().forEach(key -> {
            Set<UUID> players = new LinkedHashSet<>();
            claims.getList(key, Tag.TAG_INT_ARRAY).forEach(entry -> players.add(NbtUtils.loadUUID(entry)));
            progress.claims.put(key, players);
        });
        CompoundTag places = tag.getCompound("places");
        places.getAllKeys().forEach(key -> progress.places.put(key, BlockPos.of(places.getLong(key))));
        CompoundTag yaws = tag.getCompound("yaws");
        yaws.getAllKeys().forEach(key -> progress.yaws.put(key, yaws.getFloat(key)));
        CompoundTag bodies = tag.getCompound("bodies");
        bodies.getAllKeys().forEach(key -> progress.bodies.put(key, bodies.getUUID(key)));
        CompoundTag wards = tag.getCompound("wards");
        wards.getAllKeys().forEach(key -> {
            int[] box = wards.getIntArray(key);
            progress.wards.put(key, new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
        });
        progress.gifts.addAll(strings(tag.getList("gifts", Tag.TAG_STRING)));
        progress.named.addAll(strings(tag.getList("named", Tag.TAG_STRING)));
        progress.stone = tag.getString("stone");
        CompoundTag seen = tag.getCompound("seen");
        seen.getAllKeys().forEach(key -> progress.seen.put(UUID.fromString(key), new LinkedHashSet<>(strings(seen.getList(key, Tag.TAG_STRING)))));
        CompoundTag tracking = tag.getCompound("tracking");
        tracking.getAllKeys().forEach(key -> progress.tracking.put(UUID.fromString(key), tracking.getString(key)));
        return progress;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag steps = new CompoundTag();
        this.steps.forEach(steps::putInt);
        tag.put("steps", steps);
        CompoundTag scratch = new CompoundTag();
        this.scratch.forEach(scratch::put);
        tag.put("scratch", scratch);
        CompoundTag choices = new CompoundTag();
        this.choices.forEach(choices::putString);
        tag.put("choices", choices);
        tag.put("told", lines(told));
        tag.put("people", lines(people));
        tag.put("words", lines(words));
        tag.put("rewards", list(rewards));
        CompoundTag claims = new CompoundTag();
        this.claims.forEach((reward, players) -> {
            ListTag list = new ListTag();
            players.forEach(player -> list.add(NbtUtils.createUUID(player)));
            claims.put(reward, list);
        });
        tag.put("claims", claims);
        CompoundTag places = new CompoundTag();
        this.places.forEach((name, pos) -> places.putLong(name, pos.asLong()));
        tag.put("places", places);
        CompoundTag yaws = new CompoundTag();
        this.yaws.forEach(yaws::putFloat);
        tag.put("yaws", yaws);
        CompoundTag bodies = new CompoundTag();
        this.bodies.forEach(bodies::putUUID);
        tag.put("bodies", bodies);
        CompoundTag wards = new CompoundTag();
        this.wards.forEach((name, box) -> wards.putIntArray(name, new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()}));
        tag.put("wards", wards);
        tag.put("gifts", list(List.copyOf(gifts)));
        tag.put("named", list(List.copyOf(named)));
        CompoundTag seen = new CompoundTag();
        this.seen.forEach((player, keys) -> seen.put(player.toString(), list(List.copyOf(keys))));
        tag.put("seen", seen);
        CompoundTag tracking = new CompoundTag();
        this.tracking.forEach((player, quest) -> tracking.putString(player.toString(), quest));
        tag.put("tracking", tracking);
        tag.putString("stone", stone);
        return tag;
    }

    private static List<String> strings(ListTag list) {
        return list.stream().map(Tag::getAsString).toList();
    }

    private static ListTag list(List<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    private static void lines(ListTag list, Map<String, List<String>> into) {
        for (Tag entry : list) {
            CompoundTag lines = (CompoundTag) entry;
            into.put(lines.getString("key"), new ArrayList<>(strings(lines.getList("lines", Tag.TAG_STRING))));
        }
    }

    private static ListTag lines(Map<String, List<String>> from) {
        ListTag list = new ListTag();
        from.forEach((key, values) -> {
            CompoundTag lines = new CompoundTag();
            lines.putString("key", key);
            lines.put("lines", list(values));
            list.add(lines);
        });
        return list;
    }
}
