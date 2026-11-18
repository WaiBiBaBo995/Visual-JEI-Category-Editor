package com.pureneko.vjce.client.canvas;
import net.minecraft.client.gui.GuiGraphics;
public final class CanvasRuntime {
    public final CanvasUI ui;
    public CanvasRuntime(CanvasUI ui){this.ui=ui;}
    public void init(int width,int height){ui.rootElement.width=width;ui.rootElement.height=height;}
    public void tick(){ui.rootElement.screenTick();}
    public void draw(GuiGraphics graphics,double x,double y,float partialTick){ui.rootElement.render(graphics,x,y,partialTick);}
    public void onRemoved(){ui.rootElement.dispose();}
}
