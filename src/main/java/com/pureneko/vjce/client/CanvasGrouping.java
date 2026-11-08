package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.Property;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A real UI parent: grouped drawing fragments share transforms and serialize with the normal template. */
final class CanvasGrouping {
    private record Placement(UIElement element,int index,Map<Property<?>,Object> layout,float x,float y,float w,float h) {}
    static void group(RecipeLayoutView view) {
        var selected=Arrays.asList(view.hierarchy.getSelectedNodes());if(selected.size()<2)return;
        var parent=selected.getFirst().getParent();if(parent==null||selected.stream().anyMatch(e->e.getParent()!=parent))return;
        var placements=new ArrayList<Placement>();float minX=Float.MAX_VALUE,minY=minX,maxX=-minX,maxY=-minX;
        for(var e:selected){var values=new LinkedHashMap<Property<?>,Object>();for(var p:e.getLayout().getPropertiesList())values.put(p,e.getLayout().getInline(p));
            float x=e.getPositionX()-parent.getContentX(),y=e.getPositionY()-parent.getContentY();float w=e.getSizeWidth(),h=e.getSizeHeight();
            placements.add(new Placement(e,e.getSiblingIndex(),values,x,y,w,h));minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x+w);maxY=Math.max(maxY,y+h);
        }
        float x=minX,y=minY,w=Math.max(1,maxX-minX),h=Math.max(1,maxY-minY);
        var group=new UIElement().setId("group_"+Long.toUnsignedString(System.nanoTime(),36));group.setOverflowVisible(true);
        group.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);l.left(x);l.top(y);l.width(w);l.height(h);});
        view.historyStack.pushHistory(Component.translatable("vjce.native.group"),EditAction.of(()->{
            parent.addChild(group);for(var p:placements){p.element.removeSelf();group.addChild(p.element);p.element.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);l.leftPercent((p.x-x)*100/w);l.topPercent((p.y-y)*100/h);l.widthPercent(p.w*100/w);l.heightPercent(p.h*100/h);});}
            view.hierarchy.loadUI(view.getCurrentUI());view.focusElement(group);
        },()->{
            for(var p:placements.stream().sorted(Comparator.comparingInt(Placement::index)).toList()){p.element.removeSelf();parent.addChildAt(p.element,p.index);restore(p.element,p.layout);}group.removeSelf();view.hierarchy.loadUI(view.getCurrentUI());
        }));view.markAsDirty();
    }
    @SuppressWarnings({"unchecked","rawtypes"})private static void restore(UIElement e,Map<Property<?>,Object> values){values.forEach((k,v)->e.getLayout().setInline((Property)k,v));}
}
