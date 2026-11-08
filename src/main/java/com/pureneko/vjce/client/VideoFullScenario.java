package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.*;
import com.lowdragmc.lowdraglib2.editor.ui.resource.*;
import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.utils.CursorOverlay;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Vector2f;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Development-only real product takes for the introduction film. */
@LDLRegisterClient(name="vjce_video_full", group="vjce_video", registry=UIScenario.REGISTRY, environment=RegistrationEnvironment.DEV_ONLY)
public final class VideoFullScenario implements UIScenario {
    @Override public void configure(ScenarioOptions o){o.defaultTimeoutMs(45000).scenarioTimeoutMs(240000);}
    @Override public void define(ScenarioBuilder s){
        String phase=System.getProperty("vjce.video.phase","");
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null)
          .openModularUI("real VJCE editor",c->{var w=new EditorWindow(VjceEditor::new);c.put("filmEditor",w.getCurrentEditor());return new ModularUI(UI.of(w),c.player());})
          .awaitModularUI().waitMs(1800)
          .step("ready to record",c->mark(c,"ready"))
          .waitUntil("recorder active",c->Files.exists(c.outDir().resolve("recorder-ready")))
          .waitMs(1500).step("library view",c->mark(c,"library")).waitMs(2500);
        switch(phase){
            case "authoring" -> authoring(s);
            case "native" -> nativePage(s);
            case "resources" -> resources(s);
            case "snapshot" -> snapshot(s);
            case "create" -> create(s);
            case "publish" -> publish(s);
            default -> throw new IllegalArgumentException(phase);
        }
        s.waitMs(1500).step("take done",c->mark(c,"done")).closeScreen();
    }
    private static Editor editor(TestContext c){return c.get("filmEditor");}
    private static VjceProject p(TestContext c){return (VjceProject)editor(c).getCurrentProject();}
    private static UIElement root(TestContext c){return editor(c).getModularUI().ui.rootElement;}
    private static UIElement el(TestContext c,String id){return root(c).selectId(id).findFirst().orElseThrow();}
    private static Button button(TestContext c,String key){String text=Component.translatable(key).getString();return editor(c).select("*").filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.text.getText().getString().equals(text)).findFirst().orElseThrow();}
    private static void mark(TestContext c,String name){VideoDemoScenario.mark(c,name);}
    private static void click(ScenarioBuilder s,String name,Function<TestContext,UIElement> e){VideoDemoScenario.click(s,name,e);}
    private static Vector2f center(UIElement e){return e.getWorldMouse(e.getPositionX()+e.getSizeWidth()/2,e.getPositionY()+e.getSizeHeight()/2);}
    private static UIElement heading(TestContext c){return p(c).layoutView.getCurrentUI().rootElement.selectId("video_heading").findFirst().orElseThrow();}
    private static void clickAt(ScenarioBuilder s,String name,Function<TestContext,Vector2f> point){
        VideoDemoScenario.move(s,name,"clickAt",point);
        s.step("press "+name,c->{var v=c.<Vector2f>get("clickAt");c.input().mouseDown(v.x,v.y,0);VideoDemoScenario.trace(c,"down",v);})
         .step("release "+name,c->{var v=c.<Vector2f>get("clickAt");c.input().mouseUp(v.x,v.y,0);VideoDemoScenario.trace(c,"up",v);}).waitMs(500);
    }
    private static void field(ScenarioBuilder s,String id,String value){
        click(s,"focus "+id,c->el(c,id));
        s.step("edit "+id,c->((TextField)el(c,id)).setText(value,true)).waitMs(800);
    }
    private static void load(ScenarioBuilder s,String name,Function<TestContext,VjceProject> factory){
        s.step(name,c->editor(c).loadProject(factory.apply(c),null)).waitMs(1800);
    }
    private static void layoutLabel(TestContext c,String id,String key,int x,int y){
        var label=new Label().setText(key);label.setId(id);
        label.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);l.left(x);l.top(y);});
        label.textStyle(style->style.textColor(0xff404040));p(c).layoutView.getCurrentUI().rootElement.addChild(label);
    }
    @SuppressWarnings("unchecked") private static void authoring(ScenarioBuilder s){
        click(s,"new category",c->button(c,"vjce.new"));
        s.step("new category marker",c->{p(c).dataView.getViewContainer().selectView(p(c).dataView);mark(c,"new");}).waitMs(1800);
        click(s,"category settings",c->el(c,"vjce_category_settings"));
        field(s,"edit_title",Component.translatable("vjce.video.title").getString());
        field(s,"edit_icon","mekanism:enrichment_chamber");
        field(s,"edit_width","330");field(s,"edit_height","208");
        s.step("category form complete",c->{mark(c,"category");p(c).layoutView.reload();}).waitMs(4000);
        click(s,"recipe settings",c->el(c,"vjce_recipe_settings"));
        click(s,"add material slot",c->el(c,"vjce_add_slot_INPUT"));
        s.step("show ingredient section",c->{editor(c).inspectorView.select("*").filter(ConfiguratorGroup.class::isInstance).map(ConfiguratorGroup.class::cast).forEach(g->g.setCollapse(false));mark(c,"slot");}).waitMs(1500);
        click(s,"choose ingredient",c->el(c,"vjce_choose_ingredient"));
        field(s,"vjce_ingredient_search_text","minecraft:polished_deepslate");
        s.waitMs(1600).step("search results",c->mark(c,"search"));
        click(s,"select deepslate",c->el(c,"vjce_ingredient_cell"));
        s.step("expand quantity",c->editor(c).inspectorView.select("*").filter(ConfiguratorGroup.class::isInstance).map(ConfiguratorGroup.class::cast).forEach(g->g.setCollapse(false))).waitMs(800);
        field(s,"edit_amount","35");
        s.step("material is display only",c->{((Selector<String>)el(c,"edit_role")).setSelected("RENDER_ONLY",true);mark(c,"role");c.check("real ingredient and amount",p(c).selected.slots.getFirst().ingredients.getFirst().item.equals("minecraft:polished_deepslate")&&p(c).selected.slots.getFirst().ingredients.getFirst().amount==35);}).waitMs(4000);
        s.step("complete remaining actual materials",c->{
            var full=VideoDemoScenario.document();p(c).document.id=full.id;p(c).document.title=full.title;p(c).document.icon=full.icon;
            p(c).selected.slots.clear();p(c).selected.slots.addAll(full.recipes.getFirst().slots);p(c).layoutView.reload();p(c).dataView.refresh();
            p(c).layoutView.getViewContainer().selectView(p(c).layoutView);
            var r=p(c).layoutView.getCurrentUI().rootElement;r.selectId("duration").toList().forEach(UIElement::removeSelf);
            layoutLabel(c,"video_heading","vjce.video.title",45,10);layoutLabel(c,"video_caption","vjce.video.caption",12,181);layoutLabel(c,"video_reference","vjce.video.reference",12,193);
            heading(c).layout(l->{l.width(180);l.height(14);});
            p(c).layoutView.hierarchy.loadUI(p(c).layoutView.getCurrentUI());p(c).layoutView.graphView.setScale(1);p(c).layoutView.graphView.setOffsetX(0);p(c).layoutView.graphView.setOffsetY(0);mark(c,"layout");
        }).waitMs(3000);
        s.keyDown(Keys.LEFT_SHIFT);
        drag(s,"move title on canvas",c->center(heading(c)),c->new Vector2f(-33,0));
        s.keyUp(Keys.LEFT_SHIFT).step("verify real title move",c->c.check("title actually moved",Math.abs(heading(c).getLayoutX()-45)>10)).waitMs(1800).keyDown(Keys.LEFT_CONTROL);
        clickAt(s,"enter resize handles",c->center(heading(c)));
        s.keyUp(Keys.LEFT_CONTROL).waitMs(1500).step("resize marker",c->{c.check("title selected in transform mode",p(c).layoutView.transforms.isEditing()&&p(c).layoutView.hierarchy.getSelectedOne().orElse(null)==heading(c));c.put("titleWidth",heading(c).getSizeWidth());mark(c,"resize");});
        drag(s,"resize title",c->p(c).layoutView.transforms.handlePoint(p(c).layoutView.getCurrentUI().rootElement.selectId("video_heading").findFirst().orElseThrow(),1,0),c->new Vector2f(20,0));
        s.waitMs(2200).step("clean final title placement",c->{c.check("title width actually changed",heading(c).getSizeWidth()>c.<Float>get("titleWidth")+10);p(c).layoutView.transforms.clear();p(c).captureLayout();mark(c,"layout-done");}).waitMs(6000);
    }
    private static void nativePage(ScenarioBuilder s){
        load(s,"load actual furnace category",c->VjceEditor.projectForCategory("minecraft:smelting"));
        s.step("native before",c->{p(c).layoutView.graphView.setScale(1.5f);p(c).layoutView.graphView.setOffsetX(0);p(c).layoutView.graphView.setOffsetY(0);c.put("nativeX",NativePage.part(p(c).layoutView.getCurrentUI().rootElement,"slot_0").getLayoutX());mark(c,"before");}).waitMs(6000);
        s.keyDown(Keys.LEFT_SHIFT);
        drag(s,"move original input slot",c->center(NativePage.part(p(c).layoutView.getCurrentUI().rootElement,"slot_0")),c->new Vector2f(24,8));
        s.keyUp(Keys.LEFT_SHIFT).step("native changed",c->{mark(c,"edit");c.check("source remains real category",p(c).document.target.equals("minecraft:smelting"));c.check("native slot actually moved",Math.abs(NativePage.part(p(c).layoutView.getCurrentUI().rootElement,"slot_0").getLayoutX()-c.<Float>get("nativeX"))>5);}).waitMs(3500)
         .step("native preview",c->{p(c).captureLayout();p(c).layoutView.startSimulation();mark(c,"after");}).waitMs(12000);
    }
    @SuppressWarnings("unchecked") private static ResourceProviderContainer<CompoundTag> provider(TestContext c,SceneAssetResource type){
        editor(c).resourceView.selectResourceInstance(type);
        var container=editor(c).resourceView.select("*").filter(ResourceContainer.class::isInstance).map(ResourceContainer.class::cast).filter(v->v.resourceInstance.resource==type).findFirst().orElseThrow();
        container.selectProvider(type.getResourceInstance().listWritableProviders().getFirst());
        return (ResourceProviderContainer<CompoundTag>)container.getSelectedProviderContainer();
    }
    private static void resources(ScenarioBuilder s){
        load(s,"separate resource example",c->{var doc=new CategoryDocument();doc.width=330;doc.height=208;doc.recipes.add(new CategoryDocument.Recipe());return new VjceProject(doc);});
        click(s,"add independent scene",c->el(c,"vjce_add_scene"));
        for(var type:List.of(SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES)){
            String kind=type.entity?"entity":"block";
            s.step("create "+kind+" resource",c->{var container=provider(c,type);container.addNewResource(type.defaults(),"vjce_video_"+kind+"_"+System.currentTimeMillis());c.put("filmResource",container.getSelected());mark(c,kind);}).waitMs(2000);
            drag(s,"drag "+kind+" into real scene",c->ElementBounds.of(el(c,type.cellId(c.get("filmResource")))).center(),c->ElementBounds.of(el(c,"vjce_scene_preview")).center(),true);
            s.waitMs(2500);
        }
        s.step("resource placement",c->{mark(c,"placed");var view=(SceneEditingView)el(c,"vjce_scene_dialog");c.check("both actual drag kinds",BlockSceneElement.rows(view.working(),"blocks").size()==1&&BlockSceneElement.rows(view.working(),"entities").size()==1);}).waitMs(2500);
        click(s,"apply resource scene",c->el(c,"vjce_scene_apply"));
        s.step("preview resources",c->{p(c).layoutView.startSimulation();mark(c,"preview");}).waitMs(14000);
    }
    private static VjceProject saved(){
        var doc=com.pureneko.vjce.VisualJeiCategoryEditor.STORE.loadAll().stream().filter(d->d.id.equals("vjce:video/processing_station")).findFirst().orElseThrow();
        return new VjceProject(ProjectStore.copy(doc));
    }
    private static void publish(ScenarioBuilder s){
        load(s,"reopen finished project",c->saved());
        s.step("save editable project",c->{try{var file=com.pureneko.vjce.VisualJeiCategoryEditor.STORE.root.resolve("projects/processing-station.vjce").toFile();Files.createDirectories(file.toPath().getParent());editor(c).saveAsProject(file,()->{});mark(c,"save");}catch(Exception e){throw new IllegalStateException(e);}}).waitMs(3000);
        click(s,"confirm project save",c->root(c).select("*").filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.text.getText().getString().equals("确认")).findFirst().orElseThrow());
        s.waitMs(1800).step("verify saved project",c->c.check("editable project exists",Files.isRegularFile(com.pureneko.vjce.VisualJeiCategoryEditor.STORE.root.resolve("projects/processing-station.vjce"))))
         .step("open data actions",c->p(c).dataView.getViewContainer().selectView(p(c).dataView)).waitMs(2000);
        click(s,"save and apply",c->button(c,"vjce.publish"));
        s.step("published marker",c->mark(c,"published")).waitMs(6000);
        click(s,"export modpack archive",c->button(c,"vjce.export"));
        s.step("verify actual archive",c->{c.check("modpack export exists",Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("exports/vjce-modpack.zip")));mark(c,"export");}).waitMs(10000);
    }
    private static void snapshot(ScenarioBuilder s){
        load(s,"saved independent scene",c->saved());
        s.step("show snapshot before world change",c->{p(c).layoutView.startSimulation();mark(c,"before");}).waitMs(6000)
         .server("change original world positions only in filming world",sc->{for(int x=8;x<=14;x++)for(int y=80;y<=83;y++)for(int z=8;z<=12;z++)sc.level().setBlockAndUpdate(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());})
         .step("snapshot after source changed",c->{var scene=p(c).layoutView.canvas.getCanvasModularUI().ui.rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElseThrow();scene.ensureScene();c.check("saved snapshot retains 42 blocks",scene.blocks.getList("blocks",10).size()==42&&(Object)scene.getDummyWorld()!=c.mc().level);mark(c,"after");}).waitMs(14000);
    }
    private static void create(ScenarioBuilder s){
        load(s,"Create mechanical display",c->{var doc=new CategoryDocument();doc.width=330;doc.height=190;doc.recipes.add(new CategoryDocument.Recipe());var p=new VjceProject(doc);return p;});
        for(var preset:CreateAnimationElement.Preset.values()){
            s.step("real Create "+preset,c->{var r=p(c).layoutView.getCurrentUI().rootElement;r.clearAllChildren();var a=new CreateAnimationElement();a.preset=preset;a.setId("video_create");a.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);l.left(80);l.top(20);l.width(170);l.height(150);});r.addChild(a);c.check("Create dependency present",a.available());p(c).layoutView.hierarchy.loadUI(p(c).layoutView.getCurrentUI());p(c).layoutView.startSimulation();mark(c,preset.name().toLowerCase(Locale.ROOT));}).waitMs(11000)
             .step("stop preset",c->p(c).layoutView.stopSimulation());
        }
    }
    private static void drag(ScenarioBuilder s,String name,Function<TestContext,Vector2f> source,Function<TestContext,Vector2f> delta){drag(s,name,source,delta,false);}
    private static void drag(ScenarioBuilder s,String name,Function<TestContext,Vector2f> source,Function<TestContext,Vector2f> target,boolean absolute){
        VideoDemoScenario.move(s,name,"fullDragStart",source);
        s.step("press "+name,c->{var point=c.<Vector2f>get("fullDragStart");c.input().mouseDown(point.x,point.y,0);VideoDemoScenario.trace(c,"down",point);c.put("fullDragTo",absolute?target.apply(c):new Vector2f(point).add(target.apply(c)));c.put("fullDragTime",System.nanoTime());})
         .waitUntil("drag "+name,c->{double f=Math.min(1,(System.nanoTime()-c.<Long>get("fullDragTime"))/1_300_000_000.0);var point=new Vector2f(c.<Vector2f>get("fullDragStart")).lerp(c.get("fullDragTo"),(float)(f*f*(3-2*f)));c.input().dragTo(point.x,point.y,0);c.put("videoCursor",point);VideoDemoScenario.trace(c,"move",point);return f>=1;})
         .waitMs(700).step("release "+name,c->{var point=c.<Vector2f>get("fullDragTo");c.input().mouseUp(point.x,point.y,0);VideoDemoScenario.trace(c,"up",point);}).waitMs(900);
    }
}
