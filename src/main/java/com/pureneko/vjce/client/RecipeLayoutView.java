package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.utils.*;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.integration.*;
import org.joml.*;
import java.lang.Math;
import java.util.*;
public final class RecipeLayoutView extends WidgetGroup {
    final VjceProject project;
    final RecipeCanvasTransform transforms;
    final LinkedHashSet<CanvasElement> selection=new LinkedHashSet<>();
    private CanvasUI ui;
    final Stage stage;
    final Forms.Form hierarchy;
    final Forms.Sidebar sidebar;
    final List<ButtonWidget> toolbar=new ArrayList<>();
    private CanvasElement treeAnchor;
    boolean simulation;
    boolean showReference=true;
    float zoom=1,panX=48,panY=80;
    public RecipeLayoutView(VjceProject p,int w,int h){super(0,36,w,h);project=p;transforms=new RecipeCanvasTransform(this);int toolbarHeight=70;hierarchy=new Forms.Form(0,0,Math.min(150,w/3),h-toolbarHeight-18);sidebar=new Forms.Sidebar(0,toolbarHeight,Math.min(150,w/3),h-toolbarHeight,"vjce.panel.hierarchy",hierarchy);stage=new Stage(sidebar.occupied()+3,toolbarHeight,w-sidebar.occupied()-3,h-toolbarHeight);addWidget(stage);addWidget(sidebar);sidebar.onResize=this::layoutStage;
        String[] labels={"vjce.generate","vjce.add_progress","vjce.add_text","vjce.add_texture","vjce.scene.add","vjce.scene.create","vjce.native.group","vjce.ungroup","vjce.element.copy","vjce.element.delete","vjce.preview","vjce.scene.pause","vjce.scene.reset"};
        Runnable[] actions={()->{project.document.layout="";project.document.nativeOriginElements.clear();reload();markAsDirty();},()->add(new CanvasElement("progress"),"progress"),()->{var e=new CanvasElement("text");e.text="Text";add(e,"text_"+count());},()->{var e=new TextureElement();add(e,"texture_"+count());},()->SceneEditor.open(this,null),()->add(new CreateAnimationElement(),"create_"+count()),()->CanvasGrouping.group(this),()->CanvasGrouping.ungroup(this),this::duplicate,this::delete,this::toggleSimulation,()->SceneEditor.playing(ui,!ui.rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).anyMatch(s->s.clock.playing)),()->ui.rootElement.select("*").forEach(SceneEditor::reset)};
        for(int i=0;i<labels.length;i++){if(labels[i].equals("vjce.preview")||labels[i].equals("vjce.scene.create")&&!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"))continue;var button=Forms.button(0,0,40,labels[i],actions[i]);toolbar.add(button);addWidget(button);}
        var reference=Forms.button(0,0,40,"vjce.reference.toggle",()->showReference=!showReference);reference.setHoverTooltips("vjce.reference.help");toolbar.add(reference);addWidget(reference);panX=Math.max(48,-JeiPageReference.bounds(p.document).x()+28);resizeView(w,h);reload();
    }
    private void layoutStage(){int top=toolbar.size()>12?70:48;sidebar.setSelfPosition(new Position(0,top));stage.setSelfPosition(new Position(sidebar.occupied()+3,top));stage.setSize(new Size(Math.max(20,getSize().width-sidebar.occupied()-3),Math.max(20,getSize().height-top)));}
    void resizeView(int w,int h){setSize(new Size(w,h));int bw=Math.max(1,w/6);for(int i=0;i<toolbar.size();i++){toolbar.get(i).setSelfPosition(new Position(i%6*bw,i/6*22));toolbar.get(i).setSize(new Size(Math.max(1,bw-2),18));}int top=toolbar.size()>12?70:48;sidebar.resizePanel(Math.min(150,w/3),h-top);layoutStage();}
    @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){sidebar.advanceAnimation(System.nanoTime());super.drawInBackground(g,mx,my,pt);}
    private int count(){return ui.rootElement.select("*").toList().size();}
    public CanvasUI getCurrentUI(){return ui;}
    public boolean isSimulationRunning(){return simulation;}
    public void reload(){transforms.clear();if(ui!=null)ui.rootElement.dispose();selection.clear();ui=RecipeUI.template(project.document,project.selected).createUI();rebind();refreshHierarchy();}
    public void rebind(){if(ui==null)return;ui.rootElement.select("*").filter(TextureElement.class::isInstance).map(TextureElement.class::cast).forEach(t->t.bind((com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource< com.lowdragmc.lowdraglib.gui.texture.IGuiTexture>)project.getResources().resources.get(com.lowdragmc.lowdraglib.gui.editor.data.resource.TexturesResource.RESOURCE_NAME)));RecipeUI.bindSource(ui,project.document,project.selected);RecipeUI.bind(ui,project.document,project.selected);SceneEditor.playing(ui,simulation);}
    public void dispose(){transforms.clear();if(ui!=null)ui.rootElement.dispose();}
    public VjceEditor getEditor(){return project.editor;}
    public void markAsDirty(){project.captureLayout();project.changed("vjce.layout",this);refreshHierarchy();}
    public void focusElement(CanvasElement element){if(!selection.contains(element))transforms.clear();selection.clear();selection.add(element);inspect();}
    public void inspect(){var element=selection.stream().findFirst().orElse(ui.rootElement);getEditor().getConfigPanel().openConfigurator(ConfigPanel.Tab.WIDGET,new IConfigurable(){@Override public void buildConfigurator(ConfiguratorGroup group){CanvasConfigurator.build(group,element,RecipeLayoutView.this);}});}
    void refreshHierarchy(){if(ui==null)return;hierarchy.clear();tree(ui.rootElement,0);}
    private void tree(CanvasElement element,int depth){hierarchy.button("  ".repeat(Math.min(8,depth))+(selection.contains(element)?"> ":"")+element.type+" · "+element.id,()->selectTree(element));for(var child:element.getChildren())tree(child,depth+1);}
    void selectTree(CanvasElement element){
        transforms.clear();
        if(net.minecraft.client.gui.screens.Screen.hasShiftDown()&&treeAnchor!=null&&treeAnchor.getParent()==element.getParent()){
            var siblings=element.getParent()==null?List.of(element):element.getParent().getChildren();int a=siblings.indexOf(treeAnchor),b=siblings.indexOf(element);selection.clear();if(a>=0&&b>=0)selection.addAll(siblings.subList(Math.min(a,b),Math.max(a,b)+1));
        }else if(net.minecraft.client.gui.screens.Screen.hasControlDown()){if(!selection.remove(element))selection.add(element);treeAnchor=element;}
        else{selection.clear();selection.add(element);treeAnchor=element;}
        inspect();refreshHierarchy();
    }
    private void add(CanvasElement e,String id){if(!project.sourceEditable)return;e.id=id;e.x=40;e.y=20;if(e instanceof BlockSceneElement||e instanceof CreateAnimationElement){e.width=80;e.height=80;}else{e.width=60;e.height=12;}ui.rootElement.addChild(e);if(e instanceof TextureElement texture)texture.bind((com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource<com.lowdragmc.lowdraglib.gui.texture.IGuiTexture>)project.getResources().resources.get(com.lowdragmc.lowdraglib.gui.editor.data.resource.TexturesResource.RESOURCE_NAME));focusElement(e);markAsDirty();}
    void addScene(BlockSceneElement scene){add(scene,"scene_"+count());}
    void commitScene(Runnable redo,Runnable undo){redo.run();markAsDirty();}
    public void addSlot(int index){var spec=project.selected.slots.get(index);var slot=new IngredientSlot();slot.id=project.selected.slotIdentifier(index);slot.x=spec.x;slot.y=spec.y;ui.rootElement.addChild(slot);rebind();markAsDirty();}
    void duplicate(){for(var e:CanvasGrouping.topLevel(selection)){if(e==ui.rootElement)continue;var copy=CanvasElement.deserialize(e.serialize());copy.select("*").forEach(c->c.id=c.id+"_copy_"+Long.toUnsignedString(System.nanoTime(),36));copy.x+=8;copy.y+=8;e.getParent().addChild(copy);}markAsDirty();}
    void delete(){for(var e:CanvasGrouping.topLevel(selection)){if(e==ui.rootElement)continue;e.detach();e.dispose();}selection.clear();markAsDirty();}
    void toggleSimulation(){simulation=!simulation;transforms.clear();SceneEditor.playing(ui,simulation);}
    boolean canvasInputAllowed(){return !simulation&&project.sourceEditable&&getGui()!=null&&!CanvasModalGuard.blocked(getGui());}
    public boolean escapeCanvasTransform(){return transforms.escape();}
    @Override public void updateScreen(){super.updateScreen();if(ui!=null){ui.rootElement.screenTick();var page=NativePage.context(ui.rootElement);if(page!=null&&page.drawing.mount(ui.rootElement,project.document))refreshHierarchy();}}
    @Override public boolean keyPressed(int key,int scan,int mods){if(super.keyPressed(key,scan,mods))return true;if(!canvasInputAllowed())return false;boolean ctrl=(mods&2)!=0;
        if(key==256&&transforms.escape())return true;if(key==261){delete();return true;}if(ctrl&&key==71){if((mods&1)!=0)CanvasGrouping.ungroup(this);else CanvasGrouping.group(this);return true;}if(ctrl&&key==68){duplicate();return true;}
        if(ctrl&&key==90){var history=getEditor().getHistory();int index=history.indexOf(getEditor().getCurrentHistory());int next=index+((mods&1)!=0?1:-1);if(next>=0&&next<history.size())getEditor().jumpToHistory(history.get(next));return true;}
        int dx=key==262?1:key==263?-1:0,dy=key==264?1:key==265?-1:0;if(dx!=0||dy!=0){for(var e:CanvasGrouping.topLevel(selection)){e.x+=dx*((mods&1)!=0?10:1);e.y+=dy*((mods&1)!=0?10:1);}markAsDirty();return true;}return false;
    }
    final class Stage extends Widget {
        Stage(int x,int y,int w,int h){super(x,y,Math.max(30,w),Math.max(30,h));setDraggingConsumer(o->o instanceof SceneAssets.Drop||o instanceof com.lowdragmc.lowdraglib.gui.texture.IGuiTexture,o->{},o->{},o->{if(o instanceof com.lowdragmc.lowdraglib.gui.texture.IGuiTexture texture){var element=new TextureElement();element.setImage(texture);add(element,"texture_"+count());}else if(o instanceof SceneAssets.Drop drop){var scene=new BlockSceneElement();var data=new net.minecraft.nbt.CompoundTag();var list=new net.minecraft.nbt.ListTag();list.add(SceneAssets.instance(drop));data.put(drop.type().entity?"entities":"blocks",list);scene.blocks=data;addScene(scene);}});}
        Vector2f point(double x,double y){var p=getPosition();return new Vector2f((float)(x-p.x-panX)/zoom,(float)(y-p.y-panY)/zoom);}
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){if(CanvasModalGuard.blocked(getGui()))transforms.clear();var p=getPosition();var s=getSize();g.enableScissor(p.x,p.y,p.x+s.width,p.y+s.height);g.pose().pushPose();try{g.pose().translate(p.x+panX,p.y+panY,0);g.pose().scale(zoom,zoom,1);if(showReference)JeiPageReference.background(g,project.document);var m=point(mx,my);ui.rootElement.render(g,m.x,m.y,pt);if(showReference)JeiPageReference.guides(g,project.document);}finally{g.flush();g.pose().popPose();if(!CanvasModalGuard.blocked(getGui()))transforms.draw(g);g.disableScissor();}}
        @Override @SuppressWarnings({"rawtypes","unchecked","removal"}) public void drawInForeground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){
            if(!isMouseOverElement(mx,my)||CanvasModalGuard.blocked(getGui()))return;
            var point=point(mx,my);var hits=RecipeCanvasSelection.hits(ui,point.x,point.y,true);
            for(var element:hits){
                java.util.List<net.minecraft.network.chat.Component> tooltip=List.of();
                if(element instanceof IngredientSlot slot&&slot.ingredient!=null){var renderer=IngredientAccess.manager.getIngredientRenderer((mezz.jei.api.ingredients.IIngredientType)slot.ingredient.getType());tooltip=renderer.getTooltip(slot.ingredient.getIngredient(),net.minecraft.world.item.TooltipFlag.Default.NORMAL);}
                if(element instanceof NativePartElement nativePart){var page=NativePage.context(element);var slot=page==null?null:page.previewSlot(nativePart);if(slot!=null)tooltip=slot.getTooltip();}
                if(!tooltip.isEmpty()){getGui().getModularUIGui().setHoverTooltip(tooltip,net.minecraft.world.item.ItemStack.EMPTY,null,null);return;}
            }
        }
        @Override public boolean mouseClicked(double mx,double my,int button){return click(mx,my,button,net.minecraft.client.gui.screens.Screen.hasControlDown(),net.minecraft.client.gui.screens.Screen.hasShiftDown(),net.minecraft.client.gui.screens.Screen.hasAltDown());}
        boolean click(double mx,double my,int button,boolean ctrl,boolean shift,boolean alt){
            if(!isMouseOverElement(mx,my))return false;
            if(!canvasInputAllowed())return simulation;
            if(button==2){transforms.pan=true;return true;}
            var p=point(mx,my);
            if(button==0){
                if(transforms.beginHandle(p,alt))return true;
                var hits=RecipeCanvasSelection.hits(ui,p.x,p.y,alt&&!transforms.isEditing());var hit=hits.isEmpty()?null:hits.get(0);
                if(alt&&!transforms.isEditing()&&hits.size()>1){var previous=selection.stream().findFirst().orElse(null);hit=hits.get((hits.indexOf(previous)+1)%hits.size());}
                if(hit==null){transforms.clear();selection.clear();transforms.pan=true;}
                else{focusElement(hit);transforms.picked(hit,p,ctrl,shift);}
                inspect();refreshHierarchy();return true;
            }
            if(button==1&&selection.size()==1&&selection.iterator().next() instanceof BlockSceneElement scene){SceneEditor.open(RecipeLayoutView.this,scene);return true;}
            return true;
        }
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(transforms.pan){panX+=dx;panY+=dy;return true;}return transforms.update(point(mx,my),net.minecraft.client.gui.screens.Screen.hasShiftDown(),net.minecraft.client.gui.screens.Screen.hasControlDown());}
        @Override public boolean mouseReleased(double mx,double my,int button){boolean wasPan=transforms.pan;transforms.pan=false;return transforms.finish()||wasPan;}
        @Override public boolean mouseWheelMove(double mx,double my,double delta){if(!isMouseOverElement(mx,my))return false;var before=point(mx,my);zoom=(float)Math.max(.1,Math.min(10,zoom+.1*delta));var p=getPosition();panX=(float)(mx-p.x-before.x*zoom);panY=(float)(my-p.y-before.y*zoom);return true;}
    }
}
