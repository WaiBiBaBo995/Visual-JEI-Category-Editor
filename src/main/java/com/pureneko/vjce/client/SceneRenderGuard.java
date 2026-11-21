package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Isolate failing block-entity renderers in VJCE-owned previews. */
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

    public <T> T extract(BlockEntity tile, Supplier<T> renderer) {
        if (failed.containsKey(tile)) return null;
        try {
            return renderer.get();
        } catch (RuntimeException failure) {
            failed.put(tile, Boolean.TRUE);
            VisualJeiCategoryEditor.LOG.warn("VJCE skipped block entity {} at {} in scene preview; retry when reframing/reopening",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()), tile.getBlockPos(), failure);
            return null;
        }
    }
}
