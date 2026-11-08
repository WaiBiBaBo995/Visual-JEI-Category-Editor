package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import com.pureneko.vjce.integration.*;

@LDLRegisterClient(name="vjce_scene",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("scene editor",c->{
            var doc=VjceSmokeScenario.example();doc.width=400;doc.height=180;var p=new VjceProject(doc);var w=new EditorWindow(VjceEditor::new);w.getCurrentEditor().loadProject(p,null);c.put("p",p);
            var scene=new BlockSceneElement();scene.setId("scene_test");
            try{scene.blocks=TagParser.parseTag("{blocks:[{x:0,y:0,z:0,state:{Name:\"minecraft:enchanting_table\"},data:{}},{x:1,y:0,z:0,state:{Name:\"minecraft:oak_stairs\",Properties:{facing:\"east\"}},data:{}}]}");}catch(Exception e){throw new IllegalStateException(e);}
            scene.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(15);l.top(40);l.width(120);l.height(120);});p.layoutView.getCurrentUI().rootElement.addChild(scene);scene.clock.playing=false;c.put("scene",scene);
            for(var preset:CreateAnimationElement.Preset.values()){var a=new CreateAnimationElement();a.preset=preset;a.setId("create_"+preset);a.clock.playing=false;int x=140+preset.ordinal()*85;a.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(x);l.top(40);l.width(85);l.height(110);});p.layoutView.getCurrentUI().rootElement.addChild(a);}
            p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());return new ModularUI(UI.of(w),c.player());
        }).awaitModularUI().waitUntil("scene built",c->scene(c).getDummyWorld()!=null)
        .check("multiple blocks and block state restored",c->scene(c).getDummyWorld().getBlockState(new BlockPos(1,0,0)).getValue(net.minecraft.world.level.block.StairBlock.FACING)==net.minecraft.core.Direction.EAST)
        .check("real block entity created",c->scene(c).getDummyWorld().getBlockEntity(BlockPos.ZERO) instanceof EnchantingTableBlockEntity)
        .step("native block entity animation ticks and pauses",c->{var scene=scene(c);var be=(EnchantingTableBlockEntity)scene.getDummyWorld().getBlockEntity(BlockPos.ZERO);int before=be.time;scene.clock.playing=true;for(int i=0;i<5;i++)scene.screenTick();c.check("native BE animation advances",be.time>before);int paused=be.time;scene.clock.playing=false;for(int i=0;i<3;i++)scene.screenTick();c.check("pause stops BE ticks",be.time==paused);})
        .step("source scene survives template round trip",c->{var p=p(c);var template=p.layoutView.getCurrentUI().toTemplate();var ui=template.createUI();var copy=ui.rootElement.selectId("scene_test",BlockSceneElement.class).findFirst().orElseThrow();copy.ensureScene();c.check("block data serialized",copy.blocks.equals(scene(c).blocks));c.check("world is not shared",copy.getDummyWorld()!=scene(c).getDummyWorld());copy.release();})
        .step("Create optional animation clock",c->{boolean create=net.neoforged.fml.ModList.get().isLoaded("create");for(var preset:CreateAnimationElement.Preset.values())c.check(preset+" dependency state",p(c).layoutView.getCurrentUI().rootElement.selectId("create_"+preset,CreateAnimationElement.class).findFirst().orElseThrow().available()==create);
            if(create)CreateAnimation.scoped(7,()->{try{float t=(float)Class.forName("net.createmod.catnip.animation.AnimationTickHolder").getMethod("getRenderTime").invoke(null);c.check("Create preset uses local clock",Math.abs(t-7)<.01);}catch(Exception e){throw new IllegalStateException(e);}});
        }).screenshot("scene_edit_paused")
        .step("play scene preview",c->p(c).layoutView.startSimulation())
        .check("preview animation runs",c->p(c).layoutView.canvas.getCanvasModularUI().ui.rootElement.selectId("scene_test",BlockSceneElement.class).findFirst().orElseThrow().clock.playing)
        .screenshot("scene_preview_animated")
        .step("preview reset and stop",c->{var copy=p(c).layoutView.canvas.getCanvasModularUI().ui.rootElement.selectId("scene_test",BlockSceneElement.class).findFirst().orElseThrow();SceneEditor.reset(copy);copy.ensureScene();c.check("reset rebuilds BE state",((EnchantingTableBlockEntity)copy.getDummyWorld().getBlockEntity(BlockPos.ZERO)).time==0);p(c).layoutView.stopSimulation();c.check("removed scene releases world",copy.getDummyWorld()==null);})
        .step("open block editor",c->SceneEditor.open(p(c).layoutView,scene(c)))
        .check("block editor uses StructuredTagEditor",c->p(c).layoutView.getModularUI().ui.rootElement.selectId("vjce_scene_data").findFirst().orElseThrow() instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.StructuredTagEditor)
        .screenshot("structured_block_editor")
        .step("close scene edit page and save layout",c->{p(c).layoutView.getModularUI().ui.rootElement.selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().orElseThrow().close();p(c).captureLayout();})
        .openScreen("actual JEI block scene",c->{
            var p=p(c);var type=new mezz.jei.api.recipe.RecipeType<com.pureneko.vjce.data.CategoryDocument.Recipe>(net.minecraft.resources.ResourceLocation.parse("vjce:scene_qa"),com.pureneko.vjce.data.CategoryDocument.Recipe.class);
            var category=new DisplayCategory<>(p.document,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(java.util.List.of());var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,p.selected,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
            c.check("actual JEI scene keeps real ingredient slots",layout.getRecipeSlotsView().getSlotViews().size()==p.selected.slots.size());
            return new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Block scene in actual JEI")){
                @Override public void tick(){layout.tick();}
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(60,100);layout.drawRecipe(g,0,0);g.drawString(font,"Actual JEI: saved blocks, native block entity animation, optional Create presets",30,40,0xffffffff,false);}
            };
        }).screenshot("scene_actual_jei").step("release cached JEI scene resources",c->{SceneResources.releaseAll();c.check("cached scene resources are explicitly releasable",SceneResources.activeCount()==0);}).closeScreen();
    }
    private static VjceProject p(TestContext c){return c.get("p");}
    private static BlockSceneElement scene(TestContext c){return c.get("scene");}
}
