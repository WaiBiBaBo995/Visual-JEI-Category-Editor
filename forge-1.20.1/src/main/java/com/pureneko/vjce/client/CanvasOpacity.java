package com.pureneko.vjce.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Composite a whole element so item, scene and nested texture alpha behave consistently. */
public final class CanvasOpacity implements AutoCloseable {
    private final GuiGraphics graphics;
    private final float alpha;
    private final SceneRenderState state;
    private final TextureTarget target;
    private CanvasOpacity(GuiGraphics graphics,float alpha){
        this.graphics=graphics;this.alpha=alpha;graphics.flush();state=new SceneRenderState();
        var window=Minecraft.getInstance().getWindow();
        target=new TextureTarget(window.getWidth(),window.getHeight(),true,Minecraft.ON_OSX);
        RenderSystem.disableScissor();target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);
        state.close();target.bindWrite(true);
    }
    public static CanvasOpacity begin(GuiGraphics graphics,float opacity){return opacity<.9999f?new CanvasOpacity(graphics,Math.max(0,opacity)):null;}
    public static float inherited(com.pureneko.vjce.client.canvas.CanvasElement element){float alpha=1;for(var e=element;e!=null;e=e.getParent())alpha*=e.opacity;return alpha;}
    @Override public void close(){
        try{
            graphics.flush();state.close();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,target.getColorTextureId());
            var window=Minecraft.getInstance().getWindow();float width=window.getGuiScaledWidth(),height=window.getGuiScaledHeight();
            var buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);var matrix=new Matrix4f();
            buffer.vertex(matrix,0,height,0).uv(0,0).color(alpha,alpha,alpha,alpha).endVertex();
            buffer.vertex(matrix,width,height,0).uv(1,0).color(alpha,alpha,alpha,alpha).endVertex();
            buffer.vertex(matrix,width,0,0).uv(1,1).color(alpha,alpha,alpha,alpha).endVertex();
            buffer.vertex(matrix,0,0,0).uv(0,1).color(alpha,alpha,alpha,alpha).endVertex();
            Tesselator.getInstance().end();
        }finally{target.destroyBuffers();state.close();}
    }
}
