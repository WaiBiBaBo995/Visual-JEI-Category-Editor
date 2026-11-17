package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.NativeDrawing;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"com.simibubi.create.compat.jei.category.animations.AnimatedPress","com.simibubi.create.compat.jei.category.animations.AnimatedMixer","com.simibubi.create.compat.jei.category.animations.AnimatedCrushingWheels"},remap=false)
public abstract class CreateDrawingMixin {
    @Inject(method="draw",at=@At("HEAD"),remap=false)
    private void vjce$begin(GuiGraphics g,int x,int y,CallbackInfo ci){NativeDrawing.component(this,g,x-50,y-25,x+50,y+75,getClass().getSimpleName());}
    @Inject(method="draw",at=@At("RETURN"),remap=false)
    private void vjce$end(GuiGraphics g,int x,int y,CallbackInfo ci){NativeDrawing.end();}
}
