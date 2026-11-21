package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.recipe.vanilla.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Creates client-only objects of JEI's original recipe classes; never registers server recipes. */
public final class VanillaDisplays {
    private VanillaDisplays() {}
    public static boolean supports(Identifier category) {
        return category.getNamespace().equals("minecraft") && RuntimeAudit.VANILLA.contains(category.getPath());
    }
    public static Object create(Identifier type, CategoryDocument.Recipe recipe) {
        if (!supports(type)) throw new IllegalArgumentException("No synthetic-recipe adapter for " + type);
        String category = type.getPath();
        var inputs = recipe.slots.stream().filter(s -> s.role.equals("INPUT")).map(VanillaDisplays::items).toList();
        var outputs = recipe.slots.stream().filter(s -> s.role.equals("OUTPUT")).flatMap(s -> items(s).stream()).toList();
        ItemStack result = outputs.isEmpty() ? new ItemStack(Items.BARRIER) : outputs.getFirst();
        var ingredientList = inputs.stream().map(VanillaDisplays::ingredient).toList();
        Ingredient first = ingredientList.stream().findFirst().flatMap(value -> value).orElseGet(() -> Ingredient.of(Items.BARRIER));
        var uid = Identifier.parse(recipe.id);
        var key = ResourceKey.create(Registries.RECIPE, uid);
        var common = new Recipe.CommonInfo(false);
        var craftingBook = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
        var cookingBook = new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "");
        var template = ItemStackTemplate.fromNonEmptyStack(result);
        var factory = VjceJeiPlugin.helpers.getVanillaRecipeFactory();
        int ticks = Math.max(1, (int) recipe.duration);
        return switch (category) {
            case "crafting" -> new RecipeHolder<>(key, new ShapelessRecipe(common, craftingBook, template, ingredientList.stream().flatMap(Optional::stream).toList()));
            case "stonecutting" -> new RecipeHolder<>(key, new StonecutterRecipe(common, first, template));
            case "smelting" -> factory.createSmeltingRecipe(first, result, ticks, recipe.values.getOrDefault("experience", 0.0).floatValue(), uid);
            case "smoking" -> new RecipeHolder<>(key, new SmokingRecipe(common, cookingBook, first, template, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "blasting" -> new RecipeHolder<>(key, new BlastingRecipe(common, cookingBook, first, template, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "campfire_cooking" -> new RecipeHolder<>(key, new CampfireCookingRecipe(common, cookingBook, first, template, recipe.values.getOrDefault("experience", 0.0).floatValue(), ticks));
            case "smithing" -> new RecipeHolder<>(key, new SmithingTransformRecipe(common, at(ingredientList, 0), at(ingredientList, 1).orElseGet(() -> Ingredient.of(Items.BARRIER)), at(ingredientList, 2), template));
            case "brewing" -> factory.createBrewingRecipe(list(inputs, 1), list(inputs, 0), result, uid);
            case "anvil" -> factory.createAnvilRecipe(list(inputs, 0), list(inputs, 1), outputs, uid);
            case "grindstone" -> factory.createGrindstoneRecipe(list(inputs, 0), list(inputs, 1), outputs, recipe.values.getOrDefault("minXp", 0.0).intValue(), recipe.values.getOrDefault("maxXp", 0.0).intValue(), uid);
            case "smelting_fuel", "blasting_fuel", "smoking_fuel" -> new IJeiFuelingRecipe() {
                @Override public List<ItemStack> getInputs() { return inputs.stream().flatMap(List::stream).toList(); }
                @Override public int getBurnTime() { return ticks; }
            };
            case "compostable" -> new IJeiCompostingRecipe() {
                @Override public List<ItemStack> getInputs() { return inputs.stream().flatMap(List::stream).toList(); }
                @Override public float getChance() { return recipe.values.getOrDefault("chance", 1.0).floatValue(); }
                @Override public Identifier getUid() { return uid; }
            };
            default -> throw new IllegalArgumentException("No synthetic-recipe adapter for " + category);
        };
    }
    private static Optional<Ingredient> at(List<Optional<Ingredient>> values, int index) {
        return index < values.size() ? values.get(index) : Optional.empty();
    }
    private static Optional<Ingredient> ingredient(List<ItemStack> stacks) {
        return stacks.isEmpty() ? Optional.empty() : Optional.of(Ingredient.of(stacks.stream().map(ItemStack::getItem)));
    }
    private static List<ItemStack> list(List<List<ItemStack>> values, int index) { return index < values.size() ? values.get(index) : List.of(); }
    private static List<ItemStack> items(CategoryDocument.Slot slot) {
        return IngredientAccess.decode(slot).stream().map(mezz.jei.api.ingredients.ITypedIngredient::getIngredient).filter(ItemStack.class::isInstance).map(ItemStack.class::cast).toList();
    }
}
