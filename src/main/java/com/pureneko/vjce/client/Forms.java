package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.utils.*;
import com.google.gson.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
final class Forms {
    static final List<String> ROLES=List.of("INPUT","OUTPUT","CATALYST","RENDER_ONLY");
    static final List<String> BINDING_PROPERTIES=List.of("text","progress","visible","color");
    static final int BG=0xff222222, INPUT=0xff3c3c3c;
    static ButtonWidget button(int x,int y,int width,String key,Runnable action){
        return button(x,y,width,key,action,()->false);
    }
    static ButtonWidget button(int x,int y,int width,String key,Runnable action,BooleanSupplier selected){
        var label=new TextTexture(key).setWidth(Math.max(8,width-6)).setType(TextTexture.TextType.ROLL);
        var hover=new TextTexture(key).setWidth(Math.max(8,width-6)).setType(TextTexture.TextType.ROLL);
        var background=new ColorRectTexture(INPUT).setRadius(9);
        var button=new ButtonWidget(x,y,width,18,new GuiTextureGroup(background,label),c->action.run()){
            @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){background.setColor(selected.getAsBoolean()?0xff287a3b:INPUT);int available=Math.max(8,getSize().width-6);if(label.width!=available){label.setWidth(available);hover.setWidth(available);}super.drawInBackground(g,mx,my,pt);}
        };
        button.setHoverTexture(new ColorBorderTexture(1,0xffaaaaaa).setRadius(9),hover);
        button.setHoverTooltips(key);return button;
    }
    static TextFieldWidget text(int x,int y,int width,String value,Consumer<String> changed){return new TextFieldWidget(x,y,width,18,null,changed).setBordered(false).setBackground(new ColorRectTexture(INPUT).setRadius(9)).setTextColor(-1).setCurrentString(value==null?"":value).setMaxStringLength(32767);}
    /** A compact header remains reachable when the panel is collapsed. */
    static final class Sidebar extends WidgetGroup {
        final WidgetGroup content;
        int expandedWidth;
        boolean collapsed;
        Runnable onResize=()->{};
        private final ButtonWidget toggle;
        private final LabelWidget title;
        private final boolean right;
        private long animationStart;
        private int animationFrom;
        Sidebar(int x,int y,int w,int h,String key,WidgetGroup content){
            super(x,y,w,h);this.content=content;expandedWidth=w;right=x>0;
            setBackground(new GuiTextureGroup(new ColorRectTexture(BG),new ColorBorderTexture(1,0xff555555)));
            title=new LabelWidget(right?18:4,5,key);addWidget(title);
            toggle=new ButtonWidget(right?2:w-15,2,12,12,new GuiTextureGroup(new ColorBorderTexture(1,0xff666666),right?com.lowdragmc.lowdraglib.gui.editor.Icons.RIGHT:com.lowdragmc.lowdraglib.gui.editor.Icons.LEFT),c->setCollapsed(!collapsed));addWidget(toggle);
            content.setSelfPosition(new Position(1,18));content.setSize(new Size(w-2,h-19));addWidget(content);
        }
        int occupied(){return getSize().width;}
        void setCollapsed(boolean value){if(collapsed==value)return;collapsed=value;animationFrom=occupied();animationStart=System.nanoTime();content.setVisible(true);content.setActive(!value);toggle.setButtonTexture(new ColorBorderTexture(1,0xff666666),(value==right)?com.lowdragmc.lowdraglib.gui.editor.Icons.LEFT:com.lowdragmc.lowdraglib.gui.editor.Icons.RIGHT);toggle.setHoverTooltips(value?"vjce.panel.expand":"vjce.panel.collapse");}
        void resizePanel(int width,int height){
            int next=Math.max(50,width),current=occupied();
            if(next!=expandedWidth){double ratio=(next-16d)/(expandedWidth-16d);current=16+(int)Math.round((current-16)*ratio);animationFrom=16+(int)Math.round((animationFrom-16)*ratio);expandedWidth=next;}
            layoutWidth(animationStart==0?(collapsed?16:expandedWidth):current,height);
        }
        private void layoutWidth(int width,int height){
            height=Math.max(20,height);if(getSize().width==width&&getSize().height==height&&content.getSize().width==expandedWidth-2)return;
            setSize(new Size(width,Math.max(20,height)));
            content.setSelfPosition(new Position(right?1:1-(expandedWidth-width),18));
            if(content instanceof Form form)form.resizeForm(expandedWidth-2,Math.max(20,height-19));else content.setSize(new Size(expandedWidth-2,Math.max(20,height-19)));
            title.setSelfPosition(new Position(right?18:4-(expandedWidth-width),5));title.setVisible(width>16);
            toggle.setSelfPosition(new Position(right?2:width-15,2));content.setVisible(width>16);onResize.run();
        }
        void advanceAnimation(long now){if(animationStart==0)return;double t=Math.min(1,(now-animationStart)/500_000_000d);double eased=1-(1-t)*(1-t);layoutWidth((int)Math.round(animationFrom+((collapsed?16:expandedWidth)-animationFrom)*eased),getSize().height);if(t>=1)animationStart=0;}
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){var p=getPosition();g.enableScissor(p.x,p.y,p.x+getSize().width,p.y+getSize().height);try{super.drawInBackground(g,mx,my,pt);}finally{g.flush();g.disableScissor();}}
        @Override public boolean mouseClicked(double x,double y,int button){return isMouseOverElement(x,y)&&super.mouseClicked(x,y,button);}
        @Override public boolean mouseWheelMove(double x,double y,double delta){return isMouseOverElement(x,y)&&super.mouseWheelMove(x,y,delta);}
    }
    static String enumLabel(Enum<?> value){
        String key="vjce.enum."+value.getDeclaringClass().getSimpleName()+"."+value.name();return Component.translatable(key).getString();
    }
    static final class Form extends DraggableScrollableWidgetGroup {
        int cursor=3;
        private boolean embedded;
        Form(int x,int y,int w,int h){super(x,y,Math.max(20,w),Math.max(20,h));setYScrollBarWidth(4);setYBarStyle(null,new ColorRectTexture(0xff999999).setRadius(2));setBackground(new ColorRectTexture(BG));}
        void clear(){clearAllWidgets();cursor=3;}
        int width(){return getSize().width-12;}
        void resizeForm(int w,int h){
            w=Math.max(20,w);h=Math.max(20,h);if(getSize().width==w&&getSize().height==h)return;
            int oldWidth=width(),offset=getScrollYOffset(),dy=0;isComputingMax=true;
            try{setSize(new Size(w,h));for(var child:widgets){int oldHeight=child.getSize().height;child.addSelfPosition(0,dy);
                if(child instanceof Form form)form.resizeForm(width(),Math.max(20,h-child.getSelfPosition().y-offset));
                else if(child.getSize().width>=oldWidth-5)child.setSize(new Size(Math.max(1,width()),child.getSize().height));
                dy+=child.getSize().height-oldHeight;
            }cursor+=dy;}finally{isComputingMax=false;}
            computeMax();setScrollYOffset(Math.min(offset,Math.max(0,getMaxHeight()-getSize().height)));
        }
        /** The ConfigPanel owns scrolling; this form only lays out its full-height fields. */
        void embed(){embedded=true;setScrollYOffset(0);setYScrollBarWidth(0);setScrollable(false);setUseScissor(false);setSize(new Size(getSize().width,cursor+3));computeMax();widgets.forEach(w->w.setVisible(true));}
        @Override public boolean mouseWheelMove(double x,double y,double delta){
            if(!embedded)return super.mouseWheelMove(x,y,delta);
            if(!isMouseOverElement(x,y))return false;
            for(int i=widgets.size()-1;i>=0;i--){var child=widgets.get(i);if(child.isVisible()&&child.isActive()&&child.mouseWheelMove(x,y,delta))return true;}
            return false;
        }
        void label(String key){var label=new TextBoxWidget(3,cursor,width(),List.of(key)).setFontColor(0xffeeeeee);addWidget(label);cursor+=Math.max(15,label.getSize().height+3);}
        ButtonWidget button(String key,Runnable action){var b=Forms.button(3,cursor,width(),key,action);addWidget(b);cursor+=21;return b;}
        ButtonWidget button(String key,Runnable action,BooleanSupplier selected){var b=Forms.button(3,cursor,width(),key,action,selected);addWidget(b);cursor+=21;return b;}
        void buttonRow(List<String> labels,List<Runnable> actions){
            var row=new WidgetGroup(3,cursor,width(),18){@Override protected void onSizeUpdate(){super.onSizeUpdate();int n=widgets.size();for(int i=0;i<n;i++){widgets.get(i).setSelfPosition(new Position(i*getSize().width/n,0));widgets.get(i).setSize(new Size(Math.max(1,(i+1)*getSize().width/n-i*getSize().width/n-2),18));}}};
            int n=labels.size();for(int i=0;i<n;i++)row.addWidget(Forms.button(i*width()/n,0,Math.max(1,(i+1)*width()/n-i*width()/n-2),labels.get(i),actions.get(i)));
            addWidget(row);cursor+=21;
        }
        TextFieldWidget line(String key,String value,Consumer<String> changed){label(key);var input=text(3,cursor,width(),value,changed);addWidget(input);cursor+=21;return input;}
        void number(String key,Number value,double min,double max,double step,Consumer<Number> changed){
            var row=new com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator(key,()->value,changed,value,false){
                @Override protected void onSizeUpdate(){super.onSizeUpdate();width=getSize().width;if(image!=null)image.setSize(new Size(Math.max(1,width-leftWidth-3-rightWidth),10));if(textFieldWidget!=null)textFieldWidget.setSize(new Size(Math.max(1,width-leftWidth-6-rightWidth),10));}
                @Override public boolean mouseWheelMove(double x,double y,double delta){if(textFieldWidget!=null&&textFieldWidget.isMouseOverElement(x,y))textFieldWidget.setFocus(true);return super.mouseWheelMove(x,y,delta);}
            }.setRange(min,max).setWheel(step);
            row.init(width());row.computeHeight();row.setSelfPosition(new Position(3,cursor));addWidget(row);cursor+=row.getSize().height+5;
        }
        void bool(String key,boolean value,Consumer<Boolean> changed){addWidget(new SwitchWidget(3,cursor,12,12,(c,on)->changed.accept(on)).setTexture(new ColorBorderTexture(-1,-1).setRadius(6),new GuiTextureGroup(new ColorBorderTexture(-1,-1).setRadius(6),new ColorRectTexture(-1).setRadius(6).scale(.5f))).setPressed(value));addWidget(new LabelWidget(23,cursor+3,key));cursor+=21;}
        SelectorWidget choice(String key,List<String> choices,String value,Consumer<String> changed){return choice(key,choices,value,Function.identity(),changed);}
        SelectorWidget choice(String key,List<String> choices,String value,Function<String,String> display,Consumer<String> changed){
            label(key);var labels=new ArrayList<>(choices.stream().map(display).toList());
            String selected=choices.contains(value)?display.apply(value):Component.translatable("vjce.choice.unavailable",value).getString();
            if(!choices.contains(value))labels.add(selected);
            var selector=new SelectorWidget(3,cursor,width(),18,labels,-1).setButtonBackground(new ColorRectTexture(INPUT).setRadius(9)).setBackground(new ColorRectTexture(BG)).setValue(selected).setOnChanged(s->{int index=labels.indexOf(s);if(index>=0&&index<choices.size())changed.accept(choices.get(index));});
            addWidget(selector);cursor+=21;return selector;
        }
        void tag(String key,CompoundTag data,Consumer<CompoundTag> changed){var error=new LabelWidget(3,0,"").setTextColor(0xffff6666);line(key,data.toString(),s->{try{changed.accept(TagParser.parseTag(s));error.setText("");}catch(Exception e){error.setText(e.getMessage());}});error.setSelfPosition(new Position(3,cursor));addWidget(error);cursor+=15;}
        @SuppressWarnings({"unchecked","rawtypes"}) void object(Object target,Set<String> excluded,Runnable refresh,Runnable changed){
            for(var f:target.getClass().getFields()){
                if(Modifier.isStatic(f.getModifiers())||excluded.contains(f.getName()))continue;
                try{Object value=f.get(target);String key="vjce.field."+f.getName();Class<?> type=f.getType();
                    if(type==boolean.class)bool(key,(boolean)value,v->set(f,target,v,changed));
                    else if(target instanceof CategoryDocument.Slot&&f.getName().equals("role"))choice(key,ROLES,(String)value,s->Component.translatable("vjce.role."+s).getString(),s->set(f,target,s,changed));
                    else if(target instanceof CategoryDocument.Binding&&f.getName().equals("property"))choice(key,BINDING_PROPERTIES,(String)value,s->Component.translatable("vjce.binding."+s).getString(),s->set(f,target,s,changed));
                    else if(value instanceof Number n){double min=-30000000,max=30000000,step=1;String name=f.getName();if(Set.of("speed","distance","cameraRange").contains(name)){min=name.equals("cameraRange")?.2:0;max=128;step=.1;}if(name.equals("cameraPitch")){min=-89;max=89;}if(name.equals("period")){min=1;max=100000;}if(name.equals("amount")){min=1;max=Long.MAX_VALUE;}if(name.equals("chance")){min=0;max=1;step=.05;}number(key,n,min,max,step,v->set(f,target,parseNumber(type,v.toString()),changed));}
                    else if(type==String.class)line(key,String.valueOf(value),s->set(f,target,s,changed));
                    else if(type.isEnum())choice(key,Arrays.stream(type.getEnumConstants()).map(v->enumLabel((Enum<?>)v)).toList(),enumLabel((Enum<?>)value),s->{for(Object v:type.getEnumConstants())if(enumLabel((Enum<?>)v).equals(s))set(f,target,v,changed);});
                    else if(value instanceof Map map){label(key);for(Object k:List.copyOf(map.keySet()))line(String.valueOf(k),String.valueOf(map.get(k)),s->{try{map.put(k,Double.parseDouble(s));changed.run();}catch(NumberFormatException ignored){}});var k=line("vjce.add_value","new_field",s->{});button("vjce.add_value",()->{map.put(k.getCurrentString(),0d);changed.run();refresh.run();});}
                    else if(value instanceof List list && f.getGenericType() instanceof ParameterizedType p){
                        label(Component.translatable(key).getString()+" ["+list.size()+"]");
                        for(int i=0;i<list.size();i++){int index=i;Object item=list.get(i);button("− "+index,()->{list.remove(index);changed.run();refresh.run();});
                            if(item instanceof CategoryDocument.Ingredient ingredient)ingredient(ingredient,r->{list.set(index,r);changed.run();refresh.run();},refresh,changed);
                            else if(item instanceof String s)line("#"+index,s,v->{list.set(index,v);changed.run();});
                            else if(item instanceof List sub){line("#"+index,ProjectStore.JSON.toJson(sub),s->{try{list.set(index,ProjectStore.JSON.fromJson(s,p.getActualTypeArguments()[0]));changed.run();}catch(JsonParseException ignored){}});}
                            else object(item,Set.of(),refresh,changed);
                        }
                        button("+ "+Component.translatable(key).getString(),()->{try{var et=p.getActualTypeArguments()[0];Object next=et==String.class?"":et instanceof Class cls?cls.getConstructor().newInstance():new ArrayList<>();list.add(next);changed.run();refresh.run();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
                    }
                    else if(type==JsonElement.class)line(key,value==null?"":value.toString(),s->{try{set(f,target,s.isBlank()?null:JsonParser.parseString(s),changed);}catch(JsonParseException ignored){}});
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            }
        }
        void ingredient(CategoryDocument.Ingredient ingredient,Consumer<CategoryDocument.Ingredient> replace,Runnable refresh,Runnable changed){
            var slot=new IngredientPreview(3,cursor,ingredient);slot.setDraggingConsumer(o->o instanceof mezz.jei.api.ingredients.ITypedIngredient<?>||o instanceof net.minecraft.world.item.ItemStack,o->{},o->{},o->{var typed=IngredientPicker.typed(o);if(typed!=null)replace.accept(IngredientAccess.encode(typed));});addWidget(slot);cursor+=21;
            button("vjce.choose_ingredient",()->IngredientPicker.open(this,typed->replace.accept(IngredientAccess.encode(typed))));
            ingredientType(ingredient,replace);
            object(ingredient,Set.of("data","type"),refresh,changed);
            if(ingredient.data!=null)line("vjce.ingredient.advanced",ingredient.data.toString(),s->{try{ingredient.data=JsonParser.parseString(s);changed.run();}catch(JsonParseException ignored){}});
        }
        private void ingredientType(CategoryDocument.Ingredient ingredient,Consumer<CategoryDocument.Ingredient> replace){
            String current=Forms.ingredientType(ingredient);
            var types=IngredientAccess.manager==null?List.<mezz.jei.api.ingredients.IIngredientType<?>>of():new ArrayList<>(IngredientAccess.manager.getRegisteredIngredientTypes());
            var candidates=new ArrayList<>(types.stream().map(mezz.jei.api.ingredients.IIngredientType::getUid).toList());
            if(current.isEmpty())candidates.add(0,"");
            Function<String,String> display=v->v.isEmpty()?Component.translatable("vjce.choice.automatic").getString():v;
            var selector=choice("vjce.field.type",candidates,current,display,v->{});
            selector.setOnChanged(uid->{
                // Keep the old type and payload until selection commits a complete replacement.
                selector.setValue(candidates.contains(current)?display.apply(current):Component.translatable("vjce.choice.unavailable",current).getString());
                if(uid.equals(current))return;
                types.stream().filter(t->t.getUid().equals(uid)).findFirst().ifPresent(type->IngredientPicker.openType(this,type,typed->replace.accept(IngredientAccess.encode(typed))));
            });
        }
    }
    static String ingredientType(CategoryDocument.Ingredient ingredient){
        if(ingredient.type!=null&&!ingredient.type.isBlank())return ingredient.type;
        if(!ingredient.fluid.isBlank())return mezz.jei.api.forge.ForgeTypes.FLUID_STACK.getUid();
        if(!ingredient.item.isBlank()||!ingredient.tag.isBlank())return mezz.jei.api.constants.VanillaTypes.ITEM_STACK.getUid();
        return "";
    }
    private static Object parseNumber(Class<?> type,String text){
        if(type==int.class||type==Integer.class)return Integer.decode(text);
        if(type==long.class||type==Long.class)return Long.parseLong(text);
        if(type==float.class||type==Float.class){float value=Float.parseFloat(text);if(!Float.isFinite(value))throw new NumberFormatException();return value;}
        if(type==double.class||type==Double.class){double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new NumberFormatException();return value;}
        if(type==short.class||type==Short.class)return Short.decode(text);
        if(type==byte.class||type==Byte.class)return Byte.decode(text);
        return text;
    }
    private static void set(Field field,Object target,Object value,Runnable changed){try{field.set(target,value);if(target instanceof CategoryDocument.Ingredient i&&Set.of("item","fluid","tag").contains(field.getName())){i.data=null;i.type="";if(field.getName().equals("item"))i.fluid="";if(field.getName().equals("fluid"))i.item="";}changed.run();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    static final class IngredientPreview extends Widget {
        final CategoryDocument.Ingredient data;
        IngredientPreview(int x,int y,CategoryDocument.Ingredient data){super(x,y,18,18);this.data=data;}
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){var p=getPosition();g.fill(p.x,p.y,p.x+18,p.y+18,0xff8b8b8b);try{var values=IngredientAccess.decode(data);if(!values.isEmpty())IngredientSlot.drawIngredient(g,values.get(0),p.x+1,p.y+1,16,16,data.amount);}catch(RuntimeException ignored){}}
    }
}
