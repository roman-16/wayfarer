package io.github.roman16.wayfarer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private static final Bundled<Spec> SPEC = Bundled.json("/wayfarer/owners.json", Owners::spec);

    private Owners() {}

    private record Spec(Set<ResourceLocation> cut, Map<ResourceLocation, String> kind, Map<String, JsonObject> spacing) {}

    private static Spec spec(JsonObject json) {
        Map<ResourceLocation, String> kinds = new HashMap<>();
        Map<String, JsonObject> spacing = new LinkedHashMap<>();
        json.getAsJsonObject("kinds").entrySet().forEach(kind -> {
            spacing.put(kind.getKey(), kind.getValue().getAsJsonObject());
            kind.getValue().getAsJsonObject().getAsJsonArray("structures")
                    .forEach(structure -> kinds.put(ResourceLocation.parse(structure.getAsString()), kind.getKey()));
        });
        Set<ResourceLocation> cut = json.getAsJsonArray("cut").asList().stream().map(JsonElement::getAsString).map(ResourceLocation::parse)
                .collect(Collectors.toUnmodifiableSet());
        return new Spec(cut, Map.copyOf(kinds), spacing);
    }

    public static List<Holder<StructureSet>> arrange(List<Holder<StructureSet>> sets) {
        Map<String, List<StructureSet.StructureSelectionEntry>> gathered = new LinkedHashMap<>();
        List<Holder<StructureSet>> arranged = new ArrayList<>();
        Spec spec = SPEC.get();
        for (Holder<StructureSet> set : sets) {
            List<StructureSet.StructureSelectionEntry> kept = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                Optional<ResourceLocation> id = id(entry.structure());
                String kind = id.map(spec.kind()::get).orElse(null);
                if (kind != null) {
                    gathered.computeIfAbsent(kind, key -> new ArrayList<>()).add(entry);
                } else if (id.map(spec.cut()::contains).orElse(false)) {
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
        gathered.forEach((kind, entries) -> arranged.add(Holder.direct(new StructureSet(List.copyOf(entries), placement(spec.spacing().get(kind))))));
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
