package com.pureneko.vjce.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(ShaderInstance.class)
public interface ShaderSamplerAccess {
    @Accessor("samplerMap") Map<String, Object> vjce$samplers();
}
