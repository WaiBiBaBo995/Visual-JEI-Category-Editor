package com.pureneko.vjce.integration;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.lang.reflect.Proxy;
import java.util.*;

/** Test-only source categories exercise arbitrary recipe objects without adding gameplay recipes. */
public final class CategoryFixtures {
    private static final List<Probe> PROBES = List.of(new Probe("vjce_qa_a:shared", false, false), new Probe("vjce_qa_b:shared", false, false),
            new Probe("vjce_qa_a:empty", false, false), new Probe("vjce_qa_a:readonly", true, false), new Probe("vjce_qa_a:dynamic", false, true));
    public static void categories(IRecipeCategoryRegistration registration) { if (CompatibilityAudit.enabled()) registration.addRecipeCategories(PROBES.toArray(IRecipeCategory[]::new)); }
    public static void recipes(IRecipeRegistration registration) {
        if (CompatibilityAudit.enabled()) for (var p : PROBES) if (!p.type.getUid().getPath().equals("empty")) registration.addRecipes(p.type, List.of("source"));
    }
    private static final class Probe implements IRecipeCategory<String> {
        final RecipeType<String> type;
        final boolean rejectProxy, dynamic;
        Probe(String id, boolean rejectProxy, boolean dynamic) { type = new RecipeType<>(ResourceLocation.parse(id), String.class); this.rejectProxy = rejectProxy; this.dynamic = dynamic; }
        @Override public RecipeType<String> getRecipeType() { return type; }
        @Override public Component getTitle() { return Component.literal("Probe " + type.getUid()); }
        @Override public IDrawable getIcon() { return VjceJeiPlugin.helpers.getGuiHelper().createDrawableItemStack(new ItemStack(Items.STONE)); }
        @Override public int getWidth() { return 120; }
        @Override public int getHeight() { return 50; }
        @Override public ResourceLocation getRegistryName(String recipe) { return dynamic ? null : ResourceLocation.fromNamespaceAndPath(type.getUid().getNamespace(), type.getUid().getPath() + "/" + recipe); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, String recipe, IFocusGroup focuses) {
            if (rejectProxy && Proxy.isProxyClass(builder.getClass())) throw new IllegalArgumentException("Expected test-only capture restriction");
            builder.addInputSlot().setPosition(3, 10, 24, 24, mezz.jei.api.gui.placement.HorizontalAlignment.CENTER, mezz.jei.api.gui.placement.VerticalAlignment.CENTER).addItemStack(new ItemStack(Items.STONE));
            builder.addOutputSlot(86, 14).addItemStack(new ItemStack(Items.DIAMOND));
        }
        @Override public void draw(String recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
            graphics.fill(0, 0, 120, 50, 0xff38445a); graphics.drawString(Minecraft.getInstance().font, "Native custom drawing", 4, 3, 0xffffffff, false);
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, String recipe, IFocusGroup focuses) { builder.addText(Component.literal("Original text"), 65, 9).setPosition(30, 35);
            builder.addWidget(new mezz.jei.api.gui.widgets.IRecipeWidget(){
                @Override public net.minecraft.client.gui.navigation.ScreenPosition getPosition(){return new net.minecraft.client.gui.navigation.ScreenPosition(105,2);}
                @Override public void drawWidget(GuiGraphics graphics,double x,double y){graphics.fill(0,0,8,6,0xffeeaa44);}
            }); }
    }
}
