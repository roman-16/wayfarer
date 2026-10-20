package io.github.roman16.wayfarer.client;

import io.github.roman16.wayfarer.Wayfarer;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class JeiLink implements IModPlugin {
    private static final List<String> FOUND = Stream.of("chainmail", "diamond", "golden", "iron", "netherite")
            .flatMap(metal -> Stream.of("boots", "chestplate", "helmet", "leggings").map(piece -> metal + "_" + piece)).toList();
    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return Wayfarer.id("journal");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(FOUND.stream().map(item -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(item)))).toList(),
                VanillaTypes.ITEM_STACK, Component.translatable("wayfarer.jei.found"));
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

    static boolean typing() {
        return runtime != null && runtime.getIngredientListOverlay().hasKeyboardFocus();
    }

    static void show(ResourceLocation item) {
        if (runtime != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        }
    }
}
