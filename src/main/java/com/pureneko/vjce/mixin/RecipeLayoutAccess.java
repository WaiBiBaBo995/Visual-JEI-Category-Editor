package com.pureneko.vjce.mixin;

import mezz.jei.api.gui.drawable.*;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.library.gui.recipes.ShapelessIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.*;

/** Internal layout access; target signatures checked against JEI 19.64 and 19.70. */
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public interface RecipeLayoutAccess {
    @Invoker("ensureRecipeExtrasAreCreated") void vjce$createExtras();
    @Accessor("allWidgets") List<IRecipeWidget> vjce$widgets();
    @Accessor("recipeCategorySlots") List<IRecipeSlotDrawable> vjce$slots();
    @Accessor("drawables") List<IDrawable> vjce$drawables();
    @Accessor("recipeCategoryDecorators") Collection<IRecipeCategoryDecorator<?>> vjce$decorators();
    @Mutable @Accessor("recipeCategoryDecorators") void vjce$decorators(Collection<IRecipeCategoryDecorator<?>> decorators);
    @Accessor("recipeBackground") IScalableDrawable vjce$background();
    @Mutable @Accessor("recipeBackground") void vjce$background(IScalableDrawable background);
    @Accessor("shapelessIcon") ShapelessIcon vjce$shapeless();
    @Mutable @Accessor("shapelessIcon") void vjce$shapeless(ShapelessIcon icon);
}
