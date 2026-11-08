package com.pureneko.vjce.mixin;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.pureneko.vjce.client.SceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SceneRenderer draws models with entity sheets; retain LDLib's block entities, hooks and entities. */
@Mixin(value=WorldSceneRenderer.class,remap=false)
public abstract class SceneBlockModelsMixin {
    @Inject(method="renderSingleBlock",at=@At("HEAD"),cancellable=true)
    private void vjce$displayModels(CallbackInfo ci){if((Object)this instanceof SceneRenderer)ci.cancel();}
}
