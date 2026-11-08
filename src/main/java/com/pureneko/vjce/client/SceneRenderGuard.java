package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;

/** Only VJCE-owned scenes opt in. Never changes renderers or data in the source world. */
public final class SceneRenderGuard {
    private static final Map<WorldSceneRenderer, SceneRenderGuard> SCENES = new WeakHashMap<>();
    private final Map<BlockEntity, Boolean> failed = new WeakHashMap<>();

    static SceneRenderGuard attach(WorldSceneRenderer scene) {
        return SCENES.computeIfAbsent(scene, ignored -> new SceneRenderGuard());
    }
    static void detach(WorldSceneRenderer scene) { SCENES.remove(scene); }
    public static SceneRenderGuard get(WorldSceneRenderer scene) { return SCENES.get(scene); }
    void retry() { failed.clear(); }
    int failureCount() { return failed.size(); }

    public void render(WorldSceneRenderer scene, BlockEntityRenderer<BlockEntity> renderer, BlockEntity tile,
                       PoseStack pose, MultiBufferSource buffers, Consumer<PoseStack> draw) {
        if (failed.containsKey(tile)) return;
        try {
            // LDLib calls render directly, bypassing the dispatcher's native eligibility check.
            // Mekanism uses this to reject unformed multiblocks and empty fluid tanks.
            if (!renderer.shouldRender(tile, new Vec3(scene.getEyePos()))) return;
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
            try (var state = new SceneRenderState()) {
                // A renderer may push and then throw: do not leave LDLib's shared stack unbalanced.
                var isolated = new PoseStack();
                isolated.mulPose(pose.last().pose());
                RuntimeException renderFailure = null;
                try { draw.accept(isolated); }
                catch (RuntimeException failure) { renderFailure = failure; throw failure; }
                finally {
                    state.restoreTarget();
                    try { if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(); }
                    catch (RuntimeException cleanup) { if (renderFailure != null) renderFailure.addSuppressed(cleanup); else throw cleanup; }
                }
            }
        } catch (RuntimeException failure) {
            failed.put(tile, Boolean.TRUE);
            VisualJeiCategoryEditor.LOG.warn("VJCE skipped block entity {} at {} in scene preview; retry when reframing/reopening",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()), tile.getBlockPos(), failure);
        }
    }
}
