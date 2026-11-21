package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.pureneko.vjce.integration.IngredientAccess;
import com.pureneko.vjce.integration.JeiIngredientSearch;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.network.chat.Component;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;
final class IngredientPicker {
    static void open(WidgetGroup parent,Consumer<ITypedIngredient<?>> selected){show(parent,t->true,selected);}
    static void openType(WidgetGroup parent,IIngredientType<?> type,Consumer<ITypedIngredient<?>> selected){show(parent,t->t.getType()==type,selected);}
    static void openCatalysts(WidgetGroup parent,Consumer<ItemStack> selected){show(parent,t->t.getType()==VanillaTypes.ITEM_STACK,t->selected.accept((ItemStack)t.getIngredient()));}
    static void openBlocks(WidgetGroup parent,Predicate<ItemStack> filter,Consumer<ItemStack> selected){show(parent,t->t.getType()==VanillaTypes.ITEM_STACK&&filter.test((ItemStack)t.getIngredient()),t->selected.accept(((ItemStack)t.getIngredient()).copy()));}
    static ITypedIngredient<?> typed(Object o){if(o instanceof ITypedIngredient<?> typed)return typed;if(o instanceof ItemStack stack&&IngredientAccess.manager!=null)return IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK,stack).orElse(null);return null;}
    private static void show(WidgetGroup origin,Predicate<ITypedIngredient<?>> filter,Consumer<ITypedIngredient<?>> selected){new PickerDialog(origin,filter,selected);}

    /** Only the result grid scrolls; search, status and navigation stay reachable at every GUI scale. */
    static final class PickerDialog extends DialogWidget {
        private static final int PAGE_SIZE=40;
        final WidgetGroup panel=new WidgetGroup();
        final Forms.Form results=new Forms.Form(3,84,360,180);
        final TextFieldWidget search;
        final LabelWidget status=new LabelWidget(3,68,"");
        final ButtonWidget previous,next,close;
        final LabelWidget title=new LabelWidget(3,3,"vjce.picker.search");
        private final Predicate<ITypedIngredient<?>> filter;
        private final Consumer<ITypedIngredient<?>> selected;
        List<ITypedIngredient<?>> values=List.of();
        int page;
        private boolean searchFailed;

        PickerDialog(WidgetGroup origin,Predicate<ITypedIngredient<?>> filter,Consumer<ITypedIngredient<?>> selected){
            super(origin.getGui().mainGroup,true);this.filter=filter;this.selected=selected;
            setBackground(new ColorRectTexture(0xdd222222));
            panel.setBackground(new ColorRectTexture(Forms.BG));addWidget(panel);panel.addWidget(title);
            search=Forms.text(3,18,360,"",this::search);
            search.setHoverTooltips("vjce.picker.syntax");title.setHoverTooltips("vjce.picker.syntax");panel.addWidget(search);
            previous=Forms.button(3,44,110,"vjce.previous",()->changePage(-1));
            next=Forms.button(120,44,110,"vjce.next",()->changePage(1));
            close=Forms.button(240,44,110,"vjce.close",this::close);
            panel.addWidget(results);panel.addWidget(status);panel.addWidget(previous);panel.addWidget(next);panel.addWidget(close);
            resizeContent();search("");
        }

        void search(String text){
            page=0;searchFailed=false;
            try{values=JeiIngredientSearch.query(text).stream().filter(filter).toList();}
            catch(RuntimeException error){values=List.of();searchFailed=true;VisualJeiCategoryEditor.LOG.error("VJCE ingredient picker search failed",error);}
            search.setTextColor(values.isEmpty()?0xffff6666:0xffffffff);rebuild();
        }
        void changePage(int delta){int target=Math.max(0,Math.min(pages()-1,page+delta));if(target!=page){page=target;rebuild();}}
        int pages(){return Math.max(1,(values.size()+PAGE_SIZE-1)/PAGE_SIZE);}
        private void rebuild(){
            results.setScrollYOffset(0);results.clear();
            int index=0,cellWidth=Math.max(24,results.width()/3);
            for(var typed:values.stream().skip(page*(long)PAGE_SIZE).limit(PAGE_SIZE).toList()){
                results.addWidget(new IngredientCell(3+index%3*cellWidth,3+index/3*39,cellWidth-2,typed,()->{close();selected.accept(typed);}));index++;
            }
            status.setText(Component.translatable(searchFailed?"vjce.picker.error":values.isEmpty()?"vjce.picker.empty":"vjce.picker.results",values.size(),page+1,pages()).getString());
            previous.setActive(page>0);next.setActive(page+1<pages());
        }
        private void resizeContent(){
            int w=Math.max(120,Math.min(390,getSize().width-12)),h=Math.max(110,Math.min(300,getSize().height-12));
            panel.setSelfPosition(new Position((getSize().width-w)/2,(getSize().height-h)/2));panel.setSize(new Size(w,h));
            search.setSize(new Size(w-6,18));results.setSize(new Size(w-6,h-88));
            var buttons=List.of(previous,next,close);for(int i=0;i<3;i++){buttons.get(i).setSelfPosition(new Position(3+i*(w-6)/3,44));buttons.get(i).setSize(new Size((w-6)/3-2,18));}
        }
        @Override public void onScreenSizeUpdate(int width,int height){super.onScreenSizeUpdate(width,height);setSize(new Size(width,height));resizeContent();int offset=results.getScrollYOffset();rebuild();int rows=(Math.min(PAGE_SIZE,values.size()-page*PAGE_SIZE)+2)/3;results.setScrollYOffset(Math.min(offset,Math.max(0,3+rows*39-results.getSize().height)));}
    }
    static final class IngredientCell extends ButtonWidget {
        final ITypedIngredient<?> ingredient;
        private final TextTexture name;
        @SuppressWarnings({"rawtypes","unchecked"}) IngredientCell(int x,int y,int width,ITypedIngredient<?> ingredient,Runnable choose){
            super(x,y,width,36,new ColorRectTexture(Forms.INPUT).setRadius(3),c->choose.run());this.ingredient=ingredient;
            name=new TextTexture(((IIngredientHelper)IngredientAccess.manager.getIngredientHelper(ingredient.getType())).getDisplayName(ingredient.getIngredient())).setWidth(width-4).setType(TextTexture.TextType.ROLL);
            setHoverBorderTexture(1,0xffaaaaaa);
        }
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){super.drawInBackground(g,mx,my,pt);var p=getPosition();IngredientSlot.drawIngredient(g,ingredient,p.x+(getSize().width-16)/2f,p.y+2,16,16,1);name.draw(g,mx,my,p.x+2,p.y+20,getSize().width-4,12);}
        @Override @SuppressWarnings({"rawtypes","unchecked","removal"}) public void drawInForeground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){super.drawInForeground(g,mx,my,pt);if(isMouseOverElement(mx,my)){var renderer=IngredientAccess.manager.getIngredientRenderer((IIngredientType)ingredient.getType());getGui().getModularUIGui().setHoverTooltip(renderer.getTooltip(ingredient.getIngredient(),net.minecraft.world.item.TooltipFlag.Default.NORMAL),ItemStack.EMPTY,null,null);}}
    }
}
