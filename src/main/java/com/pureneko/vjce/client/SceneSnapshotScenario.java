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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector2f;
import java.util.*;

/** Real-world client update packets must survive capture, templates and the actual JEI cache. */
@LDLRegisterClient(name="vjce_snapshot",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneSnapshotScenario implements UIScenario {
    private static final BlockPos ORIGIN=new BlockPos(0,100,0);
    private static final List<String> TIERS=List.of("basic","advanced","elite","ultimate","creative");
    private static boolean mekanism(){return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("mekanism:basic_fluid_tank"));}
    @Override public void configure(ScenarioOptions options){options.defaultTimeoutMs(30000);}
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).server("create real filled tanks",sc->{
            var level=sc.level();sc.player().getAbilities().flying=true;sc.player().onUpdateAbilities();sc.player().connection.teleport(4.5,104,10.5,0,0);
            if(mekanism()){
                for(int i=0;i<TIERS.size();i++)for(int fluid=0;fluid<2;fluid++){
                    var pos=ORIGIN.offset(i*2,0,fluid*3);level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:"+TIERS.get(i)+"_fluid_tank")).defaultBlockState());
                    var stack=new FluidStack(fluid==0?Fluids.WATER:Fluids.LAVA,8000);stack.set(DataComponents.CUSTOM_NAME,Component.literal("VJCE fluid snapshot"));
                    fill(level.getBlockEntity(pos),stack);
                }
            }else{
                level.setBlockAndUpdate(ORIGIN.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(ORIGIN,Blocks.OAK_SIGN.defaultBlockState());
                var sign=(SignBlockEntity)level.getBlockEntity(ORIGIN);sign.setText(sign.getFrontText().setMessage(0,Component.literal("VJCE update snapshot")),true);sign.setChanged();level.sendBlockUpdated(ORIGIN,sign.getBlockState(),sign.getBlockState(),3);
            }
        }).waitUntil("client visual state arrived",c->{
            if(!mekanism())return c.mc().level.getBlockEntity(ORIGIN) instanceof SignBlockEntity sign&&sign.getFrontText().getMessage(0,false).getString().equals("VJCE update snapshot");
            for(int i=0;i<TIERS.size();i++)for(int fluid=0;fluid<2;fluid++){
                var tile=c.mc().level.getBlockEntity(ORIGIN.offset(i*2,0,fluid*3));if(tile==null||tile.getUpdateTag(c.mc().level.registryAccess()).getFloat("scale")<=0)return false;
            }return true;
        }).openModularUI("NBT scene editor",c->{
            var doc=VjceSmokeScenario.example();doc.width=380;doc.height=280;var p=new VjceProject(doc);var window=new EditorWindow(VjceEditor::new);window.getCurrentEditor().loadProject(p,null);
            var scene=new BlockSceneElement();scene.setId("snapshot_scene");scene.clock.playing=false;scene.animateBlockEntities=false;scene.autoFrame=false;scene.cameraRange=6;
            scene.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(20);l.top(15);l.width(340);l.height(240);});
            p.layoutView.getCurrentUI().rootElement.addChild(scene);p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());c.put("snapshotProject",p);c.put("snapshotScene",scene);
            return new ModularUI(UI.of(window),c.player());
        }).awaitModularUI().step("open NBT scene settings",c->SceneEditor.open(p(c).layoutView,scene(c)));
        click(s,"open world snapshot",c->root(c).selectId("vjce_world_open").findFirst().orElseThrow());
        for(String side:List.of("from","to"))for(int a=0;a<3;a++){
            final int axis=a;final String which=side;
            s.step("set snapshot "+side+" axis "+a,c->{var pos=which.equals("from")||!mekanism()?ORIGIN:ORIGIN.offset(8,0,3);int[] n={pos.getX(),pos.getY(),pos.getZ()};((TextField)root(c).selectId("vjce_world_"+which+"_"+"xyz".charAt(axis)).findFirst().orElseThrow()).setText(Integer.toString(n[axis]),true);});
        }
        click(s,"frame filled tanks",c->root(c).selectId("vjce_world_frame").findFirst().orElseThrow());
        s.screenshot("snapshot_world_preview");
        click(s,"import native visual state",c->root(c).selectId("vjce_world_import").findFirst().orElseThrow());
        s.check("capture contains separate persistent and native update data",c->{var data=(CompoundTag)((StructuredTagEditor)root(c).selectId("vjce_scene_data").findFirst().orElseThrow()).getValue();return data.getList("blocks",Tag.TAG_COMPOUND).stream().allMatch(t->((CompoundTag)t).contains("data",Tag.TAG_COMPOUND)&&((CompoundTag)t).contains("update",Tag.TAG_COMPOUND));});
        click(s,"apply snapshot",c->root(c).selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().orElseThrow().buttonContainer.getChildren().getFirst());
        s.step("verify every tier and native client state",c->{
            checkState(c,scene(c),true);c.put("snapshotData",scene(c).blocks.copy());
            for(var pos:scene(c).getRenderer().renderedBlocksMap.values())for(var block:pos.snapshot()){
                var source=c.mc().level.getBlockEntity(block.offset(ORIGIN));var captured=scene(c).getDummyWorld().getBlockEntity(block);
                c.check("source world block entity was not reused",source!=captured&&captured.getLevel()!=c.mc().level);
            }
        }).screenshot("snapshot_editor_recipe")
        .step("round trip native client state through saved template",c->{p(c).captureLayout();var ui=RecipeUI.savedTemplate(p(c).document).createUI();var restored=ui.rootElement.selectId("snapshot_scene",BlockSceneElement.class).findFirst().orElseThrow();try{checkState(c,restored,true);c.check("snapshot NBT retained in template",restored.blocks.equals(c.get("snapshotData")));}finally{restored.release();}})
        .closeScreen().server("change original tanks after capture",sc->{if(mekanism())for(int i=0;i<TIERS.size();i++)for(int f=0;f<2;f++)fill(sc.level().getBlockEntity(ORIGIN.offset(i*2,0,f*3)),FluidStack.EMPTY);})
        .openScreen("saved client state in actual JEI",c->actual(c,false))
        .step("actual JEI retains liquid fill and components",c->checkState(c,c.get("snapshotActual"),true))
        .screenshot("snapshot_actual_jei")
        .step("export native snapshot pixel coordinates",c->{
            var bounds=ElementBounds.of(c.<BlockSceneElement>get("snapshotActual"));double scale=c.mc().getWindow().getGuiScale();
            try{java.nio.file.Files.writeString(c.outDir().resolve("scene-snapshot-pixels.json"),ProjectStore.JSON.toJson(Map.of("mekanism",mekanism(),"filled","screenshots/vjce_snapshot/*_snapshot_actual_jei.png","empty","screenshots/vjce_snapshot/*_snapshot_zero_scale_control.png","bounds",List.of(bounds.x()*scale,bounds.y()*scale,bounds.width()*scale,bounds.height()*scale))));}catch(Exception e){throw new IllegalStateException(e);}
        }).closeScreen().openScreen("zero liquid-scale control in actual JEI",c->actual(c,true))
        .screenshot("snapshot_zero_scale_control").closeScreen();
    }
    private static void fill(net.minecraft.world.level.block.entity.BlockEntity tile,FluidStack stack){
        try{var tank=tile.getClass().getField("fluidTank").get(tile);var method=tank.getClass().getMethod("setStack",FluidStack.class);method.setAccessible(true);method.invoke(tank,stack);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void checkState(TestContext c,BlockSceneElement scene,boolean visible){
        scene.ensureScene();c.check("native snapshot scene has no errors",scene.error.isEmpty());
        if(!mekanism()){c.check("vanilla native update restores sign text",scene.getDummyWorld().getBlockEntity(BlockPos.ZERO) instanceof SignBlockEntity sign&&sign.getFrontText().getMessage(0,false).getString().equals("VJCE update snapshot"));return;}
        for(var tag:scene.blocks.getList("blocks",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;var pos=new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z"));var actual=scene.getDummyWorld().getBlockEntity(pos).getUpdateTag(scene.getDummyWorld().registryAccess());var expected=row.getCompound("update");
            c.check("native fluid, components and liquid scale restored at "+pos,actual.getCompound("fluid").equals(expected.getCompound("fluid"))&&actual.getFloat("scale")>0&&Math.abs(actual.getFloat("scale")-expected.getFloat("scale"))<.00001f);
        }
    }
    private static net.minecraft.client.gui.screens.Screen actual(TestContext c,boolean zeroScale){
        var doc=ProjectStore.copy(p(c).document);var type=new mezz.jei.api.recipe.RecipeType<com.pureneko.vjce.data.CategoryDocument.Recipe>(ResourceLocation.parse("vjce:snapshot_qa"),com.pureneko.vjce.data.CategoryDocument.Recipe.class);
        var category=new DisplayCategory<>(doc,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());var recipe=p(c).selected;
        var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,recipe,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
        var scene=category.getUIForRecipe(recipe).ui.rootElement.selectId("snapshot_scene",BlockSceneElement.class).findFirst().orElseThrow();
        if(zeroScale&&mekanism()){scene.blocks=scene.blocks.copy();for(var tag:scene.blocks.getList("blocks",Tag.TAG_COMPOUND))((CompoundTag)tag).getCompound("update").putFloat("scale",0);}
        c.put("snapshotActual",scene);
        return new net.minecraft.client.gui.screens.Screen(Component.literal("Native client snapshot in actual JEI")){
            @Override public void tick(){layout.tick();}
            @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(45,65);layout.drawRecipe(g,0,0);g.drawString(font,"Actual JEI: independent native client snapshot",30,30,0xffffffff,false);}
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
