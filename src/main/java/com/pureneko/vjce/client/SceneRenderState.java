package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.RenderTargetScope;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.pureneko.vjce.mixin.ShaderLightsAccess;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Restore the scene's drawing state even if a third-party block entity renderer throws. */
final class SceneRenderState implements AutoCloseable {
    private final RenderTargetScope target = RenderTargetScope.capture();
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    private final VertexSorting sorting = RenderSystem.getVertexSorting();
    private final Vector3f light0 = new Vector3f(ShaderLightsAccess.vjce$directions()[0]);
    private final Vector3f light1 = new Vector3f(ShaderLightsAccess.vjce$directions()[1]);
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND),
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int srcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int[] box = new int[4];
    SceneRenderState() { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box); }
    void restoreTarget() { target.close(); }
    @Override public void close() {
        target.close();
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.getModelViewStack().set(modelView); RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderLights(light0, light1);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        // Force transitions because some mod renderers bypass GlStateManager's state cache.
        if (depth) { RenderSystem.disableDepthTest(); RenderSystem.enableDepthTest(); }
        else { RenderSystem.enableDepthTest(); RenderSystem.disableDepthTest(); }
        if (blend) { RenderSystem.disableBlend(); RenderSystem.enableBlend(); }
        else { RenderSystem.enableBlend(); RenderSystem.disableBlend(); }
        if (cull) { RenderSystem.disableCull(); RenderSystem.enableCull(); }
        else { RenderSystem.enableCull(); RenderSystem.disableCull(); }
        RenderSystem.depthMask(!depthMask); RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc == GL11.GL_ALWAYS ? GL11.GL_LEQUAL : GL11.GL_ALWAYS); RenderSystem.depthFunc(depthFunc);
        RenderSystem.blendFuncSeparate(srcRGB == GL11.GL_ZERO ? GL11.GL_ONE : GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
        if (scissor) { RenderSystem.disableScissor(); RenderSystem.enableScissor(box[0], box[1], box[2], box[3]); }
        else { RenderSystem.enableScissor(box[0], box[1], box[2], box[3]); RenderSystem.disableScissor(); }
    }
}
