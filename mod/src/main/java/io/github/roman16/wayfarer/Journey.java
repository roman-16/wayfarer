package io.github.roman16.wayfarer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
    private static final String TALES = "tales";
    private static final String HEARD = "heard";
    private static final String DAY = "day";
    private static final String LEADS = "leads";
    private static final String SAID = "said";
    private static final String BUILT = "built";
    private static final String FOUND = "found";

    private final Set<UUID> awake = new HashSet<>();
    private final Map<UUID, List<ResourceLocation>> claims = new HashMap<>();
    private final Set<UUID> slayers = new HashSet<>();
    private final Map<UUID, Tale> tales = new HashMap<>();

    public record Lead(BlockPos pos, boolean built, boolean found) {}

    public final class Tale {
        private final List<String> heard = new ArrayList<>();
        private final Map<String, Lead> leads = new LinkedHashMap<>();
        private final Set<String> said = new HashSet<>();
        private long day = -1;

        public Set<String> heard() {
            return Set.copyOf(heard);
        }

        public long day() {
            return day;
        }

        public void hear(String step, long today) {
            heard.add(step);
            day = today;
            setDirty();
        }

        public Map<String, Lead> leads() {
            return Map.copyOf(leads);
        }

        public Optional<Lead> lead(String id) {
            return Optional.ofNullable(leads.get(id));
        }

        public Set<String> found() {
            Set<String> found = new HashSet<>();
            leads.forEach((id, lead) -> {
                if (lead.found()) {
                    found.add(id);
                }
            });
            return found;
        }

        public void give(String id, BlockPos pos) {
            leads.put(id, new Lead(pos, false, false));
            setDirty();
        }

        public void build(String id) {
            leads.computeIfPresent(id, (key, lead) -> new Lead(lead.pos(), true, lead.found()));
            setDirty();
        }

        public void find(String id) {
            leads.computeIfPresent(id, (key, lead) -> new Lead(lead.pos(), lead.built(), true));
            setDirty();
        }

        public boolean said(String scene) {
            return said.contains(scene);
        }

        public boolean say(String scene) {
            boolean added = said.add(scene);
            if (added) {
                setDirty();
            }
            return added;
        }
    }

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

    public static boolean dragonDead(MinecraftServer server) {
        return !of(server).slayers.isEmpty();
    }

    public static Tale tale(ServerPlayer player) {
        return of(player.server).tale(Teams.id(player));
    }

    public Tale tale(UUID team) {
        return tales.computeIfAbsent(team, key -> new Tale());
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
        CompoundTag tales = tag.getCompound(TALES);
        for (String team : tales.getAllKeys()) {
            CompoundTag saved = tales.getCompound(team);
            Tale tale = journey.tale(UUID.fromString(team));
            saved.getList(HEARD, Tag.TAG_STRING).forEach(step -> tale.heard.add(step.getAsString()));
            saved.getList(SAID, Tag.TAG_STRING).forEach(scene -> tale.said.add(scene.getAsString()));
            tale.day = saved.getLong(DAY);
            CompoundTag leads = saved.getCompound(LEADS);
            for (String id : leads.getAllKeys()) {
                CompoundTag lead = leads.getCompound(id);
                tale.leads.put(id, new Lead(
                        new BlockPos(lead.getInt("x"), lead.getInt("y"), lead.getInt("z")), lead.getBoolean(BUILT), lead.getBoolean(FOUND)));
            }
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
        CompoundTag talesTag = new CompoundTag();
        tales.forEach((team, tale) -> {
            CompoundTag saved = new CompoundTag();
            saved.put(HEARD, strings(tale.heard));
            saved.put(SAID, strings(tale.said.stream().sorted().toList()));
            saved.putLong(DAY, tale.day);
            CompoundTag leads = new CompoundTag();
            tale.leads.forEach((id, lead) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", lead.pos().getX());
                entry.putInt("y", lead.pos().getY());
                entry.putInt("z", lead.pos().getZ());
                entry.putBoolean(BUILT, lead.built());
                entry.putBoolean(FOUND, lead.found());
                leads.put(id, entry);
            });
            saved.put(LEADS, leads);
            talesTag.put(team.toString(), saved);
        });
        tag.put(TALES, talesTag);
        return tag;
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        values.stream().map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    private static ListTag teams(Set<UUID> teams) {
        ListTag list = new ListTag();
        teams.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(list::add);
        return list;
    }
}
