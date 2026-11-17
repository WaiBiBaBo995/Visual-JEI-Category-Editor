package com.pureneko.vjce.integration;

import net.minecraft.client.gui.GuiGraphics;
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
    public static void discover(Object category,NativeDrawing drawing) {
        Class<?> base=category.getClass();while(base!=null&&!base.getName().equals("mekanism.client.jei.BaseRecipeCategory"))base=base.getSuperclass();if(base==null)return;
        try{var field=base.getDeclaredField("guiElements");field.setAccessible(true);discoverChildren((List<?>)field.get(category),drawing,null);}catch(ReflectiveOperationException e){throw new IllegalStateException("Mekanism 1.20.1 category elements",e);}
    }
    private static void discoverChildren(List<?> children,NativeDrawing drawing,String parentKey) {
        var counts=new HashMap<String,Integer>();
        for(Object child:children) {
            Class<?> display=child.getClass();while(display.isAnonymousClass()||display.getSimpleName().isEmpty())display=display.getSuperclass();String type=display.getSimpleName();int count=counts.merge(type,1,Integer::sum)-1;
            String key=(parentKey==null?"draw_component_mek_":parentKey+"__")+type+"_"+count;
            var widget=(net.minecraft.client.gui.components.AbstractWidget)child;
            drawing.component(child,key,type,widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight(),parentKey);
            discoverChildren((List<?>)call(child,"children",new Class<?>[0]),drawing,key);
        }
    }
    public static void render(Object element,GuiGraphics graphics,int x,int y,float tick,String method) {
        boolean active=NativeDrawing.active();
        if(active)NativeDrawing.component(element,graphics,number(element,"getRelativeX"),number(element,"getRelativeY"),number(element,"getRelativeX")+((net.minecraft.client.gui.components.AbstractWidget)element).getWidth(),number(element,"getRelativeY")+((net.minecraft.client.gui.components.AbstractWidget)element).getHeight(),element.getClass().getSimpleName());
        var mouse=NativeDrawing.componentMouse(x,y);x=(int)mouse.x;y=(int)mouse.y;
        try {call(element,method,new Class<?>[]{GuiGraphics.class,int.class,int.class,float.class},graphics,x,y,tick);}
        finally {if(active)NativeDrawing.end();}
    }
    public static void foreground(Object element,GuiGraphics graphics,int x,int y,int z,int total) {
        boolean active=NativeDrawing.active();
        if(active)NativeDrawing.component(element,graphics,number(element,"getRelativeX"),number(element,"getRelativeY"),number(element,"getRelativeX")+((net.minecraft.client.gui.components.AbstractWidget)element).getWidth(),number(element,"getRelativeY")+((net.minecraft.client.gui.components.AbstractWidget)element).getHeight(),element.getClass().getSimpleName());
        var mouse=NativeDrawing.componentMouse(x,y);x=(int)mouse.x;y=(int)mouse.y;
        try {call(element,"onRenderForeground",new Class<?>[]{GuiGraphics.class,int.class,int.class,int.class,int.class},graphics,x,y,z,total);

        }
        finally {if(active)NativeDrawing.end();}
    }
}
