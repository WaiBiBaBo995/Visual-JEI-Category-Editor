package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.integration.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.Function;

@LDLRegisterClient(name="vjce_world",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class WorldCaptureScenario implements UIScenario {
    private static final BlockPos ORIGIN=new BlockPos(8,80,8),LARGE=new BlockPos(32,90,0);
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).server("prepare real-world structure",sc->{
            var level=sc.level();sc.player().getAbilities().flying=true;sc.player().onUpdateAbilities();sc.player().connection.teleport(8.5,83,14.5,0,0);
            for(var pos:BlockPos.betweenClosed(ORIGIN.offset(0,-1,0),ORIGIN.offset(2,0,2)))level.setBlockAndUpdate(pos,Blocks.GOLD_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(ORIGIN.offset(0,0,1),Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.EAST));
            var signPos=ORIGIN.offset(1,0,0);level.setBlockAndUpdate(signPos,Blocks.OAK_SIGN.defaultBlockState());
            var sign=(SignBlockEntity)level.getBlockEntity(signPos);sign.setText(sign.getFrontText().setMessage(0,Component.literal("VJCE world QA")),true);sign.setChanged();level.sendBlockUpdated(signPos,sign.getBlockState(),sign.getBlockState(),3);
            var stand=EntityType.ARMOR_STAND.create(level);stand.moveTo(9.5,81,9.5,30,5);stand.setNoGravity(true);stand.setInvulnerable(true);stand.setCustomName(Component.literal("VJCE capture QA"));stand.setHeadPose(new net.minecraft.core.Rotations(15,25,0));stand.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));level.addFreshEntity(stand);sc.put("worldZombie",stand.getUUID());
            for(var pos:BlockPos.betweenClosed(LARGE,LARGE.offset(16,16,16)))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        }).timeoutMs(15000).waitUntil("source data synchronized",c->c.mc().level.hasChunkAt(LARGE.offset(16,0,16))&&c.mc().level.getBlockState(LARGE.offset(16,16,16)).is(Blocks.STONE)&&sourceEntity(c)!=null&&c.mc().level.getBlockEntity(ORIGIN.offset(1,0,0))!=null&&c.mc().level.getBlockEntity(ORIGIN.offset(1,0,0)).saveWithoutMetadata(c.mc().level.registryAccess()).toString().contains("VJCE world QA"))
        .openModularUI("world import editor",c->{var p=new VjceProject(VjceSmokeScenario.example());p.document.width=360;p.document.height=220;var w=new EditorWindow(VjceEditor::new);w.getCurrentEditor().loadProject(p,null);var scene=BlockSceneElement.entityScene();scene.setId("world_scene");scene.clock.playing=false;scene.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(15);l.top(20);l.width(200);l.height(170);});p.layoutView.getCurrentUI().rootElement.addChild(scene);p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());c.put("worldProject",p);c.put("worldScene",scene);return new ModularUI(UI.of(w),c.player());}).awaitModularUI()
        .step("open scene settings",c->SceneEditor.open(project(c).layoutView,scene(c)));
        click(s,"world selection",c->root(c).selectId("vjce_world_open").findFirst().orElseThrow());
        s.check("entity import defaults off",c->!dialog(c).includeEntities.isOn())
        .check("world preview is left of narrow parameters and nearly full height",c->{var d=dialog(c);var preview=ElementBounds.of(d.preview);var split=(com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView)root(c).selectId("vjce_world_split").findFirst().orElseThrow();var right=ElementBounds.of(split.second);return preview.x()<right.x()&&preview.width()>right.width()*2&&preview.height()>d.overlay.getSizeHeight()*.75;})
        .check("world numeric fields use ore material",c->field(c,"range_x").getStyle().backgroundTexture()==com.lowdragmc.lowdraglib2.gui.ui.styletemplate.OreSprites.RECT2);
        click(s,"collapse world parameters",c->root(c).selectId("vjce_world_toggle_settings").findFirst().orElseThrow());
        s.check("collapsed parameters maximize preview width",c->dialog(c).preview.getSizeWidth()>dialog(c).overlay.getSizeWidth()*.94);
        click(s,"restore world parameters",c->root(c).selectId("vjce_world_toggle_settings").findFirst().orElseThrow());
        controls(s);
        s.step("frame test structure",c->dialog(c).preview.frame(ORIGIN.offset(-1,-1,-1),ORIGIN.offset(3,3,3)));
        corner(s,"first",ORIGIN);corner(s,"second",ORIGIN.offset(2,0,2));
        s.typeInto("#vjce_world_to_y","81")
        .check("Shift clicks set both real world corners",c->dialog(c).selection().min().equals(ORIGIN)&&dialog(c).selection().max().equals(ORIGIN.offset(2,1,2)))
        .screenshot("world_two_corners");
        click(s,"import blocks only",c->root(c).selectId("vjce_world_import").findFirst().orElseThrow());
        s.check("world import changes working copy only",c->working(c).getList("blocks",Tag.TAG_COMPOUND).size()==9&&scene(c).blocks.getList("blocks",Tag.TAG_COMPOUND).isEmpty())
        .check("unchecked entity import clears previous and manually added entities",c->working(c).getList("entities",Tag.TAG_COMPOUND).isEmpty());
        click(s,"cancel scene settings",c->parent(c).buttonContainer.getChildren().getLast());
        s.check("cancel discards world import",c->scene(c).blocks.getList("blocks",Tag.TAG_COMPOUND).isEmpty())
        .step("reopen scene settings",c->SceneEditor.open(project(c).layoutView,scene(c)));
        click(s,"world selection with entities",c->root(c).selectId("vjce_world_open").findFirst().orElseThrow());
        coordinates(s,ORIGIN,ORIGIN.offset(2,1,2));
        click(s,"include source entities",c->dialog(c).includeEntities);
        click(s,"import full selection",c->root(c).selectId("vjce_world_import").findFirst().orElseThrow());
        s.check("optional entity capture replaces entity list",c->working(c).getList("entities",Tag.TAG_COMPOUND).size()==1&&working(c).getList("entities",Tag.TAG_COMPOUND).getCompound(0).getString("id").equals("minecraft:armor_stand"))
        .check("block entity NBT captured",c->working(c).toString().contains("VJCE world QA"));
        click(s,"apply imported scene",c->parent(c).buttonContainer.getChildren().getFirst());
        s.check("applied structure retains states and equipment",c->{var scene=scene(c);scene.ensureScene();return scene.error.isEmpty()&&scene.getDummyWorld().getBlockState(new BlockPos(0,0,1)).getValue(StairBlock.FACING)==net.minecraft.core.Direction.EAST&&((LivingEntity)scene.sceneEntities().getFirst()).getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET);})
        .check("snapshot has its own entity identity",c->!scene(c).sceneEntities().getFirst().getUUID().equals(sourceEntity(c).getUUID()))
        .check("sign text restored into independent block entity",c->scene(c).getDummyWorld().getBlockEntity(new BlockPos(1,0,0)) instanceof SignBlockEntity sign&&sign.getFrontText().getMessage(0,false).getString().equals("VJCE world QA"))
        .check("real world was not modified by import",c->sourceEntity(c).getUUID().equals(c.get("worldZombie"))&&c.mc().level.getBlockState(ORIGIN).is(Blocks.GOLD_BLOCK)&&c.mc().level.getBlockEntity(ORIGIN.offset(1,0,0)).saveWithoutMetadata(c.mc().level.registryAccess()).toString().contains("VJCE world QA"))
        .screenshot("world_import_applied")
        .step("capture boundaries and atomic errors",c->boundaries(c))
        .step("save imported layout",c->{project(c).captureLayout();var copy=RecipeUI.savedTemplate(project(c).document).createUI();var restored=copy.rootElement.selectId("world_scene",BlockSceneElement.class).findFirst().orElseThrow();restored.ensureScene();c.check("world snapshot survives template save",restored.blocks.equals(scene(c).blocks)&&restored.sceneEntities().size()==1);restored.release();})
        .closeScreen()
        .server("change original world after snapshot",sc->sc.level().setBlockAndUpdate(ORIGIN,Blocks.LAPIS_BLOCK.defaultBlockState()))
        .waitUntil("source changed",c->c.mc().level.getBlockState(ORIGIN).is(Blocks.LAPIS_BLOCK))
        .openScreen("world snapshot in actual JEI",c->{
            var p=project(c);var probe=RecipeUI.savedTemplate(p.document).createUI().rootElement.selectId("world_scene",BlockSceneElement.class).findFirst().orElseThrow();probe.ensureScene();c.check("snapshot independent of later world changes",probe.getDummyWorld().getBlockState(BlockPos.ZERO).is(Blocks.GOLD_BLOCK));probe.release();
            var type=new mezz.jei.api.recipe.RecipeType<com.pureneko.vjce.data.CategoryDocument.Recipe>(net.minecraft.resources.ResourceLocation.parse("vjce:world_qa"),com.pureneko.vjce.data.CategoryDocument.Recipe.class);
            var category=new DisplayCategory<>(p.document,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,p.selected,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
            return new net.minecraft.client.gui.screens.Screen(Component.literal("World snapshot in JEI")){
                @Override public void tick(){layout.tick();}
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(60,80);layout.drawRecipe(g,0,0);g.drawString(font,"Actual JEI: independent world snapshot, sign data and entity equipment",30,35,0xffffffff,false);}
            };
        }).screenshot("world_actual_jei").closeScreen()
        .openModularUI("4096 block scene",c->{var scene=new BlockSceneElement();scene.blocks=c.get("largeCapture");scene.clock.playing=false;scene.layout(l->{l.width(220);l.height(220);});c.put("largeScene",scene);return new ModularUI(UI.of(new UIElement().layout(l->{l.width(240);l.height(240);}).addChild(scene)),c.player());}).awaitModularUI()
        .check("4096 blocks render without scene error",c->c.<BlockSceneElement>get("largeScene").error.isEmpty()&&c.<BlockSceneElement>get("largeScene").getRenderer()!=null)
        .screenshot("world_4096_blocks").closeScreen()
        .check("closing large scene releases world and renderer",c->c.<BlockSceneElement>get("largeScene").getDummyWorld()==null&&c.<BlockSceneElement>get("largeScene").getRenderer()==null);
    }
    private static void boundaries(TestContext c){
        var level=c.mc().level;var previous=scene(c).blocks.copy();
        for(boolean entities:List.of(false,true)){
            var replacement=WorldSceneCapture.capture(level,WorldSceneCapture.Selection.of(ORIGIN,ORIGIN),entities,previous);
            c.check("new selection with no entities clears old entities, checked="+entities,replacement.getList("entities",Tag.TAG_COMPOUND).isEmpty()&&previous.equals(scene(c).blocks));
        }
        var reverse=WorldSceneCapture.Selection.of(ORIGIN.offset(2,1,2),ORIGIN);var capture=WorldSceneCapture.capture(level,reverse,true,previous);
        c.check("reverse corners normalize to minimum origin",capture.getList("blocks",Tag.TAG_COMPOUND).getCompound(0).getInt("x")==0);
        var large=WorldSceneCapture.capture(level,WorldSceneCapture.Selection.of(LARGE,LARGE.offset(15,15,15)),false,new CompoundTag());c.put("largeCapture",large);
        c.check("4096 block boundary accepted",large.getList("blocks",Tag.TAG_COMPOUND).size()==4096);
        for(var region:List.of(WorldSceneCapture.Selection.of(LARGE,LARGE.offset(16,16,16)),WorldSceneCapture.Selection.of(LARGE,LARGE.offset(32,0,0)),WorldSceneCapture.Selection.of(new BlockPos(20000000,80,0),new BlockPos(20000000,80,0)))){
            boolean rejected=false;try{WorldSceneCapture.capture(level,region,false,previous);}catch(IllegalArgumentException expected){rejected=true;}c.check("invalid selection rejected atomically: "+region,rejected&&previous.equals(scene(c).blocks));
        }
        var temporary=new ArrayList<Entity>();
        try{
            for(int i=0;i<33;i++){var e=EntityType.ARMOR_STAND.create(level);e.moveTo(15.5,80,15.5,0,0);level.addEntity(e);temporary.add(e);}
            boolean rejected=false;try{WorldSceneCapture.capture(level,WorldSceneCapture.Selection.of(new BlockPos(15,80,15),new BlockPos(15,81,15)),true,previous);}catch(IllegalArgumentException expected){rejected=true;}
            c.check("33 entities rejected without truncation",rejected&&previous.equals(scene(c).blocks));
        }finally{temporary.forEach(Entity::discard);}
        var boat=EntityType.BOAT.create(level);boat.moveTo(11.01,81,9,0,0);level.addEntity(boat);
        try{var filtered=WorldSceneCapture.capture(level,reverse,true,previous);c.check("entity reference point governs selection",filtered.getList("entities",Tag.TAG_COMPOUND).size()==1);}finally{boat.discard();}
        var vehicle=EntityType.BOAT.create(level);var rider=EntityType.ZOMBIE.create(level);vehicle.moveTo(8.5,81,8.5,0,0);rider.startRiding(vehicle,true);
        var snapshot=WorldSceneCapture.entity(vehicle,ORIGIN);c.check("riding capture strips hierarchy without mutating source",!snapshot.getCompound("data").contains("Passengers")&&rider.getVehicle()==vehicle&&vehicle.getPassengers().size()==1);
        var armor=EntityType.ARMOR_STAND.create(level);armor.moveTo(8.5,81,8.5,0,0);var armorData=WorldSceneCapture.entity(armor,ORIGIN).getCompound("data");c.check("armor stand body pose is preserved",armorData.contains("Pose"));
    }
    private static Entity sourceEntity(TestContext c){return c.mc().level.entitiesForRendering().iterator().hasNext()?java.util.stream.StreamSupport.stream(c.mc().level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(c.get("worldZombie"))).findFirst().orElse(null):null;}
    private static VjceProject project(TestContext c){return c.get("worldProject");}
    private static BlockSceneElement scene(TestContext c){return c.get("worldScene");}
    private static UIElement root(TestContext c){return project(c).layoutView.getModularUI().ui.rootElement;}
    private static WorldSelectionDialog dialog(TestContext c){return (WorldSelectionDialog)root(c).selectId("vjce_world_dialog").findFirst().orElseThrow();}
    private static SceneEditingView parent(TestContext c){return (SceneEditingView)root(c).selectId("vjce_scene_dialog").findFirst().orElseThrow();}
    private static CompoundTag working(TestContext c){return (CompoundTag)((StructuredTagEditor)root(c).selectId("vjce_scene_data").findFirst().orElseThrow()).getValue();}
    private static void controls(ScenarioBuilder s){
        s.check("MBD2 range defaults to 5 on all axes",c->{var d=dialog(c);return d.preview.framed.getXsize()==11&&d.preview.framed.getYsize()==11&&d.preview.framed.getZsize()==11&&"5".equals(field(c,"range_x").getValue())&&"5".equals(field(c,"range_y").getValue())&&"5".equals(field(c,"range_z").getValue());})
        .step("remember selection before preview range edits",c->{c.put("originalSelection",dialog(c).selection());c.put("wheelX",dialog(c).selection().min().getX());})
        .typeInto("#vjce_world_range_x","2").typeInto("#vjce_world_range_y","0").typeInto("#vjce_world_range_z","3")
        .check("independent XYZ radii update preview without changing selection",c->{var d=dialog(c);return d.preview.framed.getXsize()==5&&d.preview.framed.getYsize()==1&&d.preview.framed.getZsize()==7&&d.selection().equals(c.get("originalSelection"));})
        .step("aim unfocused coordinate field",c->{var p=ElementBounds.of(field(c,"from_x")).center();c.put("wheelPoint",p);c.input().moveTo(p.x,p.y);c.put("wheelFrame",dialog(c).preview.framed);})
        .check("wheel target starts unfocused",c->!field(c,"from_x").isFocused())
        .step("unfocused wheel leaves coordinate alone",c->{var p=c.<Vector2f>get("wheelPoint");c.input().scroll(p.x,p.y,1);})
        .check("hover alone does not change coordinate",c->Integer.parseInt(field(c,"from_x").getValue())==c.<Integer>get("wheelX")&&!field(c,"from_x").isFocused());
        click(s,"focus coordinate",c->field(c,"from_x"));
        s.step("focused wheel increments coordinate",c->{var p=ElementBounds.of(field(c,"from_x")).center();c.put("wheelPoint",p);c.input().scroll(p.x,p.y,1);})
        .check("focused wheel adds one and keeps camera frame",c->Integer.parseInt(field(c,"from_x").getValue())==c.<Integer>get("wheelX")+1&&dialog(c).preview.framed.equals(c.get("wheelFrame"))&&field(c,"from_x").isFocused())
        .keyDown(Keys.LEFT_SHIFT)
        .step("shift wheel increments ten",c->{var p=c.<Vector2f>get("wheelPoint");c.input().scroll(p.x,p.y,1);}).keyUp(Keys.LEFT_SHIFT)
        .check("shift wheel adds ten",c->Integer.parseInt(field(c,"from_x").getValue())==c.<Integer>get("wheelX")+11)
        .step("wheel decrements coordinate",c->{var p=c.<Vector2f>get("wheelPoint");c.input().scroll(p.x,p.y,-1);})
        .check("down wheel subtracts one",c->Integer.parseInt(field(c,"from_x").getValue())==c.<Integer>get("wheelX")+10)
        .typeInto("#vjce_world_range_z","0").typeInto("#vjce_world_range_x","50")
        .step("aim radius upper limit",c->{var p=ElementBounds.of(field(c,"range_x")).center();c.put("wheelPoint",p);c.input().moveTo(p.x,p.y);})
        .step("wheel clamps radius",c->{var p=c.<Vector2f>get("wheelPoint");c.input().scroll(p.x,p.y,1);})
        .check("MBD2 radius clamps to 50 and permits zero other axes",c->"50".equals(field(c,"range_x").getValue())&&dialog(c).preview.framed.getXsize()==101&&dialog(c).preview.framed.getYsize()==1&&dialog(c).preview.framed.getZsize()==1)
        .typeInto("#vjce_world_to_y","319")
        .step("aim world height boundary",c->{var p=ElementBounds.of(field(c,"to_y")).center();c.put("wheelPoint",p);c.input().moveTo(p.x,p.y);})
        .step("wheel clamps world height",c->{var p=c.<Vector2f>get("wheelPoint");c.input().scroll(p.x,p.y,1);})
        .check("coordinate stays inside build height",c->Integer.parseInt(field(c,"to_y").getValue())==c.mc().level.getMaxBuildHeight()-1)
        .step("restore original corner values",c->{var d=dialog(c);var selection=c.<WorldSceneCapture.Selection>get("originalSelection");for(String side:List.of("from","to")){var p=side.equals("from")?selection.min():selection.max();int[] v={p.getX(),p.getY(),p.getZ()};for(int a=0;a<3;a++)field(c,side+"_"+"xyz".charAt(a)).setText(Integer.toString(v[a]),true);}});
    }
    private static com.lowdragmc.lowdraglib2.gui.ui.elements.TextField field(TestContext c,String name){return (com.lowdragmc.lowdraglib2.gui.ui.elements.TextField)root(c).selectId("vjce_world_"+name).findFirst().orElseThrow();}
    private static void coordinates(ScenarioBuilder s,BlockPos a,BlockPos b){for(var side:List.of("from","to")){var p=side.equals("from")?a:b;int[] values={p.getX(),p.getY(),p.getZ()};for(int i=0;i<3;i++)s.typeInto("#vjce_world_"+side+"_"+"xyz".charAt(i),Integer.toString(values[i]));}}
    private static void corner(ScenarioBuilder s,String name,BlockPos block){
        s.step("aim "+name+" world corner",c->{var p=dialog(c).preview.project(new Vector3f(block.getX()+.5f,block.getY()+1.001f,block.getZ()+.5f)).orElseThrow();c.put("corner",p);c.input().moveTo(p.x,p.y);})
        .waitUntil(name+" corner is under cursor",c->dialog(c).preview.scene.getLastHoverPosFace()!=null&&dialog(c).preview.scene.getLastHoverPosFace().pos().equals(block))
        .keyDown(Keys.LEFT_SHIFT)
        .step("press "+name+" corner",c->{var p=c.<Vector2f>get("corner");c.input().mouseDown(p.x,p.y,0);})
        .step("release "+name+" corner",c->{var p=c.<Vector2f>get("corner");c.input().mouseUp(p.x,p.y,0);}).keyUp(Keys.LEFT_SHIFT);
    }
    private static void click(ScenarioBuilder s,String name,Function<TestContext,UIElement> target){
        s.step("aim "+name,c->{var p=ElementBounds.of(target.apply(c)).center();c.put("point",p);c.input().moveTo(p.x,p.y);})
        .step("press "+name,c->{var p=c.<Vector2f>get("point");c.input().mouseDown(p.x,p.y,0);})
        .step("release "+name,c->{var p=c.<Vector2f>get("point");c.input().mouseUp(p.x,p.y,0);});
    }
}
