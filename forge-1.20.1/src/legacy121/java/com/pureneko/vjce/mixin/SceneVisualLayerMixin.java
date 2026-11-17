package com.pureneko.vjce.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.UIVisualLayer;
import com.pureneko.vjce.client.BlockSceneElement;
import org.spongepowered.asm.mixin.*;

/** LDLib's visual-layer shader uses a custom sampler name, which vanilla does not bind automatically. */
@Mixin(value = UIVisualLayer.class, remap = false)
public abstract class SceneVisualLayerMixin {
    @Shadow @Final private UIElement element;
    @Shadow public abstract int textureId();

    @WrapMethod(method = "draw")
    private void vjce$sceneLayer(GUIContext context, Operation<Void> original) {
        if (!(element instanceof BlockSceneElement) && element.select("*").noneMatch(BlockSceneElement.class::isInstance)) {
            original.call(context); return;
        }
        var shader = LDLibShaders.getVisualLayerShader();
        Object previous = ((ShaderSamplerAccess) shader).vjce$samplers().get("DiffuseSampler");
        shader.setSampler("DiffuseSampler", textureId());
        try { original.call(context); }
        finally { shader.setSampler("DiffuseSampler", previous); }
    }
}
