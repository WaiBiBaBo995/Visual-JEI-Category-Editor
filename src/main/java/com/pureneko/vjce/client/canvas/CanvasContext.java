package com.pureneko.vjce.client.canvas;
import net.minecraft.client.gui.GuiGraphics;
public final class CanvasContext {
    public final GuiGraphics graphics;
    public final float localMouseX, localMouseY, partialTick;
    public CanvasContext(GuiGraphics graphics,float x,float y,float partialTick){this.graphics=graphics;localMouseX=x;localMouseY=y;this.partialTick=partialTick;}
}
