package com.pureneko.vjce.mixin;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = WorldSceneRenderer.class, remap = false)
public interface SceneCameraAccess {
    @Invoker("computeCombinedMatrix")
    Matrix4f vjce$viewProjection();
}
