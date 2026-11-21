package com.pureneko.vjce.integration;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import java.util.*;

/** Only actual JEI slots explicitly owned by a VJCE transformed widget are affected. */
public final class NativeSlotTransforms {
    public static net.minecraft.client.renderer.Rect2i rect(mezz.jei.api.gui.ingredient.IRecipeSlotDrawable slot) {
        return slot instanceof com.pureneko.vjce.mixin.RecipeSlotAccess access
                ? access.vjce$ingredientRect().toMutable() : slot.getAreaIncludingBackground();
    }
    private static final Map<IRecipeSlotDrawable, ElementTransform> TRANSFORMS = Collections.synchronizedMap(new WeakHashMap<>());
    public static void register(IRecipeSlotDrawable slot, ElementTransform transform) { TRANSFORMS.put(slot, transform); }
    public static Boolean contains(Object slot, double x, double y) {
        var transform = TRANSFORMS.get(slot); return transform == null ? null : transform.contains(x, y);
    }
    public static void clear() { TRANSFORMS.clear(); }
}
