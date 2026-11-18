package com.pureneko.vjce.client;
import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.integration.ItemPresentationCodec;
import net.minecraft.world.item.ItemStack;
final class CategoryCatalystEditor {
    static void addTo(Forms.Form form,CategoryDocument document,Runnable refresh){form.label("vjce.field.catalysts");form.button("vjce.catalyst.choose",()->IngredientPicker.openCatalysts(form,stack->{if(document.catalysts.stream().noneMatch(e->{try{return ItemStack.isSameItemSameTags(ItemPresentationCodec.decode(e),stack);}catch(RuntimeException ex){return false;}})){document.catalysts.add(ItemPresentationCodec.encode(stack));refresh.run();}}));
        for(int i=0;i<document.catalysts.size();i++){int index=i;try{form.label(ItemPresentationCodec.decode(document.catalysts.get(i)).getHoverName().getString());}catch(RuntimeException e){form.label("vjce.catalyst.missing");}form.button("vjce.catalyst.remove",()->{document.catalysts.remove(index);refresh.run();});}}
}
