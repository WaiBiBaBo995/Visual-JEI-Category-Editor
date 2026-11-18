package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.pureneko.vjce.client.canvas.CanvasElement;
import java.lang.reflect.Field;
import java.util.*;

/** The same compact property rows, groups, color strips and texture previews as LDLib1's editor. */
final class CanvasConfigurator {
    private CanvasConfigurator() {}
    static void build(ConfiguratorGroup root,CanvasElement element,RecipeLayoutView view) {
        var basic=group(root,"basic",false);
        fields(basic,element,view,"id","visible","hitTest");
        if(element.type.equals("group")){
            var children=new ArrayConfiguratorGroup<CanvasElement>("children",true,element::getChildren,(getter,setter)->{
                var child=getter.get();
                return new WrapperConfigurator(child.id,new com.lowdragmc.lowdraglib.gui.widget.Widget(0,0,80,50){
                    @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){
                        var p=getPosition();g.enableScissor(p.x,p.y,p.x+80,p.y+50);g.flush();g.pose().pushPose();
                        try{float scale=Math.min(76/Math.max(1,child.width),46/Math.max(1,child.height));g.pose().translate(p.x+2,p.y+2,0);g.pose().scale(scale,scale,1);g.pose().mulPoseMatrix(child.localMatrix().invert());child.render(g,-10000,-10000,pt);}finally{g.flush();g.pose().popPose();g.disableScissor();}
                    }
                });
            },true);
            children.setCanAdd(false);
            children.setOnRemove(child->{view.selection.removeIf(e->child.select("*").anyMatch(c->c==e));child.detach();child.dispose();});
            children.setOnReorder((index,child)->element.insertChild(index,child));
            children.setOnUpdate(list->{view.rebind();view.markAsDirty();});
            basic.addConfigurators(children);
        }
        var transform=group(root,"transform",false);
        fields(transform,element,view,"x","y","width","height","rotation","scaleX","scaleY","shearX");
        var appearance=group(root,"appearance",false);
        fields(appearance,element,view,"opacity","background","clip");
        if(element.type.equals("text"))fields(group(root,"text",false),element,view,"text","color");
        if(element.type.equals("progress"))fields(group(root,"progress",false),element,view,"progress","color");
        if(element instanceof TextureElement texture){
            var material=group(root,"material",false);
            material.addConfigurators(new GuiTextureConfigurator("vjce.inspector.field.texture",()->texture.image,value->{texture.setImage(value);view.rebind();view.markAsDirty();view.inspect();},true));
            var properties=group(material,"material_properties",false);
            texture.image.buildConfigurator(properties);
            properties.addListener(c->view.markAsDirty());
        }
        if(element instanceof NativePartElement||element instanceof BlockSceneElement||element instanceof CreateAnimationElement){
            var specific=group(root,element.type,false);
            if(element instanceof BlockSceneElement scene)specific.addConfigurators(new ButtonConfigurator("vjce.scene.configure",()->SceneEditor.open(view,scene)));
            for(var field:element.getClass().getFields())if(field.isAnnotationPresent(Persisted.class)&&field.getType()!=net.minecraft.nbt.CompoundTag.class)add(specific,element,field,view);
        }
    }
    private static ConfiguratorGroup group(ConfiguratorGroup parent,String key,boolean collapsed){var group=new ConfiguratorGroup("vjce.inspector."+key,collapsed);parent.addConfigurators(group);return group;}
    private static void fields(ConfiguratorGroup parent,CanvasElement element,RecipeLayoutView view,String... names){for(String name:names)try{add(parent,element,element.getClass().getField(name),view);}catch(NoSuchFieldException failure){throw new IllegalStateException(failure);}}
    private static Object value(Field field,Object element){try{return field.get(element);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private static void update(Field field,CanvasElement element,Object next,RecipeLayoutView view){try{
        Class<?> type=field.getType();
        if(next instanceof Number n){double v=n.doubleValue();if(!Double.isFinite(v))return;
            if((field.getName().equals("scaleX")||field.getName().equals("scaleY"))&&Math.abs(v)<.0001)return;
            if(type==float.class)field.setFloat(element,n.floatValue());else if(type==double.class)field.setDouble(element,v);else if(type==int.class)field.setInt(element,n.intValue());else if(type==long.class)field.setLong(element,n.longValue());
        }else field.set(element,next);
        if(element instanceof BlockSceneElement scene)scene.release();
        view.markAsDirty();
    }catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void add(ConfiguratorGroup parent,CanvasElement element,Field field,RecipeLayoutView view){
        String name=field.getName();var annotation=field.getAnnotation(Configurable.class);String label=annotation!=null&&!annotation.name().isEmpty()?annotation.name():"vjce.field."+name;
        if(Set.of("id","width","height","texture").contains(name))label="vjce.inspector.field."+name;
        var type=field.getType();Object initial=value(field,element);Configurator row;
        if(type==boolean.class)row=new BooleanConfigurator(label,()->(Boolean)value(field,element),next->update(field,element,next,view),(Boolean)initial,true);
        else if(type==String.class)row=new StringConfigurator(label,()->(String)value(field,element),next->update(field,element,next,view),(String)initial,true);
        else if(type.isEnum())row=new SelectorConfigurator(label,()->value(field,element),next->update(field,element,next,view),initial,true,Arrays.asList(type.getEnumConstants()),v->Forms.enumLabel((Enum<?>)v));
        else if(initial instanceof Number number){
            if(name.toLowerCase(Locale.ROOT).contains("color")||name.equals("background"))row=new ColorConfigurator(label,()->(Number)value(field,element),next->update(field,element,next,view),number,true);
            else{
                double min=-8192,max=8192,step=1;
                if(Set.of("width","height").contains(name)){min=.01;max=8192;}
                if(Set.of("opacity","progress").contains(name)){min=0;max=1;step=.05;}
                if(Set.of("scaleX","scaleY","shearX").contains(name)){min=-128;max=128;step=.1;}
                if(name.equals("cameraRange")){min=.2;max=128;step=.1;}
                if(name.equals("cameraPitch")){min=-89;max=89;}
                if(name.equals("speed")){min=0;max=20;step=.1;}
                if(name.equals("period")){min=1;max=100000;}
                row=new NumberConfigurator(label,()->(Number)value(field,element),next->update(field,element,next,view),number,true).setRange(min,max).setWheel(step);
            }
        }else return;
        parent.addConfigurators(row);
    }
}
