package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.editor.data.*;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.*;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.*;
import net.minecraft.nbt.CompoundTag;
import java.io.*;
@LDLRegister(name="vjce",group="editor.vjce")
public final class VjceProject implements IProject {
    private final Resources resources=Resources.of(new EntriesResource(),new ColorsResource(),new TexturesResource(),SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES);
    public CategoryDocument document;
    public CategoryDocument.Recipe selected;
    public RecipeLayoutView layoutView;
    public RecipeDataView dataView;
    public String sourceIssue="";
    public boolean sourceEditable=true;
    VjceEditor editor;
    private boolean restored;
    public VjceProject(){this(new CategoryDocument());document.id="vjce:category_"+Long.toUnsignedString(System.nanoTime(),36);document.title="New Category";var recipe=new CategoryDocument.Recipe();recipe.id="vjce:recipe_"+Long.toUnsignedString(System.nanoTime(),36);document.recipes.add(recipe);selected=recipe;}
    public VjceProject(CategoryDocument document){resources.resources.values().forEach(Resource::buildDefault);this.document=document;selected=document.recipes.isEmpty()?new CategoryDocument.Recipe():document.recipes.get(0);}
    public boolean canCreateRecipes(){return document.target.isBlank()||com.pureneko.vjce.integration.VanillaDisplays.supports(new net.minecraft.resources.ResourceLocation(document.target));}
    public boolean canEditSelectedRecipe(){return sourceEditable&&!selected.source.startsWith("vjce:generated/")&&(canCreateRecipes()||!selected.source.isBlank());}
    @Override public Resources getResources(){return resources;}
    @Override public IProject newEmptyProject(){return new VjceProject();}
    @Override public String getSuffix(){return "vjce1201";}
    @Override public File getProjectWorkSpace(Editor editor){return VisualJeiCategoryEditor.STORE.root.resolve("projects").toFile();}
    public void captureLayout(){if(layoutView!=null&&sourceEditable&&!restored)document.layout=layoutView.getCurrentUI().toTemplate().serialize();}
    public void publish() throws IOException{captureLayout();VisualJeiCategoryEditor.STORE.save(document);}
    @Override public CompoundTag serializeNBT(){captureLayout();var t=new CompoundTag();t.putString("document",ProjectStore.JSON.toJson(document));t.putString("selected",selected.id);t.putString("categoryFile",VisualJeiCategoryEditor.STORE.sourceFile(document));t.put("resources",resources.serializeNBT());return t;}
    @Override public void deserializeNBT(CompoundTag tag){
        document=ProjectStore.readDocument(tag.getString("document"));
        VisualJeiCategoryEditor.STORE.restoreSource(document,tag.getString("categoryFile"));
        selected=document.recipes.stream().filter(r->r.id.equals(tag.getString("selected"))).findFirst().orElse(document.recipes.isEmpty()?new CategoryDocument.Recipe():document.recipes.get(0));
        resources.deserializeNBT(tag.getCompound("resources"));restored=true;
    }
    @Override public void onLoad(Editor base){
        editor=(VjceEditor)base;
        sourceEditable=true;sourceIssue="";
        if(!document.target.isBlank()){
            var original=com.pureneko.vjce.integration.VjceJeiPlugin.originals.get(document.target);
            if(original==null||com.pureneko.vjce.integration.VjceJeiPlugin.runtime==null){sourceEditable=false;sourceIssue="vjce.source.unavailable";}
            else if(document.recipes.isEmpty())selectSourceSample(original);
        }
        if(layoutView!=null)layoutView.dispose();
        restored=false;layoutView=new RecipeLayoutView(this,editor.contentWidth(),editor.contentHeight());
        dataView=new RecipeDataView(this,editor.contentWidth(),editor.contentHeight());
        editor.getTabPages().addTab("vjce.library",editor.library());
        editor.getTabPages().addTab("vjce.category_data",dataView,()->{dataView.refresh();dataView.openInspector();},()->{});
        editor.getTabPages().addTab("vjce.layout",layoutView,()->layoutView.inspect(),()->layoutView.transforms.cancel());
        editor.getResourcePanel().loadResource(resources,false);
        editor.getTabPages().switchTabIndex(2);
        if(editor.getHistory().isEmpty())editor.addAutoHistory("vjce.open",null);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private void selectSourceSample(mezz.jei.api.recipe.category.IRecipeCategory original){
        var samples=com.pureneko.vjce.integration.VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().limit(10).toList();
        for(var raw:samples)try{selected=com.pureneko.vjce.integration.CapturedCategory.capture(original,raw);if(canEditSelectedRecipe())document.recipes.add(selected);else sourceIssue="vjce.source.unstable";return;}catch(RuntimeException e){sourceIssue="vjce.source.readonly";}
        sourceEditable=false;sourceIssue=samples.isEmpty()?"vjce.source.empty":"vjce.source.readonly";
    }
    @Override public void onClosed(Editor editor){if(layoutView!=null)layoutView.dispose();SceneEditor.closeFor(layoutView);}
    public void select(CategoryDocument.Recipe recipe){captureLayout();selected=recipe;dataView.selectedSlot=-1;sourceIssue=canEditSelectedRecipe()?"":"vjce.source.unstable";layoutView.reload();dataView.refresh();}
    public void changed(String name,Object source){if(editor!=null){var state=serializeNBT();var current=editor.getCurrentHistory();if(current==null||!current.date().equals(state))editor.addRawHistory(name,state,source);while(editor.getHistory().size()>500)editor.getHistory().remove(0);}}
}
