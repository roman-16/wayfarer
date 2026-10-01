package io.github.roman16.wayfarer;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class Spread {
    private static final TagKey<StructureSet> UNSPREAD =
            TagKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath(Wayfarer.MODID, "unspread"));

    private Spread() {}

    public static int rings(ServerLevel level, HolderSet<Structure> structures, int rings) {
        boolean spread = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).holders()
                .filter(set -> set.value().structures().stream().anyMatch(entry -> structures.contains(entry.structure())))
                .noneMatch(set -> set.is(UNSPREAD));
        return spread ? (int) Math.ceil(rings / Balance.INSTANCE.structures().spreadFactor()) : rings;
    }
}
