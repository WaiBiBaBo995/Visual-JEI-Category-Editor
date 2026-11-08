package com.pureneko.vjce.integration;

import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.*;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

@JeiPlugin
public final class VjceJeiPlugin implements IModPlugin {
    public static IJeiHelpers helpers;
    public static IJeiRuntime runtime;
    public static final Map<String, IRecipeCategory<?>> originals = new LinkedHashMap<>();
    public static final Map<String, CapturedCategory<?>> adapters = new LinkedHashMap<>();
    private static List<CategoryDocument> documents;
    private final Map<CategoryDocument, DisplayCategory<CategoryDocument.Recipe>> custom = new LinkedHashMap<>();
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("vjce", "plugin"); }
    public static List<CategoryDocument> documents() {
        if (documents == null) {
            var loaded = new ArrayList<CategoryDocument>();
            for (var document : VisualJeiCategoryEditor.STORE.loadAll()) {
                try {
                    if (!document.layout.isBlank()) com.lowdragmc.lowdraglib2.gui.ui.UITemplate.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, net.minecraft.nbt.TagParser.parseTag(document.layout)).getOrThrow();
                    loaded.add(document);
                } catch (Exception e) { VisualJeiCategoryEditor.STORE.errors.add(document.id + " layout: " + e.getMessage()); }
            }
            documents = List.copyOf(loaded);
            VisualJeiCategoryEditor.STORE.errors.forEach(error -> VisualJeiCategoryEditor.LOG.error("VJCE: {}", error));
        }
        return documents;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static IRecipeCategory<?> intercept(IRecipeCategory<?> original) {
        String id = original.getRecipeType().getUid().toString();
        originals.put(id, original);
        for (var document : documents()) if (document.target.equals(id)) {
            var adapted = new CapturedCategory(original, document);
            adapters.put(id, adapted);
            return adapted;
        }
        return original;
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        helpers = registration.getJeiHelpers();
        CategoryFixtures.categories(registration);
        custom.clear();
        for (var document : documents()) if (document.target.isEmpty()) {
            var type = new RecipeType<>(ResourceLocation.parse(document.id), CategoryDocument.Recipe.class);
            var category = new DisplayCategory<>(document, type, recipe -> recipe);
            custom.put(document, category);
            registration.addRecipeCategories(category);
        }
    }
    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerRecipes(IRecipeRegistration registration) {
        IngredientAccess.manager = registration.getIngredientManager();
        CategoryFixtures.recipes(registration);
        for (var entry : custom.entrySet()) {
            var valid = new ArrayList<CategoryDocument.Recipe>();
            for (var recipe : entry.getKey().recipes) try {
                if (recipe.hidden) continue;
                recipe.slots.forEach(IngredientAccess::decode);
                valid.add(recipe);
            } catch (Exception e) { VisualJeiCategoryEditor.LOG.error("VJCE skipped {}: {}", recipe.id, e.getMessage()); }
            registration.addRecipes(entry.getValue().getRecipeType(), valid);
        }
        for (var adapter : adapters.values()) {
            for (var recipe : adapter.document.recipes) if (recipe.source.isEmpty() && !recipe.hidden) try {
                Object synthetic = VanillaDisplays.create(adapter.getRecipeType().getUid(), recipe);
                ((CapturedCategory) adapter).addDisplay(synthetic, recipe);
                registration.addRecipes((RecipeType) adapter.getRecipeType(), List.of(synthetic));
            } catch (Exception e) { VisualJeiCategoryEditor.LOG.error("VJCE skipped added display {}: {}", recipe.id, e.getMessage()); }
        }
        CompatibilityAudit.register(registration);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        custom.forEach((document, category) -> registerCatalysts(registration, document, category.getRecipeType()));
        adapters.values().forEach(category -> registerCatalysts(registration, category.document, category.getRecipeType()));
    }
    private static void registerCatalysts(IRecipeCatalystRegistration registration, CategoryDocument document, RecipeType<?> type) {
        var added = new ArrayList<ItemStack>();
        for (var encoded : document.catalysts) {
            try {
                var stack = ItemPresentationCodec.decode(encoded);
                if (added.stream().anyMatch(previous -> ItemStack.isSameItemSameComponents(previous, stack))) continue;
                registration.addRecipeCatalyst(stack, type);
                added.add(stack);
            } catch (RuntimeException error) {
                VisualJeiCategoryEditor.LOG.warn("VJCE skipped category catalyst {} / {}: {}", document.id, encoded, error.getMessage());
            }
        }
    }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime = value; IngredientAccess.manager = value.getIngredientManager(); }
    @Override public void onRuntimeUnavailable() { com.pureneko.vjce.client.SceneResources.releaseAll(); runtime = null; documents = null; originals.clear(); adapters.clear(); NativePage.clear(); }
}
