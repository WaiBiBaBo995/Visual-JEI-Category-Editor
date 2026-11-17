package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
@LDLRegister(name="editor.vjce",group="editor")
public final class VjceEditor extends Editor {
    public VjceEditor(){super(VisualJeiCategoryEditor.STORE.root.resolve("editor").toFile());}
    public static void open(){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        // LDLib's fixed property panel needs enough logical pixels for the work area.
        int originalScale=mc.options.guiScale().get();
        int editorScale=Math.max(1,Math.min(mc.getWindow().getWidth()/800,mc.getWindow().getHeight()/450));
        boolean adjusted=mc.getWindow().getGuiScale()>editorScale;
        if(adjusted){mc.options.guiScale().set(editorScale);mc.resizeDisplay();}
        var editor=new VjceEditor();var ui=new ModularUI(IUIHolder.EMPTY,mc.player).widget(editor);
        ui.setFullScreen();ui.initWidgets();
        mc.setScreen(new ModularUIGuiContainer(ui,mc.player.containerMenu.containerId));
        ui.registerCloseListener(()->{
            if(editor.currentProject instanceof VjceProject p){p.captureLayout();p.onClosed(editor);}
            if(adjusted&&mc.options.guiScale().get()==editorScale){mc.options.guiScale().set(originalScale);ClientBootstrap.resizeAfterScreenChange();}
        });
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(getTabPages()!=null&&getTabPages().focus instanceof RecipeLayoutView view&&!view.stage.isMouseOverElement(x,y))view.transforms.clear();
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256&&!CanvasModalGuard.blocked(getGui())&&getTabPages()!=null){
            if(getTabPages().focus instanceof RecipeLayoutView view&&view.escapeCanvasTransform())return true;
            if(getTabPages().focus instanceof SceneEditingView view&&view.viewport.key(key))return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private int laidOutWidth=-1,laidOutHeight=-1;
    public int contentWidth(){return Math.max(80,getSize().width-(configPanel instanceof EditorPanels.Properties p?p.occupied():252));}
    public int contentHeight(){return Math.max(40,getSize().height-36-(resourcePanel instanceof EditorPanels.Resources p?p.occupied():100));}
    void reflowPanels(long now){
        if(!(configPanel instanceof EditorPanels.Properties properties)||!(resourcePanel instanceof EditorPanels.Resources resources))return;
        properties.advanceAnimation(now);resources.advanceAnimation(now);
        properties.resize(getSize().width,getSize().height);resources.resize(contentWidth(),getSize().height);
        int w=contentWidth(),h=contentHeight();if(w==laidOutWidth&&h==laidOutHeight)return;laidOutWidth=w;laidOutHeight=h;
        menuPanel.setSize(new com.lowdragmc.lowdraglib.utils.Size(w,16));floatView.setSize(getSize());tabPages.setSize(new com.lowdragmc.lowdraglib.utils.Size(w,getSize().height));
        for(var tab:List.copyOf(tabPages.tabs.values())){
            if(tab instanceof RecipeLayoutView view)view.resizeView(w,h);
            else if(tab instanceof RecipeDataView view)view.resizeView(w,h);
            else if(tab instanceof SceneEditingView view)view.resizeView(w,h);
            else if(tab instanceof Forms.Form form)form.resizeForm(w,h);
        }
        tabPages.calculateTabSize();
    }
    @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){reflowPanels(System.nanoTime());super.drawInBackground(g,mx,my,pt);}
    @Override public void initEditorViews(){
        toolPanel=new com.lowdragmc.lowdraglib.gui.editor.ui.ToolPanel(this);
        configPanel=new EditorPanels.Properties(this);
        tabPages=new com.lowdragmc.lowdraglib.gui.editor.ui.StringTabContainer(this);
        resourcePanel=new EditorPanels.Resources(this);
        menuPanel=new com.lowdragmc.lowdraglib.gui.editor.ui.MenuPanel(this);
        floatView=new WidgetGroup(0,0,getSize().width,getSize().height);
        addWidget(tabPages);addWidget(toolPanel);addWidget(configPanel);addWidget(resourcePanel);addWidget(menuPanel);addWidget(floatView);
    }
    @Override public void onScreenSizeUpdate(int width,int height){
        if(getGui()!=null)getGui().mainGroup.setSize(new com.lowdragmc.lowdraglib.utils.Size(width,height));
        if(tabPages==null){super.onScreenSizeUpdate(width,height);return;}
        setSize(new com.lowdragmc.lowdraglib.utils.Size(width,height));
        laidOutWidth=-1;reflowPanels(System.nanoTime());
        for(var widget:List.copyOf(getGui().mainGroup.widgets))if(widget instanceof WorldSelectionDialog dialog)dialog.resizeDialog(width,height);
    }
    @Override public void loadProject(IProject project){if(currentProject instanceof VjceProject p&&currentProject!=project)p.captureLayout();super.loadProject(project);if(project==null)getTabPages().addTab("vjce.library",library());}
    Forms.Form library(){var form=new Forms.Form(0,36,contentWidth(),contentHeight());form.label("Visual JEI Category Editor · pureneko");form.button("vjce.new",()->loadProject(new VjceProject()));form.button("vjce.refresh",()->{if(currentProject==null)loadProject(null);else loadProject(currentProject);});
        var search=form.line("vjce.library.search","",s->{});var results=new Forms.Form(3,form.cursor,form.width(),Math.max(25,contentHeight()-form.cursor));form.addWidget(results);
        Runnable refresh=()->{results.clear();String query=search.getCurrentString().strip().toLowerCase(Locale.ROOT);results.label("vjce.library.saved");for(var d:VisualJeiCategoryEditor.STORE.loadAll())if(matches(query,d.title,d.id,d.target))results.button(d.title+" · "+d.id,()->loadProject(new VjceProject(ProjectStore.copy(d))));results.label("vjce.registered_categories");for(var entry:VjceJeiPlugin.originals.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())if(matches(query,entry.getValue().getTitle().getString(),entry.getKey()))results.button(entry.getValue().getTitle().getString()+" · "+entry.getKey(),()->loadProject(projectForCategory(entry.getKey())));for(String error:VisualJeiCategoryEditor.STORE.errors)results.label(error);if(VjceJeiPlugin.runtime==null)results.label("vjce.enter_world");};search.setTextResponder(s->refresh.run());refresh.run();return form;
    }
    private static boolean matches(String query,String... names){var b=new StringBuilder();for(String n:names){b.append(n).append(' ');var id=ResourceLocation.tryParse(n);if(id!=null)net.minecraftforge.fml.ModList.get().getModContainerById(id.getNamespace()).ifPresent(m->b.append(m.getModInfo().getDisplayName()));}return b.toString().toLowerCase(Locale.ROOT).contains(query);}
    public static ResourceLocation overrideId(ResourceLocation category){return new ResourceLocation("vjce","override/"+category.getNamespace()+"/"+category.getPath());}
    public static VjceProject projectForCategory(String id){var original=VjceJeiPlugin.originals.get(id);if(original==null)throw new IllegalArgumentException("JEI category unavailable: "+id);if(original instanceof DisplayCategory<?> custom&&!(original instanceof CapturedCategory<?>))return new VjceProject(ProjectStore.copy(custom.document));var saved=VisualJeiCategoryEditor.STORE.loadAll().stream().filter(d->d.target.equals(id)).findFirst();if(saved.isPresent())return new VjceProject(ProjectStore.copy(saved.get()));var d=new CategoryDocument();d.id=overrideId(new ResourceLocation(id)).toString();d.target=id;d.title=original.getTitle().getString();d.width=net.minecraft.util.Mth.clamp(original.getWidth(),18,2048);d.height=net.minecraft.util.Mth.clamp(original.getHeight(),18,2048);return new VjceProject(d);}
}
