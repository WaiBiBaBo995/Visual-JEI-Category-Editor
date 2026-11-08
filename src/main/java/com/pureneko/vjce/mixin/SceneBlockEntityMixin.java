package com.pureneko.vjce.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pureneko.vjce.client.SceneRenderGuard;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class SceneBlockEntityMixin {
    @WrapOperation(method = "renderTESR", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void vjce$nativeEligibility(BlockEntityRenderer<BlockEntity> renderer, BlockEntity tile, float partial,
                                      PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        var scene = (WorldSceneRenderer) (Object) this;
        var guard = SceneRenderGuard.get(scene);
        if (guard == null) original.call(renderer, tile, partial, pose, buffers, light, overlay);
        else guard.render(scene, renderer, tile, pose, buffers,
                isolated -> {if(scene instanceof com.pureneko.vjce.client.SceneRenderer owned)owned.transform(tile.getBlockPos(),isolated);original.call(renderer, tile, partial, isolated, buffers, light, overlay);});
    }
}
