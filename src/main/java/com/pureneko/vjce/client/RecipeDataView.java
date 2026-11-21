package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.*;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import java.util.*;
public final class RecipeDataView extends WidgetGroup {
    final VjceProject project;
    final Forms.Form browser,form;
    final Forms.Sidebar sidebar;
    final LabelWidget status;
    boolean categoryMode;
    int page;
    String query="";
    int selectedSlot=-1;
    final Map<String,Boolean> rolesOpen=new HashMap<>();
    public RecipeDataView(VjceProject project,int w,int h){super(0,36,w,h);this.project=project;status=new LabelWidget(3,h-11,project.sourceIssue);int bw=Math.min(155,w/3);browser=new Forms.Form(0,0,bw,h-42);sidebar=new Forms.Sidebar(0,24,bw,h-24,"vjce.recipes",browser);form=new Forms.Form(bw+3,24,w-bw-3,h-24);addWidget(form);addWidget(sidebar);sidebar.onResize=this::layoutContent;addWidget(Forms.button(0,0,Math.max(40,w/4),"vjce.category",()->{categoryMode=true;refresh();openInspector();}));addWidget(Forms.button(w/4,0,Math.max(40,w/4),"vjce.recipes",()->{categoryMode=false;refresh();openInspector();}));addWidget(Forms.button(w/2,0,Math.max(40,w/4),"vjce.publish",()->action(()->{project.publish();status.setText("vjce.saved_reenter");})));addWidget(Forms.button(w*3/4,0,Math.max(40,w/4),"vjce.export",()->action(()->{project.publish();var output=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("exports/vjce-1.20.1-modpack.zip");VisualJeiCategoryEditor.STORE.exportZip(output,ClientBootstrap.assets());status.setText(output.toString());})));addWidget(status);refresh();}
    public void refresh(){browser.clear();form.clear();browser.button("vjce.new_recipe",()->{if(!project.canCreateRecipes())return;var r=new CategoryDocument.Recipe();r.id=unique();project.document.recipes.add(r);project.select(r);changed();});browser.button("vjce.copy",()->{if(!project.canCreateRecipes())return;var r=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(project.selected),CategoryDocument.Recipe.class);r.id=unique();r.source="";project.document.recipes.add(r);project.select(r);changed();});browser.button("vjce.delete",()->{project.document.recipes.remove(project.selected);project.select(project.document.recipes.isEmpty()?new CategoryDocument.Recipe():project.document.recipes.get(0));changed();});
        for(var r:project.document.recipes)browser.button(r.id,()->project.select(r),()->project.selected.id.equals(r.id));sourceBrowser();
        if(categoryMode){form.object(project.document,Set.of("schemaVersion","minecraftVersion","layoutEngine","nativeOriginElements","layout","recipes","catalysts"),this::refresh,this::changed);CategoryCatalystEditor.addTo(form,project.document,()->{changed();refresh();});return;}
        if(!project.canEditSelectedRecipe()){form.label(project.sourceIssue.isEmpty()?"vjce.source.select_recipe":project.sourceIssue);return;}
        var addRoles=List.of("INPUT","OUTPUT","CATALYST");form.buttonRow(addRoles.stream().map(role->"+ "+net.minecraft.network.chat.Component.translatable("vjce.role."+role).getString()).toList(),addRoles.stream().<Runnable>map(role->()->addSlot(role)).toList());
        for(String role:List.of("INPUT","OUTPUT","CATALYST","RENDER_ONLY")){
            boolean open=rolesOpen.getOrDefault(role,true);
            form.button((open?"▾ ":"▸ ")+net.minecraft.network.chat.Component.translatable("vjce.role."+role).getString(),()->{rolesOpen.put(role,!open);refreshDeferred();});
            if(open)for(int i=0;i<project.selected.slots.size();i++){int index=i;var slot=project.selected.slots.get(i);if(slot.role.equals(role))form.button((selectedSlot==i?"> ":"")+project.selected.slotIdentifier(i),()->inspectSlot(index));}
        }
        form.label("vjce.recipe.basic");
        form.object(project.selected,Set.of("slots","focusLinks","duration","values"),this::refreshDeferred,this::changed);
        form.label("vjce.recipe.parameters");
        if(project.canCreateRecipes())form.number("vjce.field.duration",project.selected.duration,.01,30000000,1,v->{project.selected.duration=v.doubleValue();changed();});
        for(var field:project.document.fields)form.number(field.label+" ("+field.unit+")",project.selected.values.getOrDefault(field.key,field.initial),-30000000,30000000,1,v->{project.selected.values.put(field.key,v.doubleValue());changed();});
    }
    private void layoutContent(){int w=getSize().width,h=getSize().height;form.setSelfPosition(new Position(sidebar.occupied()+3,24));form.resizeForm(Math.max(20,w-sidebar.occupied()-3),Math.max(20,h-24));status.setSelfPosition(new Position(3,h-11));}
    void resizeView(int w,int h){setSize(new Size(w,h));sidebar.resizePanel(Math.min(155,w/3),h-24);int index=0;for(var widget:widgets)if(widget instanceof ButtonWidget){widget.setSelfPosition(new Position(index*w/4,0));widget.setSize(new Size(Math.max(1,w/4-2),18));index++;}layoutContent();}
    @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){sidebar.advanceAnimation(System.nanoTime());super.drawInBackground(g,mx,my,pt);}
    void openInspector(){if(!categoryMode&&!project.selected.slots.isEmpty())inspectSlot(Math.max(0,Math.min(selectedSlot,project.selected.slots.size()-1)));else project.editor.getConfigPanel().clearAllConfigurators();}
    void inspectSlot(int index){selectedSlot=index;refreshDeferred();var slot=project.selected.slots.get(index);var selectedElement=RecipeUI.slotElement(project.layoutView.getCurrentUI().rootElement,project.selected.slotIdentifier(index));if(selectedElement!=null)project.layoutView.focusElement(selectedElement);var fields=new Forms.Form(0,0,230,220);fields.object(slot,Set.of("identifier","x","y","invisible","sourceIndex"),()->inspectSlot(index),()->{project.selected.overrideIngredients=true;project.selected.focusLinks.clear();changed();refreshDeferred();});
        var current=project.selected.slotIdentifier(index);var binding=fields.line("vjce.field.identifier",current,s->{});fields.button("vjce.scene.apply",()->{String next=binding.getCurrentString().strip();var element=RecipeUI.slotElement(project.layoutView.getCurrentUI().rootElement,next);if(element==null){showError("vjce.slot.binding_invalid");return;}var occupied=project.selected.boundSlot(next);Runnable commit=()->{if(occupied!=null&&occupied!=slot)occupied.identifier=current;slot.identifier=next;changed();refresh();};if(occupied!=null&&occupied!=slot){var dialog=new DialogWidget(project.editor,true);int cx=project.editor.getSize().width/2-130,cy=project.editor.getSize().height/2-45;var content=new Forms.Form(cx,cy,260,90);content.label("vjce.slot.binding_swap");content.button("vjce.confirm",()->{dialog.close();commit.run();});content.button("vjce.close",dialog::close);dialog.addWidget(content);}else commit.run();});
        fields.button("vjce.slot.delete",()->{project.selected.removeSlot(index);project.selected.focusLinks.clear();project.selected.overrideIngredients=true;selectedSlot=-1;changed();refreshDeferred();project.editor.getConfigPanel().clearAllConfigurators();});
        fields.embed();
        project.editor.getConfigPanel().openConfigurator(com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel.Tab.WIDGET,new com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable(){@Override public void buildConfigurator(com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup group){group.addConfigurators(new com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator(fields));}});

    }
    void addSlot(String role){var slot=new CategoryDocument.Slot();slot.role=role;int i=0;String id;do{id="vjce_slot_"+i++;}while(RecipeUI.slotElement(project.layoutView.getCurrentUI().rootElement,id)!=null||project.selected.boundSlot(id)!=null);slot.identifier=id;slot.name=id;slot.x=role.equals("OUTPUT")?120:8+(project.selected.slots.size()%4)*20;slot.y=20;slot.sourceIndex=-1;var ingredient=new CategoryDocument.Ingredient();ingredient.item="minecraft:stone";slot.ingredients.add(ingredient);project.selected.slots.add(slot);project.selected.overrideIngredients=true;project.selected.focusLinks.clear();project.layoutView.addSlot(project.selected.slots.size()-1);changed();refresh();inspectSlot(project.selected.slots.size()-1);}
    @SuppressWarnings({"rawtypes","unchecked"}) private void sourceBrowser(){if(project.document.target.isBlank()||VjceJeiPlugin.runtime==null)return;var original=VjceJeiPlugin.originals.get(project.document.target);if(original==null)return;browser.line("vjce.import_original",query,s->{query=s;page=0;});browser.button("vjce.search",this::refresh);browser.button("vjce.previous",()->{page=Math.max(0,page-1);refresh();});browser.button("vjce.next",()->{page++;refresh();});var recipes=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().filter(r->query.isBlank()||String.valueOf(((mezz.jei.api.recipe.category.IRecipeCategory)original).getRegistryName(r)).contains(query)).skip(page*25L).limit(25).toList();for(Object raw:recipes){var id=((mezz.jei.api.recipe.category.IRecipeCategory)original).getRegistryName(raw);browser.button(String.valueOf(id==null?raw:id),()->action(()->{var r=CapturedCategory.capture((mezz.jei.api.recipe.category.IRecipeCategory)original,raw);var existing=project.document.recipes.stream().filter(v->v.source.equals(r.source)).findFirst();if(existing.isPresent())project.select(existing.get());else{if(!r.source.startsWith("vjce:generated/"))project.document.recipes.add(r);project.select(r);}changed();}),()->id!=null?project.selected.source.equals(id.toString()):NativePage.isPreview(project.document.target,project.selected.source,raw));}}
    private boolean refreshPending;
    void refreshDeferred(){refreshPending=true;}
    @Override public void updateScreen(){super.updateScreen();if(refreshPending){refreshPending=false;refresh();}}
    private void changed(){project.captureLayout();project.layoutView.rebind();project.changed("vjce.category_data",this);}
    public void showError(String message){status.setText(message==null?"":message);}
    private void action(Checked action){try{action.run();}catch(Exception e){showError(e.getMessage());VisualJeiCategoryEditor.LOG.error("VJCE editor action failed",e);}}
    private static String unique(){return "vjce:recipe_"+UUID.randomUUID().toString().replace("-","");}
    private interface Checked{void run()throws Exception;}
}
