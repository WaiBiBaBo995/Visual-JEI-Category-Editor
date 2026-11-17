package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.MekElements;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.client.jei.BaseRecipeCategory",remap=false)
public abstract class MekCategoryMixin {
    @Redirect(method="lambda$renderElements$2",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiTexturedElement;renderShifted(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),remap=false)
    private static void vjce$body(@Coerce Object e,GuiGraphics g,int x,int y,float t){MekElements.render(e,g,x,y,t,"renderShifted");}
    @Redirect(method="lambda$renderElements$3",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiTexturedElement;onDrawBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),remap=false)
    private static void vjce$background(@Coerce Object e,GuiGraphics g,int x,int y,float t){MekElements.render(e,g,x,y,t,"onDrawBackground");}
    @Redirect(method="renderElements",at=@At(value="INVOKE",target="Lmekanism/client/gui/element/GuiTexturedElement;onRenderForeground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"),remap=false)
    private void vjce$foreground(@Coerce Object e,GuiGraphics g,int x,int y,int z,int total){MekElements.foreground(e,g,x,y,z,total);}
}
