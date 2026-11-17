package com.pureneko.vjce.integration;
import com.pureneko.vjce.client.*;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.gui.inputs.*;
import mezz.jei.api.gui.widgets.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.*;
import java.util.*;
/** One focus-specific live source layout; data and placements never serialize Java recipe objects. */
@SuppressWarnings({"rawtypes","unchecked","removal"})
public final class NativeSession {
    public final NativePage page;
    public final CanvasRuntime ui;
    public final CategoryDocument.Recipe data;
    public List<String> slotKeys=List.of();
    public List<IRecipeSlotDrawable> actualSlots=List.of();
    private final IRecipeSlotsView completeSlots=()->List.copyOf(actualSlots);
    public NativeSession(IRecipeCategory original,Object recipe,CategoryDocument document,CategoryDocument.Recipe data,IFocusGroup focus){
        this.data=data;page=NativePage.create(original,recipe,document,data,focus,true);
        var content=(document.layout.isBlank()?page.template(document):RecipeUI.savedTemplate(document)).createUI();page.bind(content,document);RecipeUI.bind(content,document,data,true);ui=new CanvasRuntime(content);ui.init(document.width,document.height);
    }
    public CanvasElement root(){return ui.ui.rootElement;}
    private NativePartElement element(String key){return NativePage.part(root(),key);}
    private boolean visible(CanvasElement e){return NativePage.visible(e)&&(!(e instanceof NativePartElement p)||p.useSourceDrawing);}
    private ElementTransform transform(CanvasElement e,float w,float h){return new ElementTransform(root(),e,w,h);}
    private ElementTransform pageTransform(CanvasElement e){return transform(e,page.original.getWidth(),page.original.getHeight());}
    private void withTransform(GuiGraphics g,ElementTransform t,CanvasElement element,Runnable draw){g.flush();g.pose().pushPose();try(var alpha=CanvasOpacity.begin(g,CanvasOpacity.inherited(element))){t.apply(g);draw.run();g.flush();}finally{g.pose().popPose();}}
    public void drawBase(GuiGraphics g,IRecipeSlotsView slots,double x,double y){
        var e=element("native_layer");if(!visible(e))return;var t=pageTransform(e);var p=t.local(x,y);
        withTransform(g,t,e,()->{page.access.vjce$background().draw(g,page.layout.getRectWithBorder());page.drawBase(g,actualSlots.isEmpty()?slots:completeSlots,p.x,p.y);});
    }
    public void extras(IRecipeExtrasBuilder builder){
        if(builder instanceof com.pureneko.vjce.mixin.RecipeLayoutAccess a){a.vjce$decorators(List.of());a.vjce$shapeless(null);a.vjce$background((g,x,y,w,h)->{});}
        actualSlots=List.copyOf(builder.getRecipeSlots().getSlots());var slotsWidget=new CanvasJeiWidget(ui);
        for(int i=0;i<actualSlots.size()&&i<slotKeys.size();i++){var e=RecipeUI.slotElement(root(),slotKeys.get(i));if(e!=null&&visible(e))slotsWidget.bind(e,actualSlots.get(i));}
        builder.addSlottedWidget(slotsWidget,new ArrayList<>(slotsWidget.slots.values()));
        for(var part:page.parts.values()){
            var e=element(part.key());if(!visible(e))continue;
            builder.addWidget(new IRecipeWidget(){
                @Override public ScreenPosition getPosition(){return new ScreenPosition(0,0);}
                @Override public void drawWidget(GuiGraphics g,double x,double y){var t=transform(e,part.width(),part.height());var p=t.local(x,y);withTransform(g,t,e,()->page.widget(part,e).drawWidget(g,p.x,p.y));}
                @Override public void getTooltip(ITooltipBuilder tooltip,double x,double y){var t=transform(e,part.width(),part.height());if(t.contains(x,y)){var p=t.local(x,y);part.widget().getTooltip(tooltip,p.x,p.y);}}
            });
        }
        var overlay=element("native_overlay");
        builder.addWidget(new IRecipeWidget(){
            @Override public ScreenPosition getPosition(){return new ScreenPosition(0,0);}
            @Override public void tick(){page.layout.tick();}
            @Override public void drawWidget(GuiGraphics g,double x,double y){if(!visible(overlay))return;var t=pageTransform(overlay);var p=t.local(x,y);withTransform(g,t,overlay,()->page.drawOverlay(g,completeSlots,p.x,p.y));}
            @Override public void getTooltip(ITooltipBuilder tooltip,double x,double y){
                if(!visible(overlay))return;var t=pageTransform(overlay);if(!t.contains(x,y))return;var p=t.local(x,y);
                for(var widget:page.unknown)widget.getTooltip(tooltip,p.x-widget.getPosition().x(),p.y-widget.getPosition().y());
                for(var decorator:page.access.vjce$decorators())((mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator)decorator).decorateTooltips(tooltip,page.recipe,page.original,completeSlots,p.x,p.y);
            }
        });
        var source=page.layout.getInputHandler();
        builder.addInputHandler(new IJeiInputHandler(){
            @Override public ScreenRectangle getArea(){return overlay==null?new ScreenRectangle(0,0,0,0):pageTransform(overlay).area();}
            private org.joml.Vector2f point(double x,double y){var t=pageTransform(overlay);var a=t.area();var p=t.local(x+a.left(),y+a.top());var w=t.world(p.x,p.y);return page.drawing.sourcePoint(root(),w.x,w.y,p.x,p.y);}
            @Override public boolean handleInput(double x,double y,IJeiUserInput input){if(!visible(overlay))return false;var p=point(x,y);return inSource(p)&&source.handleInput(p.x,p.y,input);}
            @Override public boolean handleMouseScrolled(double x,double y,double delta){if(!visible(overlay))return false;var p=point(x,y);return inSource(p)&&source.handleMouseScrolled(p.x,p.y,delta);}
            @Override public boolean handleMouseDragged(double x,double y,com.mojang.blaze3d.platform.InputConstants.Key key,double dx,double dy){if(!visible(overlay))return false;var p=point(x,y);var d=pageTransform(overlay).direction(dx,dy);return inSource(p)&&source.handleMouseDragged(p.x,p.y,key,d.x,d.y);}
            @Override public void handleMouseMoved(double x,double y){if(visible(overlay)){var p=point(x,y);source.handleMouseMoved(p.x,p.y);}}
        });
    }
    private boolean inSource(org.joml.Vector2f p){return p.x>=0&&p.y>=0&&p.x<page.original.getWidth()&&p.y<page.original.getHeight();}
    public boolean legacyInput(double x,double y,com.mojang.blaze3d.platform.InputConstants.Key key){var e=element("native_overlay");if(!visible(e))return false;var p=pageTransform(e).local(x,y);p=page.drawing.sourcePoint(root(),x,y,p.x,p.y);return inSource(p)&&page.original.handleInput(page.recipe,p.x,p.y,key);}
    public void tooltip(ITooltipBuilder tooltip,IRecipeSlotsView slots,double x,double y){var e=element("native_layer");if(!visible(e))return;var t=pageTransform(e);if(!t.contains(x,y))return;var p=t.local(x,y);p=page.drawing.sourcePoint(root(),x,y,p.x,p.y);page.original.getTooltip(tooltip,page.recipe,actualSlots.isEmpty()?slots:completeSlots,p.x,p.y);}
}
