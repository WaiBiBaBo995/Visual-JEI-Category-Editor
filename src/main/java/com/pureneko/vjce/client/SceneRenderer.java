package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.RenderTargetScope;
import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.math.PositionedRect;
import com.mojang.blaze3d.systems.RenderSystem;
import com.pureneko.vjce.mixin.EntityDispatcherAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11;

/** Render to a local texture, then let LDLib apply the full GUI transform to all four corners. */
public final class SceneRenderer extends FBOWorldSceneRenderer {
    private final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.nbt.CompoundTag> transforms=new java.util.HashMap<>();
    void setBlockTransforms(net.minecraft.nbt.ListTag rows){for(var tag:rows){var row=(net.minecraft.nbt.CompoundTag)tag;transforms.put(new net.minecraft.core.BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")),row.copy());}}
    public void transform(net.minecraft.core.BlockPos pos,com.mojang.blaze3d.vertex.PoseStack pose){var row=transforms.get(pos);if(row!=null)SceneTransforms.block(pose,row);}
    private boolean cameraActive;
    private boolean entitiesPlaying;
    private double entityBudget, entitySpeed;
    private float heldEntityPartialTick;
    private final SceneBlockModels blockModels = new SceneBlockModels(this);
    SceneRenderer(Level world) { super(world, 128, 128); SceneRenderGuard.attach(this); }

    @Override public void setFBOSize(int width, int height) {
        // RenderTarget.resize binds framebuffer 0; keep resizing inside the current UI surface.
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try (var ignored = RenderTargetScope.capture()) { super.setFBOSize(width, height); }
        finally { if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest(); }
    }

    void entityTime(boolean playing, double budget, double speed) {
        entitiesPlaying = playing; entityBudget = budget;
        entitySpeed = Double.isFinite(speed) ? Math.max(0, Math.min(20, speed)) : 1;
    }
    /** Called only for this renderer by SceneAnimationMixin; other LDLib scenes keep their clock. */
    public float entityPartialTick(float nativePartialTick) {
        if (entitiesPlaying) heldEntityPartialTick = (float) Math.max(0, Math.min(1, entityBudget + nativePartialTick * entitySpeed));
        return heldEntityPartialTick;
    }

    @Override protected void setupCamera(PositionedRect viewport) {
        super.setupCamera(viewport);
        cameraActive = true;
    }
    @Override protected void resetCamera() {
        try { super.resetCamera(); } finally { cameraActive = false; }
    }
    @Override public void drawScene(float x, float y, float width, float height, float mouseX, float mouseY) {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] box = new int[4]; GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
        var target = RenderTargetScope.capture();
        try { super.drawScene(x, y, width, height, mouseX, mouseY); }
        finally {
            try { if (cameraActive) resetCamera(); }
            finally {
                target.close();
                // LDLib changes GL directly; force the state transition to resync GlStateManager's cache.
                if (scissor) {
                    RenderSystem.disableScissor(); RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
                } else {
                    RenderSystem.enableScissor(box[0], box[1], box[2], box[3]); RenderSystem.disableScissor();
                }
            }
        }
    }
    @Override protected void drawWorld() {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var access = (EntityDispatcherAccess) dispatcher;
        var previousLevel = access.vjce$level();
        var previousCamera = dispatcher.camera;
        var previousCrosshair = dispatcher.crosshairPickEntity;
        var previousOrientation = access.vjce$orientation();
        boolean shadow = access.vjce$shadow(), hitBoxes = access.vjce$hitBoxes();
        try (var lighting = new SceneLighting()) {
            dispatcher.prepare(world, camera, null);
            dispatcher.setRenderShadow(false); dispatcher.setRenderHitBoxes(false);
            try { blockModels.render(); super.drawWorld(); }
            catch (RuntimeException failure) {
                // Drain any partial model batches into this FBO before restoring the GUI target.
                try { Minecraft.getInstance().renderBuffers().bufferSource().endBatch(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } finally {
            access.vjce$level(previousLevel); dispatcher.camera = previousCamera;
            dispatcher.crosshairPickEntity = previousCrosshair; access.vjce$orientation(previousOrientation);
            dispatcher.setRenderShadow(shadow); dispatcher.setRenderHitBoxes(hitBoxes);
        }
    }
    @Override public void releaseResource() { SceneRenderGuard.detach(this); try { blockModels.close(); } finally { super.releaseResource(); } }
}
