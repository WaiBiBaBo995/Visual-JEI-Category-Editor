package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.ProjectStore;
import com.pureneko.vjce.integration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.joml.Vector2f;
import java.util.*;

/** Real-world client update packets must survive capture, templates and the actual JEI cache. */
@LDLRegisterClient(name="vjce_snapshot",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneSnapshotScenario implements UIScenario {
    private static final BlockPos ORIGIN=new BlockPos(0,100,0);
    @Override public void configure(ScenarioOptions options){options.defaultTimeoutMs(30000);}
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).server("set up a real sign",sc->{
            var level=sc.level();sc.player().getAbilities().flying=true;sc.player().onUpdateAbilities();sc.player().connection.teleport(4.5,104,10.5,0,0);
                level.setBlockAndUpdate(ORIGIN.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(ORIGIN,Blocks.OAK_SIGN.defaultBlockState());
                var sign=(SignBlockEntity)level.getBlockEntity(ORIGIN);sign.setText(sign.getFrontText().setMessage(0,Component.literal("VJCE update snapshot")),true);sign.setChanged();level.sendBlockUpdated(ORIGIN,sign.getBlockState(),sign.getBlockState(),3);
        }).waitUntil("client sign state arrived", c -> c.mc().level.getBlockEntity(ORIGIN) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(0, false).getString().equals("VJCE update snapshot")).openModularUI("NBT scene editor",c->{
            var doc=VjceSmokeScenario.example();doc.width=380;doc.height=280;var p=new VjceProject(doc);var window=new EditorWindow(VjceEditor::new);window.getCurrentEditor().loadProject(p,null);
            var scene=new BlockSceneElement();scene.setId("snapshot_scene");scene.clock.playing=false;scene.animateBlockEntities=false;scene.autoFrame=false;scene.cameraRange=6;
            scene.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(20);l.top(15);l.width(340);l.height(240);});
            p.layoutView.getCurrentUI().rootElement.addChild(scene);p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());c.put("snapshotProject",p);c.put("snapshotScene",scene);
            return new ModularUI(UI.of(window),c.player());
        }).awaitModularUI().step("open NBT scene settings",c->SceneEditor.open(p(c).layoutView,scene(c)));
        click(s,"open world snapshot",c->root(c).selectId("vjce_world_open").findFirst().orElseThrow());
        for(String side:List.of("from","to"))for(int a=0;a<3;a++){
            final int axis=a;final String which=side;
            s.step("set snapshot "+side+" axis "+a,c->{var pos=ORIGIN;int[] n={pos.getX(),pos.getY(),pos.getZ()};((TextField)root(c).selectId("vjce_world_"+which+"_"+"xyz".charAt(axis)).findFirst().orElseThrow()).setText(Integer.toString(n[axis]),true);});
        }
        click(s,"frame the sign",c->root(c).selectId("vjce_world_frame").findFirst().orElseThrow());
        s.screenshot("snapshot_world_preview");
        click(s,"import native visual state",c->root(c).selectId("vjce_world_import").findFirst().orElseThrow());
        s.check("capture contains separate persistent and native update data",c->{var data=(CompoundTag)((StructuredTagEditor)root(c).selectId("vjce_scene_data").findFirst().orElseThrow()).getValue();return data.getListOrEmpty("blocks").stream().allMatch(t->((CompoundTag)t).getCompound("data").isPresent()&&((CompoundTag)t).getCompound("update").isPresent());});
        click(s,"apply snapshot",c->root(c).selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().orElseThrow().buttonContainer.getChildren().getFirst());
        s.step("verify native client state",c->{
            checkState(c,scene(c));c.put("snapshotData",scene(c).blocks.copy());
            for(var pos:scene(c).<com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer>getRenderer().renderedBlocksMap.values())for(var block:pos.snapshot()){
                var source=c.mc().level.getBlockEntity(block.offset(ORIGIN));var captured=scene(c).getDummyWorld().getBlockEntity(block);
                c.check("source world block entity was not reused",source!=captured&&captured.getLevel()!=c.mc().level);
            }
        }).screenshot("snapshot_editor_recipe")
        .step("round trip native client state through saved template",c->{p(c).captureLayout();var ui=RecipeUI.savedTemplate(p(c).document).createUI();var restored=ui.rootElement.selectId("snapshot_scene",BlockSceneElement.class).findFirst().orElseThrow();try{checkState(c,restored);c.check("snapshot NBT retained in template",restored.blocks.equals(c.get("snapshotData")));}finally{restored.release();}})
        .closeScreen().server("remove the original block", sc -> sc.level().removeBlock(ORIGIN, false))
        .openScreen("saved client state in actual JEI",c->actual(c))
        .step("actual JEI retains the sign text",c->checkState(c,c.get("snapshotActual")))
        .screenshot("snapshot_actual_jei").closeScreen();
    }

    private static void checkState(TestContext c, BlockSceneElement scene) {
        scene.ensureScene();
        c.check("native snapshot scene has no errors", scene.error.isEmpty());
        c.check("vanilla native update restores sign text", scene.getDummyWorld().getBlockEntity(BlockPos.ZERO) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(0, false).getString().equals("VJCE update snapshot"));
    }

    private static net.minecraft.client.gui.screens.Screen actual(TestContext c) {
        var doc=ProjectStore.copy(p(c).document);var type=mezz.jei.api.recipe.types.IRecipeType.create(Identifier.parse("vjce:snapshot_qa"),com.pureneko.vjce.data.CategoryDocument.Recipe.class);
        var category=new DisplayCategory<>(doc,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());var recipe=p(c).selected;
        var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,recipe,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
        var scene=category.getUIForRecipe(recipe).ui.rootElement.selectId("snapshot_scene",BlockSceneElement.class).findFirst().orElseThrow();

        c.put("snapshotActual",scene);
        return new net.minecraft.client.gui.screens.Screen(Component.literal("Native client snapshot in actual JEI")){
            @Override public void tick(){layout.tick();}
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(45,65);layout.drawRecipe(g,0,0);g.text(font,"Actual JEI: independent native client snapshot",30,30,0xffffffff,false);}
            @Override public void removed(){scene.release();super.removed();}
        };
    }
    private static VjceProject p(TestContext c){return c.get("snapshotProject");}
    private static BlockSceneElement scene(TestContext c){return c.get("snapshotScene");}
    private static UIElement root(TestContext c){return p(c).layoutView.getModularUI().ui.rootElement;}
    private static void click(ScenarioBuilder s,String name,java.util.function.Function<TestContext,UIElement> target){
        s.step("aim "+name,c->{var pos=ElementBounds.of(target.apply(c)).center();c.put("snapshotClick",pos);c.input().moveTo(pos.x,pos.y);})
        .step("press "+name,c->{var pos=c.<Vector2f>get("snapshotClick");c.input().mouseDown(pos.x,pos.y,0);})
        .step("release "+name,c->{var pos=c.<Vector2f>get("snapshotClick");c.input().mouseUp(pos.x,pos.y,0);});
    }
}
