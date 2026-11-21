package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import java.util.*;

@LDLRegisterClient(name="vjce_entities", group="vjce", registry=UIScenario.REGISTRY, environment=RegistrationEnvironment.DEV_ONLY)
public final class EntitySceneScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready", c -> VjceJeiPlugin.runtime != null)
                .openModularUI("entity scene editor", c -> {
                    var doc = VjceSmokeScenario.example(); doc.width = 400; doc.height = 240;
                    var project = new VjceProject(doc); var window = new EditorWindow(VjceEditor::new);
                    window.getCurrentEditor().loadProject(project, null); c.put("p", project);
                    return new ModularUI(UI.of(window), c.player());
                }).awaitModularUI();
        s.step("prepare entity animation fixture",c->p(c).layoutView.addScene(BlockSceneElement.entityScene()));
        s.check("entity fixture is an entity-only paused scene", c -> {
            var scene = scene(c); return scene != null && BlockSceneElement.rows(scene.blocks,"blocks").isEmpty()
                    && BlockSceneElement.rows(scene.blocks,"entities").size() == 1 && !scene.clock.playing;
        }).waitUntil("entity renderer ready", c -> scene(c).getRenderer() != null)
                .check("entity belongs to an independent world", c -> scene(c).sceneEntities().getFirst().level() != Minecraft.getInstance().level)
                .step("native idle ticks remain anchored and can pause", c -> {
                    var scene = scene(c); var entity = scene.sceneEntities().getFirst(); var position = entity.position(); int before = entity.tickCount;
                    scene.clock.playing = true; for (int i=0;i<5;i++) scene.screenTick();
                    c.check("native entity ticks advance", entity.tickCount == before+5);
                    c.check("AI gravity and movement disabled", entity.isNoGravity() && entity.noPhysics && ((Mob)entity).isNoAi() && entity.position().distanceTo(position)<.0001 && entity.getDeltaMovement().lengthSqr()==0);
                    scene.clock.playing=false; int paused=entity.tickCount; scene.screenTick(); c.check("pause freezes native idle tick",entity.tickCount==paused);
                    scene.clock.playing=true;scene.animateEntities=false;scene.screenTick();c.check("entity animation toggle is independent",entity.tickCount==paused);scene.animateEntities=true;scene.clock.playing=false;
                }).step("observe paused renderer time",c->{
                    var samples=new ArrayList<Float>();c.put("partialSamples",samples);
                    scene(c).<com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer>getRenderer().setSceneEntityRenderHook(new com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook(){
                        @Override public void applyEntity(net.minecraft.world.level.Level world,Entity entity,com.mojang.blaze3d.vertex.PoseStack pose,float partial){samples.add(partial);}
                    });
                }).waitUntil("paused scene rendered repeatedly",c->c.<List<Float>>get("partialSamples").size()>=5)
                .check("pause freezes entity render interpolation",c->c.<List<Float>>get("partialSamples").stream().distinct().count()==1)
                .screenshot("entity_only_scene");
        s.step("install mixed scene with living and nonliving entities", c -> {
            var scene = scene(c);
            try { scene.blocks = TagParser.parseCompoundFully("{blocks:[{x:0,y:-1,z:0,state:{Name:\"minecraft:gold_block\"}}],entities:[{id:\"minecraft:pig\",x:-1.5d,y:0d,z:0d,yaw:25f,scale:1f,pose:\"STANDING\",data:{}},{id:\"minecraft:zombie\",x:0.5d,y:0d,z:0d,yaw:-25f,scale:1f,pose:\"CROUCHING\",data:{ArmorItems:[{},{},{},{id:\"minecraft:diamond_helmet\",count:1}],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}},{id:\"minecraft:oak_boat\",x:2.5d,y:0d,z:0d,yaw:10f,scale:0.8f,data:{}},{id:\"minecraft:item_display\",x:4d,y:0.5d,z:0d,scale:1.5f,data:{item:{id:\"minecraft:stone\",count:1}}}]}"); }
            catch(Exception failure) { throw new IllegalStateException(failure); }
            scene.layout(l->{l.left(15);l.top(25);l.width(360);l.height(200);});scene.ensureScene();
            c.check("mixed scene created",scene.error.isEmpty() && scene.sceneEntities().size()==4);
            var zombie=(LivingEntity)scene.sceneEntities().get(1);
            c.check("equipment NBT loaded",zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)&&zombie.getMainHandItem().is(Items.IRON_SWORD));
            c.check("pose loaded",zombie.getPose()==Pose.CROUCHING);
            p(c).layoutView.hierarchy.loadUI(p(c).layoutView.getCurrentUI());
        }).screenshot("mixed_entities_editor")
                .step("open entity scene settings",c->SceneEditor.open(p(c).layoutView,scene(c)))
                .check("global entity resources and instance pose controls available",c->p(c).getResources().resources.contains(SceneAssetResource.ENTITIES)&&root(c).selectId("vjce_entity_pose").findAny().isPresent())
                .typeInto("#vjce_entity_x","-1.25")
                .check("controls edit working copy only",c->Math.abs(((CompoundTag)((StructuredTagEditor)root(c).selectId("vjce_scene_data").findFirst().orElseThrow()).getValue()).getListOrEmpty("entities").getCompoundOrEmpty(0).getDoubleOr("x", 0)+1.25)<.001 && Math.abs(scene(c).sceneEntities().getFirst().getX()+1.5)<.001)
                .screenshot("entity_settings");
        click(s,"apply entity settings",c->root(c).selectId("vjce_scene_dialog",SceneEditingView.class).findFirst().orElseThrow().buttonContainer.getChildren().getFirst());
        s.check("applying updates entity placement",c->Math.abs(scene(c).sceneEntities().getFirst().getX()+1.25)<.001)
                .step("template round trip and independent entity identities",c->{
                    p(c).captureLayout();var restored=RecipeUI.savedTemplate(p(c).document).createUI();
                    var copy=restored.rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElseThrow();copy.ensureScene();
                    c.check("mixed data retained",copy.blocks.equals(scene(c).blocks)&&copy.sceneEntities().size()==4);
                    c.check("entity instances and UUIDs are unique",copy.sceneEntities().getFirst()!=scene(c).sceneEntities().getFirst()&&!copy.sceneEntities().getFirst().getUUID().equals(scene(c).sceneEntities().getFirst().getUUID()));copy.release();
                }).step("start mixed scene preview",c->p(c).layoutView.startSimulation())
                .screenshot("mixed_entities_preview")
                .step("reset recreates entities",c->{var copy=p(c).layoutView.canvas.getCanvasModularUI().ui.rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElseThrow();var old=copy.sceneEntities().getFirst();SceneEditor.reset(copy);copy.ensureScene();c.check("reset starts fresh entity state",copy.sceneEntities().getFirst()!=old&&copy.sceneEntities().getFirst().tickCount==0);p(c).layoutView.stopSimulation();})
                .step("unsupported configurations show explicit errors",c->{
                    for(String data:List.of("{blocks:[],entities:[{id:\"missing:entity\"}]}","{blocks:[],entities:[{id:\"minecraft:player\"}]}","{blocks:[],entities:[{id:\"minecraft:pig\",data:{Passengers:[]}}]}")) {
                        var probe=new BlockSceneElement();try{probe.blocks=TagParser.parseCompoundFully(data);}catch(Exception e){throw new IllegalStateException(e);}probe.ensureScene();c.check("unsupported entity isolated: "+data,!probe.error.isEmpty()&&probe.getDummyWorld()==null);probe.release();
                    }
                }).step("render failure restores framebuffer projection and dispatcher",c->failureRecovery(c))
                .openScreen("mixed entities in actual JEI",c->actual(c)).screenshot("mixed_entities_actual_jei").closeScreen();
        s.openModularUI("optional mod entity",c->{
            var scene=BlockSceneElement.entityScene();scene.setId("mod_entity");c.put("modScene",scene);
            if(com.lowdragmc.lowdraglib2.Platform.isModLoaded("mekanism")){var rows=new ListTag();rows.add(SceneEntities.defaults("mekanism:robit"));scene.blocks.put("entities",rows);}
            scene.layout(l->{l.width(180);l.height(180);});return new ModularUI(UI.of(new UIElement().layout(l->{l.width(200);l.height(200);}).addChild(scene)),c.player());
        }).awaitModularUI().check("available mod renderer can use the same scene path",c->c.<BlockSceneElement>get("modScene").error.isEmpty()&&c.<BlockSceneElement>get("modScene").sceneEntities().size()==1)
                .screenshot("optional_mod_entity").closeScreen();
    }
    private static VjceProject p(TestContext c){return c.get("p");}
    private static UIElement root(TestContext c){return p(c).layoutView.getModularUI().ui.rootElement;}
    private static BlockSceneElement scene(TestContext c){return p(c).layoutView.getCurrentUI().rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElse(null);}
    private static void click(ScenarioBuilder s,String name,java.util.function.Function<TestContext,UIElement> target){
        s.step("aim "+name,c->{var p=ElementBounds.of(target.apply(c)).center();c.put("click",p);c.input().moveTo(p.x,p.y);})
                .step("press "+name,c->{var p=c.<Vector2f>get("click");c.input().mouseDown(p.x,p.y,0);})
                .step("release "+name,c->{var p=c.<Vector2f>get("click");c.input().mouseUp(p.x,p.y,0);});
    }
    private static Screen actual(TestContext c){
        var p=p(c);p.captureLayout();var type=mezz.jei.api.recipe.types.IRecipeType.create(net.minecraft.resources.Identifier.parse("vjce:entity_qa"),CategoryDocument.Recipe.class);
        var category=new DisplayCategory<>(p.document,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,p.selected,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
        return new Screen(net.minecraft.network.chat.Component.literal("Entity scene in JEI")){
            @Override public void tick(){layout.tick();}
            @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(60,80);layout.drawRecipe(g,0,0);g.text(font,"Actual JEI: blocks + entities + equipment + pose",30,35,0xffffffff,false);}
        };
    }
    private static void failureRecovery(TestContext c) {
        var probe = BlockSceneElement.entityScene();
        probe.ensureScene();
        var renderer = (VjceSceneRenderer) probe.getRenderer();
        renderer.setSceneEntityRenderHook(new com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook() {
            @Override
            public void applyEntity(net.minecraft.world.level.Level world, Entity entity, com.mojang.blaze3d.vertex.PoseStack pose, float partial) {
                throw new IllegalStateException("intentional entity renderer failure");
            }
        });
        var dispatcher = c.mc().getEntityRenderDispatcher();
        var oldCamera = dispatcher.camera;
        var oldTarget = dispatcher.crosshairPickEntity;
        var projection = RenderSystem.getProjectionMatrixBuffer();
        var model = new Matrix4f(RenderSystem.getModelViewStack());
        try {
            renderer.drawScene(0, 0, 160, 120, 80, 60);
            c.check("scene fails without affecting the rest of the GUI", probe.error.contains("intentional entity renderer failure"));
            c.check("scene restores projection and model-view", RenderSystem.getProjectionMatrixBuffer() == projection
                    && RenderSystem.getModelViewStack().equals(model, .0001f));
            c.check("scene restores entity dispatcher", dispatcher.camera == oldCamera && dispatcher.crosshairPickEntity == oldTarget);
        } finally {
            probe.release();
        }
    }
}
