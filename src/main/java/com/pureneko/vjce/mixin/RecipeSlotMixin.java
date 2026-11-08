package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.NativeSlotTransforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RecipeSlotUnderMouse rechecks the returned slot; retain identity and use its edited geometry. */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public abstract class RecipeSlotMixin {
    @Inject(method = "isMouseOver", at = @At("HEAD"), cancellable = true)
    private void vjce$transformedHit(double x, double y, CallbackInfoReturnable<Boolean> result) {
        var hit = NativeSlotTransforms.contains(this, x, y); if (hit != null) result.setReturnValue(hit);
    }
}
