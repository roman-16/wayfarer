package io.github.roman16.wayfarer;

import io.github.roman16.wayfarer.story.Tasks;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.RepairContext;
import net.silentchaos512.gear.item.RepairKitItem;
import net.silentchaos512.gear.setup.SgDataComponents;

public final class FireRepair extends CustomRecipe {
    private static final int QUARTERS = 4;
    @Nullable
    public static Player crafter;

    public FireRepair(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ItemStack gear, ItemStack kit) {}

    private record Mend(int damage, Map<MaterialInstance, Float> used) {}

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Player player = crafter != null ? crafter : CommonHooks.getCraftingPlayer();
        if (!(level instanceof ServerLevel server) || player == null
                || Fire.nearest(server, player.position(), Balance.get().fire().restBlocks()).isEmpty()) {
            return false;
        }
        Parts parts = parts(input);
        return parts != null && mend(parts).damage() > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parts parts = parts(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mended = parts.gear().copy();
        mended.setDamageValue(mended.getDamageValue() - mend(parts).damage());
        return mended;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Parts parts = parts(input);
        if (parts == null) {
            return remaining;
        }
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).getItem() instanceof RepairKitItem kit) {
                ItemStack left = input.getItem(slot).copy();
                kit.removeRepairMaterials(left, mend(parts).used());
                remaining.set(slot, left);
            }
        }
        if (CommonHooks.getCraftingPlayer() instanceof ServerPlayer player) {
            Tasks.did(player, "mend");
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Wayfarer.FIRE_REPAIR.get();
    }

    @Nullable
    private static Parts parts(CraftingInput input) {
        ItemStack gear = ItemStack.EMPTY;
        ItemStack kit = ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof RepairKitItem && kit.isEmpty()) {
                kit = stack;
            } else if (stack.isDamageableItem() && gear.isEmpty()) {
                gear = stack;
            } else {
                return null;
            }
        }
        return gear.isEmpty() || kit.isEmpty() ? null : new Parts(gear, kit);
    }

    private static Mend mend(Parts parts) {
        ItemStack gear = parts.gear();
        int half = gear.getMaxDamage() / 2;
        int wanted = gear.getDamageValue() - half;
        if (wanted <= 0) {
            return new Mend(0, Map.of());
        }
        RepairKitItem kit = (RepairKitItem) parts.kit().getItem();
        if (gear.getItem() instanceof GearItem) {
            ItemStack capped = gear.copy();
            capped.setDamageValue(wanted);
            return new Mend(kit.getDamageToRepair(capped, parts.kit(), RepairContext.Type.QUICK), kit.getRepairMaterials(capped, parts.kit(), RepairContext.Type.QUICK));
        }
        int quarter = Math.max(1, gear.getMaxDamage() / QUARTERS);
        Map<MaterialInstance, Float> stored = parts.kit().getOrDefault(SgDataComponents.MATERIAL_STORAGE.get(), Map.of());
        for (Map.Entry<MaterialInstance, Float> entry : stored.entrySet()) {
            boolean fits = false;
            for (ItemStack material : entry.getKey().getIngredient().getItems()) {
                fits |= gear.getItem().isValidRepairItem(gear, material);
            }
            if (fits) {
                float units = Math.min(entry.getValue(), (float) wanted / quarter);
                Map<MaterialInstance, Float> used = new HashMap<>();
                used.put(entry.getKey(), units);
                return new Mend(Math.min(wanted, Math.round(units * quarter)), used);
            }
        }
        return new Mend(0, Map.of());
    }
}
