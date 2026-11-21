package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.math.PositionedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Use LDLib's scene submission and transform geometry before its block meshes are built. */
public final class VjceSceneRenderer extends FBOWorldSceneRenderer implements ISceneBlockRenderHook {
    private final Map<BlockPos, CompoundTag> transforms = new HashMap<>();
    private final Consumer<RuntimeException> onFailure;
    private boolean cameraActive;
    private boolean entitiesPlaying;
    private double entityBudget, entitySpeed;
    private float heldEntityPartialTick;

    VjceSceneRenderer(Level world, Consumer<RuntimeException> onFailure) {
        super(world, 128, 128);
        this.onFailure = onFailure;
        SceneRenderGuard.attach(this);
    }

    void setBlockTransforms(ListTag rows) {
        transforms.clear();
        for (var tag : rows) {
            var row = (CompoundTag) tag;
            var pos = new BlockPos(row.getIntOr("x", 0), row.getIntOr("y", 0), row.getIntOr("z", 0));
            transforms.put(pos, row.copy());
        }
    }

    @Override
    public void applyBESR(Level world, BlockPos pos, BlockEntity tile, PoseStack pose, float partialTick) {
        var row = transforms.get(pos);
        if (row != null) SceneTransforms.block(pose, row);
    }

    public BakedQuad transformQuad(BlockPos pos, BakedQuad quad) {
        var row = transforms.get(pos);
        if (row == null || !SceneTransforms.hasBlockTransform(row)) return quad;
        var pose = new PoseStack();
        SceneTransforms.block(pose, row);
        var transform = pose.last().pose();
        var normal = pose.last().normal().transform(quad.direction().step(), new Vector3f());
        var face = Direction.getApproximateNearest(normal.x, normal.y, normal.z);
        return new BakedQuad(
                transform.transformPosition(quad.position0(), new Vector3f()),
                transform.transformPosition(quad.position1(), new Vector3f()),
                transform.transformPosition(quad.position2(), new Vector3f()),
                transform.transformPosition(quad.position3(), new Vector3f()),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), face, quad.materialInfo());
    }

    void entityTime(boolean playing, double budget, double speed) {
        entitiesPlaying = playing;
        entityBudget = budget;
        entitySpeed = Double.isFinite(speed) ? Math.max(0, Math.min(20, speed)) : 1;
    }

    public float entityPartialTick(float nativePartialTick) {
        if (entitiesPlaying) heldEntityPartialTick = (float) Math.max(0, Math.min(1, entityBudget + nativePartialTick * entitySpeed));
        return heldEntityPartialTick;
    }

    @Override
    protected void setupCamera(PositionedRect viewport) {
        super.setupCamera(viewport);
        cameraActive = true;
    }

    @Override
    protected void resetCamera() {
        try {
            super.resetCamera();
        } finally {
            cameraActive = false;
        }
    }

    @Override
    protected void drawWorld(RenderBuffers buffers) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var previousCamera = dispatcher.camera;
        var previousTarget = dispatcher.crosshairPickEntity;
        try {
            super.drawWorld(buffers);
        } catch (RuntimeException failure) {
            try {
                buffers.bufferSource().endBatch();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            dispatcher.prepare(previousCamera, previousTarget);
        }
    }

    @Override
    public void drawScene(float x, float y, float width, float height, float mouseX, float mouseY) {
        try {
            super.drawScene(x, y, width, height, mouseX, mouseY);
        } catch (RuntimeException failure) {
            onFailure.accept(failure);
        } finally {
            if (cameraActive) resetCamera();
        }
    }

    @Override
    public void releaseResource() {
        SceneRenderGuard.detach(this);
        super.releaseResource();
    }
}
