package com.pureneko.vjce.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.pureneko.vjce.client.VjceSceneRenderer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer$BlockCompileContext", remap = false)
public abstract class SceneBlockModelsMixin {
    @Shadow @Final private WorldSceneRenderer renderer;

    @WrapOperation(method = "renderBlock", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
    private void vjce$transform(ModelBlockRenderer modelRenderer, BlockQuadOutput output,
                                float x, float y, float z, BlockAndTintGetter region, BlockPos pos,
                                BlockState state, BlockStateModel model, long seed, Operation<Void> render) {
        BlockQuadOutput transformed = output;
        if (renderer instanceof VjceSceneRenderer scene) {
            transformed = (qx, qy, qz, quad, instance) -> output.put(qx, qy, qz, scene.transformQuad(pos, quad), instance);
        }
        render.call(modelRenderer, transformed, x, y, z, region, pos, state, model, seed);
    }
}
