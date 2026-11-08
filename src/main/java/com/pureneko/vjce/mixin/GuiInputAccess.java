package com.pureneko.vjce.mixin;
import java.util.List;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="mezz.jei.library.gui.recipes.RecipeLayoutInputHandler",remap=false)
public interface GuiInputAccess { @Accessor("guiEventListeners") List<IJeiGuiEventListener> vjce$listeners(); }
