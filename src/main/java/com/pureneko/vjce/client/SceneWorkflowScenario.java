package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.editor.ui.resource.*;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.integration.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import org.joml.Vector2f;
import java.util.*;
import java.util.function.Function;

@LDLRegisterClient(name="vjce_scene_workflow",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneWorkflowScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("unified scene workflow",c->{
            var p=new VjceProject(VjceSmokeScenario.example());p.document.width=360;p.document.height=250;
            var window=new EditorWindow(VjceEditor::new);window.getCurrentEditor().loadProject(p,null);c.put("workflowProject",p);return new ModularUI(UI.of(window),c.player());
        }).awaitModularUI().check("toolbar has one scene entry",c->p(c).layoutView.selectId("vjce_add_scene").findAny().isPresent()&&p(c).layoutView.selectId("vjce_add_entity").findAny().isEmpty()&&p(c).layoutView.selectId("vjce_configure_scene").findAny().isEmpty());
        click(s,"add scene",c->p(c).layoutView.selectId("vjce_add_scene").findFirst().orElseThrow());
        s.check("empty draft does not create a recipe element",c->p(c).layoutView.getCurrentUI().rootElement.select("*").noneMatch(BlockSceneElement.class::isInstance)&&view(c).working().isEmpty())
        .check("scene editing uses a dock view and leaves the library interactive",c->view(c).getViewContainer()!=null&&!CanvasModalGuard.blocked(view(c).getModularUI())&&view(c).getEditor().resourceView.isDisplayed())
        .step("create native block resource",c->create(c,SceneAssetResource.BLOCKS,"block"));
        drag(s,"block from library into preview",c->cell(c,SceneAssetResource.BLOCKS,"block"),c->ElementBounds.of(view(c).preview).center());
        s.check("real drag creates a referenced block instance",c->BlockSceneElement.rows(view(c).working(),"blocks").size()==1&&BlockSceneElement.rows(view(c).working(),"blocks").getCompound(0).contains("resource"))
        .step("create native entity resource",c->create(c,SceneAssetResource.ENTITIES,"entity"))
        .step("simulate a legacy definition with backward thumbnail",c->{
            var provider=c.<IResourceProvider<CompoundTag>>get("entityProvider");
            var path=c.<IResourcePath>get("entityPath");
            var data=provider.getResource(path).copy(); data.putFloat("yaw",0); provider.addResource(path,data);
            select(c,SceneAssetResource.ENTITIES).reloadResourceContainer();
        })
        .check("legacy entity thumbnail is shown from the front quarter",c->{
            var thumbnail=(BlockSceneElement)cell(c,SceneAssetResource.ENTITIES,"entity").select("*").filter(BlockSceneElement.class::isInstance).findFirst().orElseThrow();
            thumbnail.ensureScene();
            return frontQuarter(thumbnail);
        });
        drag(s,"entity from library into preview",c->cell(c,SceneAssetResource.ENTITIES,"entity"),c->ElementBounds.of(view(c).preview).center());
        s.check("entity drag faces camera at 45 degrees",c->frontQuarter(view(c).preview))
        .check("drop onto a block places entity feet on its surface",c->{var data=view(c).working();return data.getList("entities",Tag.TAG_COMPOUND).getCompound(0).getDouble("y")==data.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).getInt("y")+1;})
        .step("scroll instance angle into view",c->parameters(c).scrollToChild(view(c).selectId("vjce_entity_yaw").findFirst().orElseThrow()))
        .step("blur angle field",c->view(c).getModularUI().clearFocus())
        .step("aim angle",c->{var q=ElementBounds.of(view(c).selectId("vjce_entity_yaw").findFirst().orElseThrow()).center();c.put("anglePoint",q);c.input().moveTo(q.x,q.y);})
        .step("unfocused angle wheel",c->{var q=c.<Vector2f>get("anglePoint");c.input().scroll(q.x,q.y,1);})
        .check("unfocused decimal input stays unchanged",c->BlockSceneElement.rows(view(c).working(),"entities").getCompound(0).getDouble("yaw")==90);
        click(s,"focus entity angle",c->view(c).selectId("vjce_entity_yaw").findFirst().orElseThrow());
        s.step("focused decimal wheel",c->{var q=ElementBounds.of(view(c).selectId("vjce_entity_yaw").findFirst().orElseThrow()).center();c.put("anglePoint",q);c.input().scroll(q.x,q.y,1);})
        .check("focused decimal increments by .1",c->Math.abs(BlockSceneElement.rows(view(c).working(),"entities").getCompound(0).getDouble("yaw")-90.1)<.0001)
        .keyDown(Keys.LEFT_SHIFT).step("shift decimal wheel",c->{var q=c.<Vector2f>get("anglePoint");c.input().scroll(q.x,q.y,1);}).keyUp(Keys.LEFT_SHIFT)
        .check("shift decimal increments by one",c->Math.abs(BlockSceneElement.rows(view(c).working(),"entities").getCompound(0).getDouble("yaw")-91.1)<.0001)
        .screenshot("scene_resource_draft");
        click(s,"apply referenced scene",c->view(c).buttonContainer.getChildren().getFirst());
        s.step("remember created scene",c->{var scene=p(c).layoutView.getCurrentUI().rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElseThrow();c.put("workflowScene",scene);})
        .check("apply creates one scene with both content kinds",c->scene(c).blocks.getList("blocks",Tag.TAG_COMPOUND).size()==1&&scene(c).blocks.getList("entities",Tag.TAG_COMPOUND).size()==1)
        .step("edit global entity through native inspector",c->{var container=select(c,SceneAssetResource.ENTITIES);container.editResource(c.get("entityPath"));})
        .check("resource definition opens in inspector",c->editor(c).selectId("vjce_asset_data").findAny().isPresent())
        .step("change global entity type",c->{var edit=(StructuredTagEditor)editor(c).selectId("vjce_asset_data").findFirst().orElseThrow();var data=((CompoundTag)edit.getValue()).copy();data.putString("id","minecraft:cow");edit.setValue(data,true);})
        .step("resource edits update scene and saved-template instances",c->{
            scene(c).ensureScene();c.check("global type change updates placed entity",scene(c).sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.COW);
            p(c).captureLayout();var ui=RecipeUI.savedTemplate(p(c).document).createUI();var copy=ui.rootElement.selectId(scene(c).getId(),BlockSceneElement.class).findFirst().orElseThrow();try{copy.ensureScene();c.check("reference survives template serialization",copy.sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.COW);}finally{copy.release();}
        })
        .step("rename entity resource through native container",c->{var container=select(c,SceneAssetResource.ENTITIES);var renamed=container.renameResourceTo(c.get("entityPath"),"vjce_qa010_renamed");c.put("entityPath",renamed);})
        .step("renamed reference stays live",c->{
            try{var map=SceneAssets.class.getDeclaredField("paths");map.setAccessible(true);((Map<?,?>)map.get(null)).clear();var loaded=SceneAssets.class.getDeclaredField("loaded");loaded.setAccessible(true);loaded.setBoolean(null,false);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            scene(c).ensureScene();c.check("resource rename survives path-index reload",scene(c).missingResources.isEmpty()&&scene(c).sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.COW);
        })
        .step("edit resource after rename",c->{var edit=(StructuredTagEditor)editor(c).selectId("vjce_asset_data").findFirst().orElseThrow();var data=((CompoundTag)edit.getValue()).copy();data.putString("id","minecraft:sheep");edit.setValue(data,true);})
        .step("inspector follows renamed path",c->{scene(c).ensureScene();c.check("post-rename inspector edits affect the renamed resource",scene(c).sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.SHEEP);c.put("entityDefinition",SceneAssetResource.ENTITIES.getResourceInstance().getResource(c.get("entityPath")).copy());})
        .step("delete resource through native history",c->select(c,SceneAssetResource.ENTITIES).removeResource(c.get("entityPath"),false))
        .step("missing resource keeps its instance and transform",c->{scene(c).ensureScene();c.check("missing resource yields a recoverable placeholder",!scene(c).missingResources.isEmpty()&&scene(c).error.isEmpty()&&Math.abs(scene(c).blocks.getList("entities",Tag.TAG_COMPOUND).getCompound(0).getDouble("yaw")-91.1)<.0001);})
        .screenshot("scene_missing_resource")
        .step("restore resource at the same path",c->{var provider=c.<IResourceProvider<CompoundTag>>get("entityProvider");provider.addResource(c.get("entityPath"),c.get("entityDefinition"));select(c,SceneAssetResource.ENTITIES).reloadResourceContainer();SceneAssets.changed();})
        .step("placeholder automatically recovers",c->{scene(c).ensureScene();c.check("restoring resource recovers the same scene instance",scene(c).missingResources.isEmpty()&&scene(c).sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.SHEEP);})
        ;
        click(s,"first click existing scene",SceneWorkflowScenario::scene);
        click(s,"double click existing scene",SceneWorkflowScenario::scene);
        s.waitUntil("double click opens the existing scene editor",c->editor(c).selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().map(v->v.target==scene(c)).orElse(false))
        .step("select entity resource library",c->select(c,SceneAssetResource.ENTITIES))
        .step("orbit scene camera before next placement",c->view(c).preview.cameraYaw=-90);
        drag(s,"entity resource into object list",c->cell(c,SceneAssetResource.ENTITIES,"entity"),c->{var box=ElementBounds.of(view(c).selectId("vjce_scene_objects").findFirst().orElseThrow());return new Vector2f(box.x()+box.width()/2,box.y()+box.height()-5);});
        s.check("only the new instance faces the orbited camera",c->{var rows=BlockSceneElement.rows(view(c).working(),"entities");return rows.size()==2&&rows.getCompound(1).getFloat("yaw")==135&&Math.abs(rows.getCompound(0).getDouble("yaw")-91.1)<.0001;})
        .step("global edit during scene draft",c->{var provider=c.<IResourceProvider<CompoundTag>>get("entityProvider");var data=provider.getResource(c.get("entityPath")).copy();data.putString("id","minecraft:pig");provider.addResource(c.get("entityPath"),data);SceneAssets.changed();});
        click(s,"cancel scene draft",c->view(c).buttonContainer.getChildren().getLast());
        s.step("cancel only discards scene draft",c->{scene(c).ensureScene();c.check("cancel preserves original instance count and global resource edits",scene(c).blocks.getList("entities",Tag.TAG_COMPOUND).size()==1&&scene(c).sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.PIG);})
        .screenshot("referenced_scene_recipe").closeScreen()
        .openScreen("referenced scene in cached actual JEI",c->actual(c))
        .step("edit resources while actual JEI is open",c->{
            var entities=c.<IResourceProvider<CompoundTag>>get("entityProvider");var definition=entities.getResource(c.get("entityPath")).copy();definition.putString("id","minecraft:cow");entities.addResource(c.get("entityPath"),definition);c.put("actualDefinition",definition);
            var blocks=c.<IResourceProvider<CompoundTag>>get("blockProvider");var block=blocks.getResource(c.get("blockPath")).copy();block.getCompound("state").putString("Name","minecraft:enchanting_table");blocks.addResource(c.get("blockPath"),block);SceneAssets.changed();
        }).step("cached JEI rebuilds native entity and block entity",c->{
            var scene=c.<BlockSceneElement>get("actualScene");scene.ensureScene();var pos=scene.blocks.getList("blocks",Tag.TAG_COMPOUND).getCompound(0);
            c.check("cached JEI follows entity resource changes",scene.sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.COW);
            c.check("cached JEI follows block resource changes",scene.getDummyWorld().getBlockEntity(new BlockPos(pos.getInt("x"),pos.getInt("y"),pos.getInt("z"))) instanceof net.minecraft.world.level.block.entity.EnchantingTableBlockEntity);
        }).screenshot("resource_actual_jei")
        .step("delete referenced resource with JEI open",c->{c.<IResourceProvider<CompoundTag>>get("entityProvider").removeResource(c.get("entityPath"));SceneAssets.changed();})
        .step("cached JEI shows missing-resource placeholder",c->{var scene=c.<BlockSceneElement>get("actualScene");scene.ensureScene();c.check("missing resource is isolated inside cached JEI",!scene.missingResources.isBlank()&&scene.error.isEmpty());})
        .screenshot("resource_missing_actual_jei")
        .step("restore resource with JEI open",c->{c.<IResourceProvider<CompoundTag>>get("entityProvider").addResource(c.get("entityPath"),c.get("actualDefinition"));SceneAssets.changed();})
        .step("cached JEI recovers and retains instance transforms",c->{var scene=c.<BlockSceneElement>get("actualScene");scene.ensureScene();c.check("cached JEI recovers the original instance",scene.missingResources.isEmpty()&&scene.sceneEntities().getFirst().getType()==net.minecraft.world.entity.EntityType.COW&&Math.abs(scene.sceneEntities().getFirst().getYRot()-91.1)<.001);})
        .closeScreen();
        s.teardown("remove only fixture resources",c->{for(String key:List.of("block","entity")){var provider=c.<IResourceProvider<CompoundTag>>get(key+"Provider");var path=c.<IResourcePath>get(key+"Path");if(provider!=null&&path!=null)provider.removeResource(path);}SceneAssets.changed();});
    }
    private static boolean frontQuarter(BlockSceneElement scene){
        var direction=scene.sceneEntities().getFirst().getLookAngle();
        double radians=Math.toRadians(scene.cameraYaw);
        return Math.abs(direction.x*Math.cos(radians)+direction.z*Math.sin(radians)-Math.sqrt(.5))<.001;
    }
    private static net.minecraft.client.gui.screens.Screen actual(TestContext c){
        var project=p(c);project.captureLayout();var type=new mezz.jei.api.recipe.RecipeType<com.pureneko.vjce.data.CategoryDocument.Recipe>(net.minecraft.resources.ResourceLocation.parse("vjce:resource_qa"),com.pureneko.vjce.data.CategoryDocument.Recipe.class);
        var category=new DisplayCategory<>(project.document,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,project.selected,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
        var scene=category.getUIForRecipe(project.selected).ui.rootElement.selectId(scene(c).getId(),BlockSceneElement.class).findFirst().orElseThrow();c.put("actualScene",scene);
        return new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Resource references in actual JEI")){
            @Override public void tick(){layout.tick();}
            @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float partial){graphics.fill(0,0,width,height,0xff444444);layout.setPosition(60,60);layout.drawRecipe(graphics,0,0);}
            @Override public void removed(){scene.release();super.removed();}
        };
    }
    @SuppressWarnings("unchecked") private static ResourceProviderContainer<CompoundTag> select(TestContext c,SceneAssetResource type){
        editor(c).resourceView.selectResourceInstance(type);var container=editor(c).resourceView.select("*").filter(ResourceContainer.class::isInstance).map(ResourceContainer.class::cast).filter(v->v.resourceInstance.resource==type).findFirst().orElseThrow();
        var provider=type.getResourceInstance().listWritableProviders().getFirst();container.selectProvider(provider);return (ResourceProviderContainer<CompoundTag>)container.getSelectedProviderContainer();
    }
    private static void create(TestContext c,SceneAssetResource type,String key){var container=select(c,type);container.addNewResource(type.defaults(),"vjce_qa010_"+key);c.put(key+"Path",container.getSelected());c.put(key+"Provider",container.resourceProvider);}
    private static UIElement cell(TestContext c,SceneAssetResource type,String key){return editor(c).selectId(type.cellId(c.get(key+"Path"))).findFirst().orElseThrow();}
    private static VjceProject p(TestContext c){return c.get("workflowProject");}
    private static com.lowdragmc.lowdraglib2.editor.ui.Editor editor(TestContext c){return p(c).layoutView.getEditor();}
    private static SceneEditingView view(TestContext c){return editor(c).selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().orElseThrow();}
    private static BlockSceneElement scene(TestContext c){return c.get("workflowScene");}
    private static ScrollerView parameters(TestContext c){return (ScrollerView)view(c).selectId("vjce_scene_parameters").findFirst().orElseThrow();}
    private static void click(ScenarioBuilder s,String name,Function<TestContext,UIElement> target){
        s.step("aim "+name,c->{var p=ElementBounds.of(target.apply(c)).center();c.put("workflowPoint",p);c.input().moveTo(p.x,p.y);})
        .step("press "+name,c->{var p=c.<Vector2f>get("workflowPoint");c.input().mouseDown(p.x,p.y,0);})
        .step("release "+name,c->{var p=c.<Vector2f>get("workflowPoint");c.input().mouseUp(p.x,p.y,0);});
    }
    private static void drag(ScenarioBuilder s,String name,Function<TestContext,UIElement> source,Function<TestContext,Vector2f> target){
        s.step("aim "+name,c->{var p=ElementBounds.of(source.apply(c)).center();c.put("workflowPoint",p);c.input().moveTo(p.x,p.y);})
        .step("press "+name,c->{var p=c.<Vector2f>get("workflowPoint");c.input().mouseDown(p.x,p.y,0);})
        .step("drag "+name,c->{var p=target.apply(c);c.put("workflowDrop",p);c.input().dragTo(p.x,p.y,0);})
        .step("release "+name,c->{var p=c.<Vector2f>get("workflowDrop");c.input().mouseUp(p.x,p.y,0);});
    }
}
