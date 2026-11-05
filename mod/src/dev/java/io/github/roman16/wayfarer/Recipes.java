package io.github.roman16.wayfarer;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public final class Recipes implements IModPlugin {
    private static final int INVENTORY = 36;
    private static final int RESULT = 0;
    private static final int SMALL_GRID = 2;
    private static final int LARGE_GRID = 3;
    private static final int NAMED_CHOICES = 2;
    private static final int OPEN_TICKS = 40;
    private static final int OPEN_TRIES = 4;
    @Nullable
    private static IJeiRuntime jei;

    private record Plan(IRecipeSlotsView slots, boolean itself, List<String> missing, boolean small) {}

    @Override
    public ResourceLocation getPluginUid() {
        return Wayfarer.id("hands");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jei = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        jei = null;
    }

    static void craft(Minecraft minecraft, String argument) {
        String[] words = argument.strip().split(" ");
        String last = words[words.length - 1];
        boolean counted = words.length > 1 && last.matches("\\d+");
        String name = counted ? argument.strip().substring(0, argument.strip().lastIndexOf(' ')).strip() : argument.strip();
        int wanted = counted ? Integer.parseInt(last) : 1;
        Optional<Item> item = Hands.item(name);
        if (item.isEmpty()) {
            Driver.fail("craft failed: no item is called " + name);
            return;
        }
        if (jei == null) {
            Driver.fail("craft failed: JEI has not started yet");
            return;
        }
        IRecipeManager recipes = jei.getRecipeManager();
        IFocusFactory focuses = jei.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> focus = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, new ItemStack(item.get()));
        LocalPlayer player = minecraft.player;
        List<Plan> plans = recipes.createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                .filter(category -> recipes.createRecipeCatalystLookup(category.getRecipeType()).getItemStack().anyMatch(stack -> stack.is(Items.CRAFTING_TABLE)))
                .flatMap(category -> plans(recipes, focuses, focus, category, player.getInventory(), item.get()))
                .sorted(Comparator.comparing(Plan::itself).thenComparing(plan -> plan.missing().size()))
                .toList();
        String made = item.get().getDescription().getString();
        if (plans.isEmpty()) {
            Driver.fail("craft failed: no recipe makes " + made);
            return;
        }
        Optional<BlockPos> table = Hands.table(minecraft);
        Optional<Plan> chosen = plans.stream().filter(plan -> plan.missing().isEmpty() && (table.isPresent() || plan.small())).findFirst();
        if (chosen.isEmpty()) {
            boolean big = plans.stream().anyMatch(plan -> plan.missing().isEmpty());
            Driver.fail(big ? "craft failed: " + made + " needs a crafting table within reach" : "craft failed: needs " + plans.stream()
                    .map(plan -> String.join(", ", plan.missing())).distinct().limit(NAMED_CHOICES).collect(Collectors.joining("; or ")));
            return;
        }
        String where = table.map(pos -> " at the crafting table, " + Math.round(Math.sqrt(player.blockPosition().distSqr(pos))) + " blocks "
                + Senses.side(player, pos.getCenter())).orElse(" in the inventory's grid");
        Runnable fill = () -> fill(minecraft, chosen.get(), item.get(), wanted, 0, where);
        if (table.isEmpty()) {
            Hands.open(minecraft, fill);
            return;
        }
        IntStream.range(0, Inventory.getSelectionSize()).filter(slot -> player.getInventory().getItem(slot).isEmpty()).boxed().findFirst()
                .or(() -> IntStream.range(0, Inventory.getSelectionSize()).filter(slot -> player.getInventory().getItem(slot).getItem() instanceof TieredItem).boxed().findFirst())
                .ifPresent(slot -> player.getInventory().selected = slot);
        Hands.settled(minecraft, () -> openTable(minecraft, table.get(), fill, OPEN_TRIES));
    }

    private static void openTable(Minecraft minecraft, BlockPos table, Runnable fill, int tries) {
        LocalPlayer player = minecraft.player;
        Driver.useAt(minecraft, table, missed -> {
            if (missed != null) {
                Driver.fail("craft failed: the click went to " + missed + ", not to the crafting table");
                return;
            }
            long from = minecraft.level.getGameTime();
            Driver.after(() -> player.containerMenu instanceof CraftingMenu && minecraft.screen != null || minecraft.level.getGameTime() - from > OPEN_TICKS,
                    "the crafting table to open", () -> {
                        if (player.containerMenu instanceof CraftingMenu && minecraft.screen != null) {
                            Driver.drawn("the crafting table", fill);
                        } else if (tries > 1) {
                            openTable(minecraft, table, fill, tries - 1);
                        } else {
                            Driver.fail("craft failed: the crafting table did not open at " + OPEN_TRIES + " clicks");
                        }
                    });
        });
    }

    private static <T> Stream<Plan> plans(IRecipeManager recipes, IFocusFactory focuses, IFocus<ItemStack> focus, IRecipeCategory<T> category, Inventory inventory, Item made) {
        return recipes.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get()
                .flatMap(recipe -> recipes.createRecipeLayoutDrawable(category, recipe, focuses.createFocusGroup(List.of(focus))).stream())
                .map(layout -> new Plan(layout.getRecipeSlotsView(), itself(layout.getRecipeSlotsView(), made), missing(inventory, layout.getRecipeSlotsView()), small(layout.getRecipe())));
    }

    private static boolean itself(IRecipeSlotsView slots, Item made) {
        return slots.getSlotViews(RecipeIngredientRole.INPUT).stream().anyMatch(slot -> slot.getItemStacks().anyMatch(stack -> stack.is(made)));
    }

    private static boolean small(Object recipe) {
        Object crafting = recipe instanceof RecipeHolder<?> holder ? holder.value() : recipe;
        return crafting instanceof CraftingRecipe found && found.canCraftInDimensions(SMALL_GRID, SMALL_GRID);
    }

    private static List<String> missing(Inventory inventory, IRecipeSlotsView slots) {
        int[] left = new int[INVENTORY];
        for (int index = 0; index < INVENTORY; index++) {
            left[index] = inventory.getItem(index).getCount();
        }
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> choices = slot.getItemStacks().toList();
            if (choices.isEmpty()) {
                continue;
            }
            int found = -1;
            for (int index = 0; index < INVENTORY && found < 0; index++) {
                ItemStack stack = inventory.getItem(index);
                if (left[index] > 0 && choices.stream().anyMatch(choice -> ItemStack.isSameItem(choice, stack))) {
                    found = index;
                }
            }
            if (found >= 0) {
                left[found]--;
            } else {
                needs.merge(choice(slot, choices), 1, Integer::sum);
            }
        }
        return needs.entrySet().stream().map(entry -> entry.getValue() + " " + entry.getKey()).toList();
    }

    private static String choice(IRecipeSlotView slot, List<ItemStack> choices) {
        if (choices.size() > NAMED_CHOICES && slot.getTagKey().isPresent()) {
            return "of any " + slot.getTagKey().get().location().getPath().replace('_', ' ').replace('/', ' ');
        }
        return choices.stream().limit(NAMED_CHOICES).map(stack -> stack.getHoverName().getString()).distinct().collect(Collectors.joining(" or "));
    }

    private static void fill(Minecraft minecraft, Plan plan, Item item, int wanted, int made, String where) {
        LocalPlayer player = minecraft.player;
        AbstractContainerMenu menu = player.containerMenu;
        Inventory inventory = player.getInventory();
        int side = menu instanceof InventoryMenu ? SMALL_GRID : LARGE_GRID;
        for (int cell = 1; cell <= side * side; cell++) {
            if (!menu.getSlot(cell).getItem().isEmpty()) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, cell, 0, ClickType.QUICK_MOVE, player);
            }
        }
        List<String> stuck = IntStream.rangeClosed(1, side * side).mapToObj(menu::getSlot).filter(slot -> !slot.getItem().isEmpty())
                .map(slot -> slot.getItem().getCount() + " " + slot.getItem().getHoverName().getString()).toList();
        if (!stuck.isEmpty()) {
            Hands.close(minecraft);
            Driver.fail("craft failed: the grid holds " + String.join(", ", stuck) + " and the inventory has no room to take it back");
            return;
        }
        List<String> missing = missing(inventory, plan.slots());
        if (!missing.isEmpty()) {
            Hands.close(minecraft);
            Driver.fail("craft failed: made " + made + ", then needs " + String.join(", ", missing));
            return;
        }
        List<IRecipeSlotView> inputs = plan.slots().getSlotViews(RecipeIngredientRole.INPUT);
        List<Integer> cells = IntStream.range(0, inputs.size()).filter(index -> !inputs.get(index).isEmpty()).boxed().toList();
        if (inputs.size() != LARGE_GRID * LARGE_GRID) {
            Hands.close(minecraft);
            Driver.fail("craft failed: JEI lays this recipe out in " + inputs.size() + " slots, not a grid of 3 by 3");
            return;
        }
        int top = cells.stream().mapToInt(index -> index / LARGE_GRID).min().orElse(0);
        int left = cells.stream().mapToInt(index -> index % LARGE_GRID).min().orElse(0);
        int[] held = new int[INVENTORY];
        for (int index = 0; index < INVENTORY; index++) {
            held[index] = inventory.getItem(index).getCount();
        }
        for (int cell : cells) {
            int row = cell / LARGE_GRID - top;
            int column = cell % LARGE_GRID - left;
            if (row >= side || column >= side) {
                Hands.close(minecraft);
                Driver.fail("craft failed: " + item.getDescription().getString() + " needs a crafting table within reach");
                return;
            }
            List<ItemStack> choices = inputs.get(cell).getItemStacks().toList();
            int from = IntStream.range(0, INVENTORY).filter(index -> held[index] > 0
                    && choices.stream().anyMatch(choice -> ItemStack.isSameItem(choice, inventory.getItem(index)))).boxed()
                    .max(Comparator.comparingInt((Integer index) -> IntStream.range(0, INVENTORY).filter(other -> held[other] > 0
                            && ItemStack.isSameItem(inventory.getItem(other), inventory.getItem(index))).map(other -> held[other]).sum())
                            .thenComparingInt(index -> -index)).orElseThrow();
            held[from]--;
            int slot = Hands.menuSlot(menu, inventory, from);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 1 + row * side + column, 1, ClickType.PICKUP, player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
        }
        Driver.after(() -> menu.getSlot(RESULT).getItem().is(item), "the grid to show " + item.getDescription().getString(), () -> {
            int before = inventory.countItem(item);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, RESULT, 0, ClickType.QUICK_MOVE, player);
            Hands.settled(minecraft, () -> {
                int count = inventory.countItem(item) - before;
                if (count <= 0) {
                    Hands.close(minecraft);
                    Driver.fail("craft failed: no room in the inventory for " + item.getDescription().getString() + (made > 0 ? " after " + made : "")
                            + "; clear a slot and craft again");
                    return;
                }
                int total = made + count;
                if (total < wanted) {
                    fill(minecraft, plan, item, wanted, total, where);
                    return;
                }
                Hands.close(minecraft);
                Driver.say("craft: " + total + " " + item.getDescription().getString() + where);
            });
        });
    }
}
