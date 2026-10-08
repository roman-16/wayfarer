package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class JeiLink implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return Wayfarer.id("journal");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    static boolean ready() {
        return runtime != null;
    }

    static void show(ResourceLocation item) {
        if (runtime != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        }
    }
}
