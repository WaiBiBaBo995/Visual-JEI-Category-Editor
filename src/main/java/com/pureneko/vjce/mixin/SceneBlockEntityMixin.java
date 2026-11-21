package com.pureneko.vjce.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.pureneko.vjce.client.SceneRenderGuard;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WorldSceneRenderer.class, remap = false)
public abstract class SceneBlockEntityMixin {
    @WrapOperation(method = "lambda$submitBlockEntities$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"))
    private BlockEntityRenderState vjce$extract(BlockEntityRenderDispatcher dispatcher, BlockEntity tile,
                                                float partialTick, ModelFeatureRenderer.CrumblingOverlay overlay,
                                                Operation<BlockEntityRenderState> extract) {
        var guard = SceneRenderGuard.get((WorldSceneRenderer) (Object) this);
        if (guard == null) return extract.call(dispatcher, tile, partialTick, overlay);
        return guard.extract(tile, () -> extract.call(dispatcher, tile, partialTick, overlay));
    }
}
