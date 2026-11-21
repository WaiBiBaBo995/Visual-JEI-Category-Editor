package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.MekElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.client.gui.element.GuiElement",remap=false)
public abstract class MekChildMixin {
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;renderShifted(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),remap=false,require=0)
    private void vjce$body(@Coerce Object e,GuiGraphicsExtractor g,int x,int y,float t){MekElements.render(e,g,x,y,t,"renderShifted");}
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;onDrawBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),remap=false,require=0)
    private void vjce$background(@Coerce Object e,GuiGraphicsExtractor g,int x,int y,float t){MekElements.render(e,g,x,y,t,"onDrawBackground");}
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;onRenderForeground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"),remap=false,require=0)
    private void vjce$foreground(@Coerce Object e,GuiGraphicsExtractor g,int x,int y){MekElements.foreground(e,g,x,y);}
}
