package com.pureneko.vjce.integration;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.pureneko.vjce.mixin.TooltipAccess;
import com.pureneko.vjce.mixin.WidgetTooltipAccess;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional component adapter, with no Mekanism class linkage when Mekanism is absent. */
public final class MekElements {
    private static final Map<String,Method> METHODS=new ConcurrentHashMap<>();
    private MekElements() {}
    private static Object call(Object object,String name,Class<?>[] types,Object... args) {
        String key=object.getClass().getName()+"#"+name+Arrays.toString(types);
        try { var method=METHODS.computeIfAbsent(key,k->{try{var m=object.getClass().getMethod(name,types);m.trySetAccessible();return m;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});return method.invoke(object,args); }
        catch(ReflectiveOperationException e){throw new IllegalStateException("Mekanism component API: "+name,e);}
    }
    private static int number(Object object,String name){return (int)call(object,name,new Class<?>[0]);}
    public static net.minecraft.network.chat.Component sourceTooltip(Object element) {
        if (element instanceof WidgetTooltipAccess widget && widget.vjce$tooltip().get() instanceof TooltipAccess tooltip) {
            return tooltip.vjce$message();
        }
        return null;
    }
    public static Object listenerElement(Object listener) {
        if(!listener.getClass().getName().equals("mekanism.client.recipe_viewer.jei.MekJeiWidget"))return null;
        try{var f=listener.getClass().getDeclaredField("element");f.setAccessible(true);return f.get(listener);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static void discover(Object category,NativeDrawing drawing) {
        Class<?> base=category.getClass();while(base!=null&&!base.getName().equals("mekanism.client.recipe_viewer.jei.BaseRecipeCategory"))base=base.getSuperclass();if(base==null)return;
        discoverChildren(category,drawing,null);
    }
    private static void discoverChildren(Object parent,NativeDrawing drawing,String parentKey) {
        var counts=new HashMap<String,Integer>();
        for(Object child:(List<?>)call(parent,"children",new Class<?>[0])) {
            Class<?> display=child.getClass();while(display.isAnonymousClass()||display.getSimpleName().isEmpty())display=display.getSuperclass();String type=display.getSimpleName();int count=counts.merge(type,1,Integer::sum)-1;
            String key=(parentKey==null?"draw_component_mek_":parentKey+"__")+type+"_"+count;
            drawing.component(child,key,type,number(child,"getX"),number(child,"getY"),number(child,"getWidth"),number(child,"getHeight"),parentKey);
            discoverChildren(child,drawing,key);
        }
    }
    public static void render(Object element,GuiGraphicsExtractor graphics,int x,int y,float tick,String method) {
        boolean active=NativeDrawing.active();
        if(active)NativeDrawing.component(element,graphics,number(element,"getRelativeX"),number(element,"getRelativeY"),number(element,"getRelativeX")+number(element,"getWidth"),number(element,"getRelativeY")+number(element,"getHeight"),element.getClass().getSimpleName());
        var mouse=NativeDrawing.componentMouse(x,y);x=(int)mouse.x;y=(int)mouse.y;
        try {call(element,method,new Class<?>[]{GuiGraphicsExtractor.class,int.class,int.class,float.class},graphics,x,y,tick);}
        finally {if(active)NativeDrawing.end();}
    }
    public static void foreground(Object element,GuiGraphicsExtractor graphics,int x,int y) {
        boolean active=NativeDrawing.active();
        if(active)NativeDrawing.component(element,graphics,number(element,"getRelativeX"),number(element,"getRelativeY"),number(element,"getRelativeX")+number(element,"getWidth"),number(element,"getRelativeY")+number(element,"getHeight"),element.getClass().getSimpleName());
        var mouse=NativeDrawing.componentMouse(x,y);x=(int)mouse.x;y=(int)mouse.y;
        try {call(element,"onRenderForeground",new Class<?>[]{GuiGraphicsExtractor.class,int.class,int.class},graphics,x,y);
            if(active){call(element,"updateTooltip",new Class<?>[]{int.class,int.class},x,y);NativeDrawing.tooltip(sourceTooltip(element));}
        }
        finally {if(active)NativeDrawing.end();}
    }
}
