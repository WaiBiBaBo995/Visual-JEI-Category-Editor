package com.pureneko.vjce.mixin;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.pureneko.vjce.client.VjceSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Freeze native entity interpolation together with the scene's tick clock. */
@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class SceneAnimationMixin {
    @ModifyVariable(method = "submitEntities", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float vjce$entityTime(float partialTick) {
        return (Object) this instanceof VjceSceneRenderer scene ? scene.entityPartialTick(partialTick) : partialTick;
    }
}
