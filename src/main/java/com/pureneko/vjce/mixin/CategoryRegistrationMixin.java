package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.VjceJeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "mezz.jei.library.load.registration.RecipeCategoryRegistration", remap = false)
public abstract class CategoryRegistrationMixin {
    @ModifyVariable(method = "addRecipeCategories", at = @At("HEAD"), argsOnly = true, remap = false)
    private IRecipeCategory<?>[] vjce$adapt(IRecipeCategory<?>[] categories) {
        // JEI may call isHandled while earlier plugins register recipes, before our registerRecipes.
        var helpers = ((mezz.jei.api.registration.IRecipeCategoryRegistration) (Object) this).getJeiHelpers();
        VjceJeiPlugin.helpers = helpers;
        com.pureneko.vjce.integration.IngredientAccess.manager = helpers.getIngredientManager();
        var result = categories.clone();
        for (int i = 0; i < result.length; i++) result[i] = VjceJeiPlugin.intercept(result[i]);
        return result;
    }
}
