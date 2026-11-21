package com.pureneko.vjce.client.canvas;
import com.pureneko.vjce.client.IngredientSlot;
import com.pureneko.vjce.integration.*;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import java.util.*;

/** Owns JEI's real slots so drawing, tooltips and focus search share transformed geometry. */
public final class CanvasJeiWidget implements ISlottedRecipeWidget {
    private final CanvasRuntime runtime;
    public final Map<CanvasElement,IRecipeSlotDrawable> slots=new LinkedHashMap<>();
    public CanvasJeiWidget(CanvasRuntime runtime){this.runtime=runtime;}
    @Override public ScreenPosition getPosition(){return new ScreenPosition(0,0);}
    public void bind(CanvasElement element,IRecipeSlotDrawable slot){slots.put(element,slot);if(element instanceof IngredientSlot item)item.jeiOwned=true;}
    @Override public void tick(){runtime.tick();}
    @Override public void drawWidget(GuiGraphics g,double x,double y){
        runtime.draw(g,x,y,net.minecraft.client.Minecraft.getInstance().getFrameTime());
        for(var entry:slots.entrySet()){
            var e=entry.getKey();if(!NativePage.visible(e))continue;var slot=entry.getValue();var r=slot.getRect();
            var transform=new ElementTransform(runtime.ui.rootElement,e,r.getWidth(),r.getHeight());NativeSlotTransforms.register(slot,transform);
            g.flush();g.pose().pushPose();try(var alpha=com.pureneko.vjce.client.CanvasOpacity.begin(g,com.pureneko.vjce.client.CanvasOpacity.inherited(e))){transform.apply(g);g.pose().translate(-r.getX(),-r.getY(),0);slot.draw(g);g.flush();}finally{g.pose().popPose();}
        }
    }
    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x,double y){
        var entries=new ArrayList<>(slots.entrySet());Collections.reverse(entries);
        for(var entry:entries){var e=entry.getKey();if(!NativePage.visible(e))continue;var slot=entry.getValue();var r=slot.getRect();var t=new ElementTransform(runtime.ui.rootElement,e,r.getWidth(),r.getHeight());NativeSlotTransforms.register(slot,t);if(t.contains(x,y))return Optional.of(new RecipeSlotUnderMouse(slot,0,0));}
        return Optional.empty();
    }
}
