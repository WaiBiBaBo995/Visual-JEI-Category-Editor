package com.pureneko.vjce.mixin;

import mezz.jei.api.gui.placement.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(targets = "mezz.jei.common.gui.elements.TextWidget", remap = false)
public interface TextWidgetAccess {
    @Accessor("text") List<FormattedText> vjce$text();
    @Accessor("font") Font vjce$font();
    @Accessor("colorOverride") Integer vjce$color();
    @Accessor("shadow") boolean vjce$shadow();
    @Accessor("lineSpacing") int vjce$spacing();
    @Accessor("horizontalAlignment") HorizontalAlignment vjce$horizontal();
    @Accessor("verticalAlignment") VerticalAlignment vjce$vertical();
}
