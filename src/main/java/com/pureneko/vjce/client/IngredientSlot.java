package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.pureneko.vjce.integration.IngredientAccess;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

@LDLRegister(name = "vjce-ingredient-slot", group = "inventory", registry = "ldlib2:ui_element")
public class IngredientSlot extends UIElement {
    public ITypedIngredient<?> ingredient;
    public long amount = 1;
    public IngredientSlot() {
        layout(l -> { l.width(18); l.height(18); l.paddingAll(1); });
        addClass("vjce-ingredient-slot");
        getStyle().setPipelineState(com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin.DEFAULT);
        getStyle().backgroundTexture(Sprites.RECT_DARK);
        getStyle().setPipelineState(com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin.INLINE);
    }
    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        drawIngredient(context.graphics, ingredient, getContentX(), getContentY(), getContentWidth(), getContentHeight(), amount);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void drawIngredient(GuiGraphics graphics, ITypedIngredient<?> ingredient, float x, float y, float width, float height, long amount) {
        if (ingredient == null || IngredientAccess.manager == null) return;
        graphics.pose().pushPose();
        graphics.flush();
        graphics.pose().translate(x, y, 100);
        var renderer = IngredientAccess.manager.getIngredientRenderer((mezz.jei.api.ingredients.IIngredientType) ingredient.getType());
        graphics.pose().scale(width / Math.max(1, renderer.getWidth()), height / Math.max(1, renderer.getHeight()), 1);
        renderer.render(graphics, ingredient.getIngredient());
        graphics.flush();
        if (amount > 1) {
            String text = Long.toString(amount);
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(Minecraft.getInstance().font, text, 17 - Minecraft.getInstance().font.width(text), 9, 0xFFFFFFFF, true);
        }
        graphics.pose().popPose();
    }
}
