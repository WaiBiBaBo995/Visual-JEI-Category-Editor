package com.pureneko.vjce.client;

import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A single-recipe JEI frame in page coordinates. It never belongs to the editable canvas tree. */
final class JeiPageReference {
    private static final int ACCENT=0xff409eae;
    record Bounds(int x,int y,int width,int height) {}

    static Bounds bounds(CategoryDocument doc){
        int width=Math.max(198,doc.width+36);
        return new Bounds(-(width-doc.width)/2,-46,width,doc.height+58);
    }

    static void background(GuiGraphics g,CategoryDocument doc){
        var frame=bounds(doc);var textures=Internal.getTextures();
        var font=Minecraft.getInstance().font;
        g.flush();
        try(var state=new SceneRenderState()){
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
            // Use JEI's current sprites, including resource pack replacements.
            textures.getTabSelected().draw(g,frame.x+6,frame.y-24);
            textures.getRecipeOptionsTab().draw(g,frame.x-22,frame.y+frame.height-44,28,44);
            textures.getRecipeGuiBackground().draw(g,frame.x,frame.y,frame.width,frame.height);
            textures.getRecipeBackground().draw(g,-4,-4,doc.width+8,doc.height+8);
            for(int row=0;row<2;row++){
                int y=frame.y+6+row*15;
                textures.getButtonForState(false,false,false).draw(g,frame.x+6,y,13,13);
                textures.getArrowPrevious().draw(g,frame.x+9,y+3);
                textures.getButtonForState(false,false,false).draw(g,frame.x+frame.width-19,y,13,13);
                textures.getArrowNext().draw(g,frame.x+frame.width-16,y+3);
            }
            textures.getButtonForState(false,false,false).draw(g,doc.width+6,0,13,13);
            textures.getRecipeTransfer().draw(g,doc.width+7,1);
            textures.getBookmarksFirst().draw(g,frame.x-16,frame.y+frame.height-38);
            textures.getCraftableFirst().draw(g,frame.x-16,frame.y+frame.height-22);
            var original=VjceJeiPlugin.originals.get(doc.target);
            IDrawable icon=original==null?null:original.getIcon();
            if(icon!=null){g.pose().pushPose();try{g.pose().translate(frame.x+10,frame.y-20,0);g.pose().scale(16f/Math.max(1,icon.getWidth()),16f/Math.max(1,icon.getHeight()),1);icon.draw(g);}finally{g.flush();g.pose().popPose();}}
            else{var id=ResourceLocation.tryParse(doc.icon);if(id!=null){var stack=new ItemStack(BuiltInRegistries.ITEM.get(id));var typed=IngredientPicker.typed(stack);IngredientSlot.drawIngredient(g,typed,frame.x+10,frame.y-20,16,16,1);}}
            String title=font.plainSubstrByWidth(doc.title,frame.width-68);
            g.drawString(font,title,frame.x+(frame.width-font.width(title))/2,frame.y+8,0xff404040,false);
            g.drawString(font,"1/1",frame.x+(frame.width-font.width("1/1"))/2,frame.y+23,0xff404040,false);
            g.flush();
        }
    }

    static void guides(GuiGraphics g,CategoryDocument doc){
        // The outline describes the real category draw area, even after moving/scaling its root.
        g.renderOutline(0,0,doc.width,doc.height,ACCENT);
        g.fill(-3,-1,4,1,ACCENT);g.fill(-1,-3,1,4,ACCENT);
        var font=Minecraft.getInstance().font;
        g.drawString(font,"(0, 0)",0,-14,ACCENT,false);
        String size=Component.translatable("vjce.reference.area",doc.width,doc.height).getString();
        g.drawString(font,size,0,doc.height+15,ACCENT,false);
    }
}
