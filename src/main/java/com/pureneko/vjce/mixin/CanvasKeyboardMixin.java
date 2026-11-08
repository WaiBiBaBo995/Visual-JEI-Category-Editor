package com.pureneko.vjce.mixin;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.pureneko.vjce.client.RecipeLayoutView;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla closes a Screen on Escape before dispatching the focused widget's key event. */
@Mixin(Screen.class)
public abstract class CanvasKeyboardMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void vjce$cancelCanvasGesture(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> result) {
        if (key != GLFW.GLFW_KEY_ESCAPE || !((Object) this instanceof ModularUIScreen screen)) return;
        var views = screen.getModularUI().ui.rootElement.select("*").filter(RecipeLayoutView.class::isInstance).map(RecipeLayoutView.class::cast).toList();
        if (views.stream().anyMatch(RecipeLayoutView::modalBlocksCanvas)) {
            screen.getModularUI().getWidget().keyPressed(key, scan, modifiers); result.setReturnValue(true); return;
        }
        if (views.stream().anyMatch(RecipeLayoutView::escapeCanvasTransform)) result.setReturnValue(true);
    }
}
