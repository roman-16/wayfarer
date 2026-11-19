package io.github.roman16.wayfarer.story;

import io.github.roman16.wayfarer.Climate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

public final class Progress extends SavedData {
    private static final String DONE = "done";
    static final String NAME = "wayfarer_story";

    final Map<String, Integer> steps = new LinkedHashMap<>();
    final Map<String, CompoundTag> scratch = new LinkedHashMap<>();
    final Map<String, String> choices = new LinkedHashMap<>();
    final Map<String, List<String>> told = new LinkedHashMap<>();
    final Map<String, List<Heard>> conversations = new LinkedHashMap<>();
    final Set<String> pages = new LinkedHashSet<>();
    final Set<String> gear = new LinkedHashSet<>();
    final Map<String, Long> gearMet = new LinkedHashMap<>();
    final Map<UUID, String> gearTracking = new LinkedHashMap<>();
    long gearSpoke = -1;
    final Map<String, List<String>> people = new LinkedHashMap<>();
    final Map<String, List<String>> words = new LinkedHashMap<>();
    final List<String> rewards = new ArrayList<>();
    final Map<String, Set<UUID>> claims = new LinkedHashMap<>();
    final Map<String, BlockPos> places = new LinkedHashMap<>();
    final Map<String, Float> yaws = new LinkedHashMap<>();
    final Map<String, UUID> bodies = new LinkedHashMap<>();
    final Map<String, BoundingBox> wards = new LinkedHashMap<>();
    final Map<String, List<BoundingBox>> rooms = new LinkedHashMap<>();
    final Map<String, List<BlockPos>> refused = new LinkedHashMap<>();
    final Map<String, Standing.Witness> witnesses = new LinkedHashMap<>();
    final Set<String> gifts = new LinkedHashSet<>();
    final List<String> carried = new ArrayList<>();
    final Set<Climate> crossed = new LinkedHashSet<>();
    long crossedDay;
    final Set<String> held = new LinkedHashSet<>();
    final Set<String> joined = new LinkedHashSet<>();
    final Set<Long> lit = new LinkedHashSet<>();
    final Set<String> named = new LinkedHashSet<>();
    final Set<String> unlaid = new LinkedHashSet<>();
    final Set<String> visited = new LinkedHashSet<>();
    final Map<UUID, Set<String>> seen = new LinkedHashMap<>();
    final Map<UUID, String> tracking = new LinkedHashMap<>();
    final Map<String, Integer> trust = new LinkedHashMap<>();
    String stone = "";

    record Heard(String by, String text) {}

    void hear(String key, String by, String text) {
        conversations.computeIfAbsent(key, any -> new ArrayList<>()).add(new Heard(by, text));
        setDirty();
    }

    boolean read(String book) {
        boolean first = pages.add(book);
        if (first) {
            setDirty();
        }
        return first;
    }

    public static Progress of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Progress::new, Progress::load, null), NAME);
    }

    public boolean open(String quest) {
        return steps.containsKey(quest) && !done(quest);
    }

    public boolean done(String quest) {
        return Story.get().quest(quest).map(found -> steps.getOrDefault(quest, -1) >= found.steps().size()).orElse(false);
    }

    public boolean past(String quest, String step) {
        return done(quest) || open(quest) && Story.get().quest(quest)
                .map(found -> step(quest) > found.steps().stream().map(Story.Step::id).toList().indexOf(step)).orElse(false);
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

    void refuse(String name, BlockPos site) {
        refused.computeIfAbsent(name, key -> new ArrayList<>()).add(site.immutable());
        setDirty();
    }

    List<BlockPos> refused(String name) {
        return refused.getOrDefault(name, List.of());
    }

    boolean clearOfRefused(String name, BlockPos at, int apart) {
        return refused(name).stream().noneMatch(site -> site.distSqr(at.atY(site.getY())) <= (double) apart * apart);
    }

    private static BoundingBox box(int[] corners) {
        return new BoundingBox(corners[0], corners[1], corners[2], corners[3], corners[4], corners[5]);
    }

    private static int[] corners(BoundingBox box) {
        return new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
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

    public int trust(String people) {
        return trust.getOrDefault(people, 0);
    }

    void trust(Map<String, Integer> levels) {
        levels.forEach((people, level) -> {
            if (trust(people) >= 0) {
                trust.put(people, level);
                setDirty();
            }
        });
    }

    void shun(String people) {
        trust.put(people, -1);
        setDirty();
    }

    public boolean claimed(String reward, UUID player) {
        return claims.getOrDefault(reward, Set.of()).contains(player);
    }

    private static int index(Story.Quest quest, String step) {
        if (step.equals(DONE)) {
            return quest.steps().size();
        }
        for (int index = 0; index < quest.steps().size(); index++) {
            if (quest.steps().get(index).id().equals(step)) {
                return index;
            }
        }
        throw new IllegalStateException("the save stands at " + quest.id() + "/" + step + ", which the story no longer has");
    }

    static Progress load(CompoundTag tag, HolderLookup.Provider registries) {
        Progress progress = new Progress();
        CompoundTag steps = tag.getCompound("steps");
        steps.getAllKeys().forEach(key -> Story.get().quest(key).ifPresent(quest -> progress.steps.put(key, index(quest, steps.getString(key)))));
        CompoundTag scratch = tag.getCompound("scratch");
        scratch.getAllKeys().forEach(key -> progress.scratch.put(key, scratch.getCompound(key)));
        CompoundTag choices = tag.getCompound("choices");
        choices.getAllKeys().forEach(key -> progress.choices.put(key, choices.getString(key)));
        lines(tag.getList("told", Tag.TAG_COMPOUND), progress.told);
        for (Tag entry : tag.getList("conversations", Tag.TAG_COMPOUND)) {
            CompoundTag talk = (CompoundTag) entry;
            List<Heard> heard = new ArrayList<>();
            for (Tag line : talk.getList("lines", Tag.TAG_COMPOUND)) {
                heard.add(new Heard(((CompoundTag) line).getString("by"), ((CompoundTag) line).getString("text")));
            }
            progress.conversations.put(talk.getString("key"), heard);
        }
        progress.pages.addAll(strings(tag.getList("pages", Tag.TAG_STRING)));
        progress.gear.addAll(strings(tag.getList("gear", Tag.TAG_STRING)));
        progress.gearSpoke = tag.contains("gearSpoke") ? tag.getLong("gearSpoke") : -1;
        CompoundTag met = tag.getCompound("gearMet");
        met.getAllKeys().forEach(key -> progress.gearMet.put(key, met.getLong(key)));
        CompoundTag gearTracking = tag.getCompound("gearTracking");
        gearTracking.getAllKeys().forEach(key -> progress.gearTracking.put(UUID.fromString(key), gearTracking.getString(key)));
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
        wards.getAllKeys().forEach(key -> progress.wards.put(key, box(wards.getIntArray(key))));
        CompoundTag rooms = tag.getCompound("rooms");
        rooms.getAllKeys().forEach(key -> progress.rooms.put(key, rooms.getList(key, Tag.TAG_INT_ARRAY).stream().map(room -> box(((IntArrayTag) room).getAsIntArray()))
                .toList()));
        CompoundTag refused = tag.getCompound("refused");
        refused.getAllKeys().forEach(key -> progress.refused.put(key, Arrays.stream(refused.getLongArray(key)).mapToObj(BlockPos::of)
                .collect(Collectors.toCollection(ArrayList::new))));
        CompoundTag witnesses = tag.getCompound("witnesses");
        witnesses.getAllKeys().forEach(key -> {
            CompoundTag witness = witnesses.getCompound(key);
            progress.witnesses.put(key, new Standing.Witness(BlockPos.of(witness.getLong("centre")), strings(witness.getList("blocks", Tag.TAG_STRING))));
        });
        progress.gifts.addAll(strings(tag.getList("gifts", Tag.TAG_STRING)));
        strings(tag.getList("crossed", Tag.TAG_STRING)).forEach(climate -> progress.crossed.add(Climate.valueOf(climate)));
        progress.crossedDay = tag.getLong("crossed_day");
        progress.carried.addAll(strings(tag.getList("carried", Tag.TAG_STRING)));
        progress.held.addAll(strings(tag.getList("held", Tag.TAG_STRING)));
        progress.joined.addAll(strings(tag.getList("joined", Tag.TAG_STRING)));
        Arrays.stream(tag.getLongArray("lit")).forEach(progress.lit::add);
        progress.named.addAll(strings(tag.getList("named", Tag.TAG_STRING)));
        progress.unlaid.addAll(strings(tag.getList("unlaid", Tag.TAG_STRING)));
        progress.visited.addAll(strings(tag.getList("visited", Tag.TAG_STRING)));
        progress.stone = tag.getString("stone");
        CompoundTag seen = tag.getCompound("seen");
        seen.getAllKeys().forEach(key -> progress.seen.put(UUID.fromString(key), new LinkedHashSet<>(strings(seen.getList(key, Tag.TAG_STRING)))));
        CompoundTag tracking = tag.getCompound("tracking");
        tracking.getAllKeys().forEach(key -> progress.tracking.put(UUID.fromString(key), tracking.getString(key)));
        CompoundTag trust = tag.getCompound("trust");
        trust.getAllKeys().forEach(key -> progress.trust.put(key, trust.getInt(key)));
        return progress;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag steps = new CompoundTag();
        this.steps.forEach((quest, index) -> Story.get().quest(quest).ifPresent(found -> steps.putString(quest, index >= found.steps().size() ? DONE : found.steps().get(index).id())));
        tag.put("steps", steps);
        CompoundTag scratch = new CompoundTag();
        this.scratch.forEach(scratch::put);
        tag.put("scratch", scratch);
        CompoundTag choices = new CompoundTag();
        this.choices.forEach(choices::putString);
        tag.put("choices", choices);
        tag.put("told", lines(told));
        ListTag conversations = new ListTag();
        this.conversations.forEach((key, heard) -> {
            CompoundTag talk = new CompoundTag();
            talk.putString("key", key);
            ListTag lines = new ListTag();
            heard.forEach(line -> {
                CompoundTag said = new CompoundTag();
                said.putString("by", line.by());
                said.putString("text", line.text());
                lines.add(said);
            });
            talk.put("lines", lines);
            conversations.add(talk);
        });
        tag.put("conversations", conversations);
        tag.put("pages", list(List.copyOf(pages)));
        tag.put("gear", list(List.copyOf(gear)));
        CompoundTag met = new CompoundTag();
        gearMet.forEach(met::putLong);
        tag.put("gearMet", met);
        tag.putLong("gearSpoke", gearSpoke);
        CompoundTag gearTracking = new CompoundTag();
        this.gearTracking.forEach((player, thing) -> gearTracking.putString(player.toString(), thing));
        tag.put("gearTracking", gearTracking);
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
        this.wards.forEach((name, box) -> wards.putIntArray(name, corners(box)));
        tag.put("wards", wards);
        CompoundTag rooms = new CompoundTag();
        this.rooms.forEach((name, boxes) -> {
            ListTag list = new ListTag();
            boxes.forEach(box -> list.add(new IntArrayTag(corners(box))));
            rooms.put(name, list);
        });
        tag.put("rooms", rooms);
        CompoundTag refused = new CompoundTag();
        this.refused.forEach((name, sites) -> refused.putLongArray(name, sites.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put("refused", refused);
        CompoundTag witnesses = new CompoundTag();
        this.witnesses.forEach((name, witness) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("centre", witness.centre().asLong());
            entry.put("blocks", list(witness.blocks()));
            witnesses.put(name, entry);
        });
        tag.put("witnesses", witnesses);
        tag.put("gifts", list(List.copyOf(gifts)));
        tag.put("crossed", list(crossed.stream().map(Climate::name).toList()));
        tag.putLong("crossed_day", crossedDay);
        tag.put("carried", list(carried));
        tag.put("held", list(List.copyOf(held)));
        tag.put("joined", list(List.copyOf(joined)));
        tag.putLongArray("lit", lit.stream().mapToLong(Long::longValue).toArray());
        tag.put("named", list(List.copyOf(named)));
        tag.put("unlaid", list(List.copyOf(unlaid)));
        tag.put("visited", list(List.copyOf(visited)));
        CompoundTag seen = new CompoundTag();
        this.seen.forEach((player, keys) -> seen.put(player.toString(), list(List.copyOf(keys))));
        tag.put("seen", seen);
        CompoundTag tracking = new CompoundTag();
        this.tracking.forEach((player, quest) -> tracking.putString(player.toString(), quest));
        tag.put("tracking", tracking);
        CompoundTag trust = new CompoundTag();
        this.trust.forEach(trust::putInt);
        tag.put("trust", trust);
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
