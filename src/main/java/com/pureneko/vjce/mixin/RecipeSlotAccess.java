package com.pureneko.vjce.mixin;

import mezz.jei.common.util.ImmutableRect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public interface RecipeSlotAccess {
    @Accessor("rect")
    ImmutableRect2i vjce$ingredientRect();

}
