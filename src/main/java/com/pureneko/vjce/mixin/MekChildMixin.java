package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.MekElements;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.client.gui.element.GuiElement",remap=false)
public abstract class MekChildMixin {
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;renderShifted(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),remap=false)
    private void vjce$body(@Coerce Object e,GuiGraphics g,int x,int y,float t){MekElements.render(e,g,x,y,t,"renderShifted");}
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;onDrawBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),remap=false)
    private void vjce$background(@Coerce Object e,GuiGraphics g,int x,int y,float t){MekElements.render(e,g,x,y,t,"onDrawBackground");}
    @Redirect(method="onRenderForeground",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiElement;onRenderForeground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"),remap=false)
    private void vjce$foreground(@Coerce Object e,GuiGraphics g,int x,int y,int z,int total){MekElements.foreground(e,g,x,y,z,total);}
}
