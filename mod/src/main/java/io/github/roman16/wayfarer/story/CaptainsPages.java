package io.github.roman16.wayfarer.story;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.roman16.wayfarer.ContainerLoot;
import io.github.roman16.wayfarer.Library;
import io.github.roman16.wayfarer.Wayfarer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class CaptainsPages extends LootModifier {
    public static final MapCodec<CaptainsPages> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, CaptainsPages::new));
    private static final List<Page> PAGES = List.of(new Page("gazebound/orders_1", null), new Page("gazebound/orders_2", null), new Page("gazebound/orders_3", null),
            new Page("gazebound/orders_4", "the_crossing"), new Page("gazebound/orders_5", "the_crossing"));
    private static final TagKey<Structure> OUTPOSTS = TagKey.create(Registries.STRUCTURE, Wayfarer.id("owners/outpost"));
    private static final String SCRATCH = "captains_pages";
    private static final String GIVEN = "given";

    private record Page(String book, @Nullable String after) {}

    public CaptainsPages(LootItemCondition[] conditions) {
        super(conditions);
    }

    static void carry(Progress from, Progress into) {
        int given = from.scratch.getOrDefault(SCRATCH, new CompoundTag()).getInt(GIVEN);
        if (given > 0) {
            into.scratch(SCRATCH).putInt(GIVEN, given);
            into.setDirty();
        }
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        ServerLevel level = context.getLevel();
        if (origin == null || level.dimension() != Level.OVERWORLD || !ContainerLoot.filled(context.getQueriedLootTableId()) || !Engine.running(level.getServer())) {
            return loot;
        }
        StructureStart outpost = level.structureManager().getStructureWithPieceAt(BlockPos.containing(origin), OUTPOSTS);
        if (!outpost.isValid()) {
            return loot;
        }
        Progress progress = Progress.of(level.getServer());
        CompoundTag scratch = progress.scratch(SCRATCH);
        String key = Long.toString(outpost.getChunkPos().toLong());
        int given = scratch.getInt(GIVEN);
        if (!scratch.contains(key) && given < PAGES.size() && (PAGES.get(given).after() == null || progress.done(PAGES.get(given).after()))) {
            scratch.putInt(key, given);
            scratch.putInt(GIVEN, given + 1);
            progress.setDirty();
        }
        if (scratch.contains(key)) {
            Library.get().book(PAGES.get(scratch.getInt(key)).book()).ifPresent(page -> loot.add(page.written()));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
