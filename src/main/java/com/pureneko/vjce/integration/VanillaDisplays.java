package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.recipe.vanilla.*;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Creates client-only objects of JEI's original recipe classes; never registers server recipes. */
public final class VanillaDisplays {
    private VanillaDisplays() {}
    public static boolean supports(ResourceLocation category) {
        return category.getNamespace().equals("minecraft") && RuntimeAudit.VANILLA.contains(category.getPath());
    }
    public static Object create(ResourceLocation type, CategoryDocument.Recipe recipe) {
        if (!supports(type)) throw new IllegalArgumentException("No synthetic-recipe adapter for " + type);
        String category = type.getPath();
        var inputs = recipe.slots.stream().filter(s -> s.role.equals("INPUT")).map(VanillaDisplays::items).toList();
        var outputs = recipe.slots.stream().filter(s -> s.role.equals("OUTPUT")).flatMap(s -> items(s).stream()).toList();
        ItemStack result = outputs.isEmpty() ? new ItemStack(Items.BARRIER) : outputs.getFirst();
        var ingredientList = NonNullList.<Ingredient>create();
        inputs.forEach(items -> ingredientList.add(Ingredient.of(items.stream())));
        Ingredient first = ingredientList.isEmpty() ? Ingredient.EMPTY : ingredientList.getFirst();
        var uid = ResourceLocation.parse(recipe.id);
        var factory = VjceJeiPlugin.helpers.getVanillaRecipeFactory();
        int ticks = Math.max(1, (int) recipe.duration);
        return switch (category) {
            case "crafting" -> new RecipeHolder<>(uid, new ShapelessRecipe("", CraftingBookCategory.MISC, result, ingredientList));
            case "stonecutting" -> new RecipeHolder<>(uid, new StonecutterRecipe("", first, result));
            case "smelting" -> factory.createSmeltingRecipe(first, result, ticks, recipe.values.getOrDefault("experience", 0.0).floatValue(), uid);
            case "smoking" -> new RecipeHolder<>(uid, new SmokingRecipe("", CookingBookCategory.MISC, first, result, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "blasting" -> new RecipeHolder<>(uid, new BlastingRecipe("", CookingBookCategory.MISC, first, result, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "campfire_cooking" -> new RecipeHolder<>(uid, new CampfireCookingRecipe("", CookingBookCategory.MISC, first, result, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "smithing" -> new RecipeHolder<>(uid, new SmithingTransformRecipe(at(ingredientList, 0), at(ingredientList, 1), at(ingredientList, 2), result));
            case "brewing" -> factory.createBrewingRecipe(list(inputs, 1), list(inputs, 0), result, uid);
            case "anvil" -> factory.createAnvilRecipe(list(inputs, 0), list(inputs, 1), outputs, uid);
            case "grindstone" -> factory.createGrindstoneRecipe(list(inputs, 0), list(inputs, 1), outputs, recipe.values.getOrDefault("minXp", 0.0).intValue(), recipe.values.getOrDefault("maxXp", 0.0).intValue(), uid);
            case "fuel" -> new IJeiFuelingRecipe() {
                @Override public List<ItemStack> getInputs() { return inputs.stream().flatMap(List::stream).toList(); }
                @Override public int getBurnTime() { return ticks; }
            };
            case "compostable" -> new IJeiCompostingRecipe() {
                @Override public List<ItemStack> getInputs() { return inputs.stream().flatMap(List::stream).toList(); }
                @Override public float getChance() { return recipe.values.getOrDefault("chance", 1.0).floatValue(); }
                @Override public ResourceLocation getUid() { return uid; }
            };
            default -> throw new IllegalArgumentException("No synthetic-recipe adapter for " + category);
        };
    }
    private static Ingredient at(List<Ingredient> values, int index) { return index < values.size() ? values.get(index) : Ingredient.EMPTY; }
    private static List<ItemStack> list(List<List<ItemStack>> values, int index) { return index < values.size() ? values.get(index) : List.of(); }
    private static List<ItemStack> items(CategoryDocument.Slot slot) {
        return IngredientAccess.decode(slot).stream().map(mezz.jei.api.ingredients.ITypedIngredient::getIngredient).filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
    }
}
