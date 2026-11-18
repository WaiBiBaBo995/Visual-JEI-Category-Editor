package com.pureneko.vjce.client;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import java.util.*;
public final class RecipeUI {
    private RecipeUI(){}
    public static CanvasTemplate canvasTemplate(CanvasTemplate template){return template;}
    public static CanvasTemplate savedTemplate(CategoryDocument document){return CanvasTemplate.parse(document.layout);}
    public static CanvasTemplate template(CategoryDocument document,CategoryDocument.Recipe recipe){
        if(!document.layout.isBlank())return savedTemplate(document);
        if(!document.target.isBlank()){
            NativePage page;try{page=NativePage.preview(document,recipe);}catch(RuntimeException e){page=NativePage.uneditedPreview(document);}
            if(page!=null)return page.template(document);
        }
        var root=new CanvasElement();root.width=document.width;root.height=document.height;root.background=0xffc6c6c6;
        for(int i=0;i<recipe.slots.size();i++){var slot=recipe.slots.get(i);if(slot.invisible)continue;var e=new IngredientSlot();e.id=recipe.slotIdentifier(i);e.x=slot.x;e.y=slot.y;root.addChild(e);}
        var duration=new CanvasElement("text");duration.id="duration";duration.x=4;duration.y=Math.max(0,document.height-12);duration.width=90;duration.height=10;duration.color=0xff333333;root.addChild(duration);
        for(int i=0;i<document.fields.size();i++){
            var field=document.fields.get(i);int y=Math.max(0,document.height-28-i*14);
            var label=new CanvasElement("text");label.id="field_"+field.key;label.text=field.label;label.x=field.icon.isBlank()?4:16;label.y=y;label.width=Math.max(10,document.width-76);label.height=10;label.color=field.color;root.addChild(label);
            var bar=new CanvasElement("progress");bar.id="bar_"+field.key;bar.x=document.width-64;bar.y=y;bar.width=60;bar.height=10;bar.color=field.color;root.addChild(bar);
            if(!field.icon.isBlank()){var icon=new CanvasElement("texture");icon.id="icon_"+field.key;icon.texture=field.icon;icon.x=4;icon.y=y;icon.width=10;icon.height=10;root.addChild(icon);}
        }
        return CanvasTemplate.of(root);
    }
    public static CanvasRuntime create(CategoryDocument document,CategoryDocument.Recipe recipe){
        var ui=template(document,recipe).createUI();bindSource(ui,document,recipe);bind(ui,document,recipe);var runtime=new CanvasRuntime(ui);runtime.init(document.width,document.height);return runtime;
    }
    public static void bindSource(CanvasUI ui,CategoryDocument document,CategoryDocument.Recipe recipe){
        if(document.target.isBlank())return;NativePage page;try{page=NativePage.preview(document,recipe);}catch(RuntimeException error){page=NativePage.uneditedPreview(document);}if(page!=null)page.bind(ui,document);
    }
    public static boolean isSlot(CanvasElement e){return e instanceof IngredientSlot||e instanceof NativePartElement p&&p.part.startsWith("slot_");}
    public static CanvasElement slotElement(CanvasElement root,String identifier){return root.selectId(identifier).filter(RecipeUI::isSlot).findFirst().orElse(null);}
    public static void bind(CanvasUI ui,CategoryDocument document,CategoryDocument.Recipe recipe){bind(ui,document,recipe,false);}
    public static void bind(CanvasUI ui,CategoryDocument document,CategoryDocument.Recipe recipe,boolean nativeSlots){
        ui.rootElement.width=document.width;ui.rootElement.height=document.height;
        for(int i=0;i<recipe.slots.size();i++){var slot=recipe.slots.get(i);var e=slotElement(ui.rootElement,recipe.slotIdentifier(i));if(e instanceof IngredientSlot generic){var values=nativeSlots?List.<mezz.jei.api.ingredients.ITypedIngredient<?>>of():IngredientAccess.decode(slot);generic.ingredient=values.isEmpty()?null:values.get(0);generic.amount=slot.ingredients.isEmpty()?1:slot.ingredients.get(0).amount;}}
        var variables=document.variables(recipe);
        ui.rootElement.selectId("duration").forEach(e->e.text=String.format(Locale.ROOT,"%.2f s",recipe.duration/20));
        ui.rootElement.selectId("progress").forEach(e->e.onTick("recipe_progress",()->e.progress=(float)((net.minecraft.Util.getMillis()%Math.max(50,recipe.duration*50))/Math.max(50,recipe.duration*50))));
        for(var field:document.fields){double value=variables.getOrDefault(field.key,field.initial);ui.rootElement.selectId("field_"+field.key).forEach(e->e.text=value+" "+field.unit);ui.rootElement.selectId("bar_"+field.key).forEach(e->e.progress=(float)(value/field.maximum));}
        for(var binding:document.bindings){double value=Expression.evaluate(binding.expression,variables);ui.rootElement.selectId(binding.element).forEach(e->{switch(binding.property){
            case "visible"->e.visible=value!=0;case "text"->{e.text=String.format(Locale.ROOT,binding.format,value);if(e instanceof NativePartElement part)part.replacementText=e.text;}
            case "progress"->e.progress=(float)value;case "color"->{e.color=(int)(long)value;if(e instanceof NativePartElement part){part.overrideTextColor=true;part.textColor=e.color;}}
        }});}
    }
}
