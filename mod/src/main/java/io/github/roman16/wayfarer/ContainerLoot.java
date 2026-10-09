package io.github.roman16.wayfarer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class ContainerLoot extends LootModifier {
    public static final MapCodec<ContainerLoot> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, ContainerLoot::new));
    private static final String BLOCK_DROPS = "blocks/";
    private static final Set<String> CONTAINERS = Set.of("chest", "chests");

    public ContainerLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static boolean filled(ResourceLocation table) {
        return !table.getNamespace().equals(Wayfarer.MODID) && !table.getPath().startsWith(BLOCK_DROPS)
                && Arrays.stream(table.getPath().split("/")).anyMatch(CONTAINERS::contains);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (filled(context.getQueriedLootTableId())) {
            double kept = Balance.get().loot().keptShare();
            loot.removeIf(stack -> context.getRandom().nextDouble() >= kept);
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
