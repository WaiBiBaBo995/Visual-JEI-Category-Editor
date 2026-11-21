package com.pureneko.vjce.client;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.*;
/** Captures both framebuffer bindings and viewport, including nested scene rendering. */
final class RenderTargetScope implements AutoCloseable {
    private final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int[] viewport=new int[4];
    private RenderTargetScope(){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);}
    static RenderTargetScope capture(){return new RenderTargetScope();}
    @Override public void close(){GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GlStateManager._viewport(viewport[0],viewport[1],viewport[2],viewport[3]);}
}
