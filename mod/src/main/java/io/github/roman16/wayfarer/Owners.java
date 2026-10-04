package io.github.roman16.wayfarer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class Owners {
    private static final Set<ResourceLocation> CUT;
    private static final Map<ResourceLocation, String> KIND = new HashMap<>();
    private static final Map<String, JsonObject> SPACING = new LinkedHashMap<>();

    static {
        try (var stream = Owners.class.getResourceAsStream("/wayfarer/owners.json")) {
            if (stream == null) {
                throw new IllegalStateException("owners.json is missing from the mod jar; run just generate");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            CUT = json.getAsJsonArray("cut").asList().stream().map(JsonElement::getAsString).map(ResourceLocation::parse)
                    .collect(Collectors.toUnmodifiableSet());
            json.getAsJsonObject("kinds").entrySet().forEach(kind -> {
                SPACING.put(kind.getKey(), kind.getValue().getAsJsonObject());
                kind.getValue().getAsJsonObject().getAsJsonArray("structures")
                        .forEach(structure -> KIND.put(ResourceLocation.parse(structure.getAsString()), kind.getKey()));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Owners() {}

    public static List<Holder<StructureSet>> arrange(List<Holder<StructureSet>> sets) {
        Map<String, List<StructureSet.StructureSelectionEntry>> gathered = new LinkedHashMap<>();
        List<Holder<StructureSet>> arranged = new ArrayList<>();
        for (Holder<StructureSet> set : sets) {
            List<StructureSet.StructureSelectionEntry> kept = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                Optional<ResourceLocation> id = id(entry.structure());
                String kind = id.map(KIND::get).orElse(null);
                if (kind != null) {
                    gathered.computeIfAbsent(kind, key -> new ArrayList<>()).add(entry);
                } else if (id.map(CUT::contains).orElse(false)) {
                    continue;
                } else {
                    kept.add(entry);
                }
            }
            if (kept.size() == set.value().structures().size()) {
                arranged.add(set);
            } else if (!kept.isEmpty()) {
                arranged.add(Holder.direct(new StructureSet(List.copyOf(kept), set.value().placement())));
            }
        }
        gathered.forEach((kind, entries) -> arranged.add(Holder.direct(new StructureSet(List.copyOf(entries), placement(SPACING.get(kind))))));
        return List.copyOf(arranged);
    }

    private static Optional<ResourceLocation> id(Holder<Structure> structure) {
        return structure.unwrapKey().map(key -> key.location());
    }

    private static StructurePlacement placement(JsonObject kind) {
        return new RandomSpreadStructurePlacement(
                Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT,
                1.0F,
                kind.get("salt").getAsInt(),
                Optional.empty(),
                kind.get("spacing").getAsInt(),
                kind.get("separation").getAsInt(),
                RandomSpreadType.LINEAR);
    }
}
