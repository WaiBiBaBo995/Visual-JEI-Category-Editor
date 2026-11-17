package com.pureneko.vjce.client;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.integration.IngredientAccess;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
public class IngredientSlot extends CanvasElement {
    public ITypedIngredient<?> ingredient;
    public long amount=1;
    public boolean jeiOwned;
    public IngredientSlot(){super("slot");width=18;height=18;}
    @Override public void drawBackgroundAdditional(CanvasContext c){
        if(jeiOwned)return;
        com.lowdragmc.lowdraglib.gui.widget.SlotWidget.ITEM_SLOT_TEXTURE.draw(c.graphics,(int)c.localMouseX,(int)c.localMouseY,0,0,(int)width,(int)height);
        drawIngredient(c.graphics,ingredient,1,1,width-2,height-2,amount);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) public static void drawIngredient(GuiGraphics graphics,ITypedIngredient<?> ingredient,float x,float y,float width,float height,long amount){
        if(ingredient==null||IngredientAccess.manager==null)return;
        graphics.pose().pushPose();
        // Banner/block-entity and third-party ingredient renderers can leak depth, lighting,
        // blending and target state. Finish their batches before restoring the surrounding UI.
        try (var state=new SceneRenderState()) {
            graphics.flush();
            graphics.pose().translate(x,y,0);
            var renderer=IngredientAccess.manager.getIngredientRenderer((mezz.jei.api.ingredients.IIngredientType)ingredient.getType());
            graphics.pose().scale(width/Math.max(1,renderer.getWidth()),height/Math.max(1,renderer.getHeight()),1);
            renderer.render(graphics,ingredient.getIngredient());graphics.flush();
            if(amount>1){String text=Long.toString(amount);graphics.drawString(Minecraft.getInstance().font,text,17-Minecraft.getInstance().font.width(text),9,0xffffffff,true);graphics.flush();}
        }finally{graphics.pose().popPose();}
    }
}
