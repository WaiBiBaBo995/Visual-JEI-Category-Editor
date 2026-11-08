package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import net.minecraft.nbt.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Vector2f;
import java.util.*;

@LDLRegisterClient(name="vjce_scene_rotation",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneRotationScenario implements UIScenario {
    private static final int[] ANGLES={0,30,45,90,135,180};
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("scene rotations",c->{
            var root=new UIElement().layout(l->{l.width(640);l.height(340);}).style(st->st.backgroundTexture(ColorPattern.GRAY.rectTexture()));
            for(int i=0;i<ANGLES.length;i++){
                int x=40+(i%3)*190,y=20+(i/3)*150,angle=ANGLES[i];var scene=fixture();scene.setId("angle_"+angle);place(scene,x,y,120,90);scene.transform(t->t.rotation(angle));root.addChild(scene);
                var label=new com.lowdragmc.lowdraglib2.gui.ui.elements.Label().setText(angle+" deg");place(label,x,y+120,100,12);root.addChild(label);
            }
            c.put("grid",root);return new ModularUI(UI.of(root),c.player());
        }).awaitModularUI().waitUntil("all scene textures allocated",c->gridScenes(c).stream().allMatch(e->e.getRenderer() instanceof SceneRenderer fbo&&fbo.getFbo()!=null))
        .check("rotation preserves logical size and texture resolution",c->{var first=(SceneRenderer)gridScenes(c).getFirst().getRenderer();return gridScenes(c).stream().allMatch(e->Math.abs(e.getSizeWidth()-120)<.01&&Math.abs(e.getSizeHeight()-90)<.01&&((SceneRenderer)e.getRenderer()).getResolutionWidth()==first.getResolutionWidth()&&((SceneRenderer)e.getRenderer()).getResolutionHeight()==first.getResolutionHeight()&&e.error.isEmpty());})
        .step("resizing preserves GUI render target",c->{
            int target=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);int[] viewport=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);boolean depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            ((SceneRenderer)gridScenes(c).getFirst().getRenderer()).setFBOSize(302,222);
            int[] after=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,after);
            c.check("scene resize restores target viewport and depth state",target==org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)&&Arrays.equals(viewport,after)&&depth==org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST));
        })
        .step("save rotation fixtures and pixel metadata",c->{
            var doc=new CategoryDocument();doc.id="vjce:rotation_qa";doc.width=640;doc.height=340;var recipe=new CategoryDocument.Recipe();doc.recipes.add(recipe);c.put("doc",doc);c.put("recipe",recipe);
            doc.layout=UITemplate.CODEC.encodeStart(NbtOps.INSTANCE,c.<UIElement>get("grid").getModularUI().ui.toTemplate()).getOrThrow().toString();
            var captures=new ArrayList<Map<String,Object>>();double scale=c.mc().getWindow().getGuiScale();
            for(var scene:gridScenes(c)){var p=ElementBounds.of(scene).center();captures.add(Map.of("angle",scene.getStyle().transform2D().rotation(),"center",List.of(p.x*scale,p.y*scale),"radius",82*scale));}
            c.put("fboInstances",gridScenes(c).stream().map(e->(SceneRenderer)e.getRenderer()).toList());
            try{java.nio.file.Files.writeString(c.outDir().resolve("scene-rotation-pixels.json"),ProjectStore.JSON.toJson(Map.of("imagePattern","screenshots/vjce_scene_rotation/*_rotation_grid.png","scenes",captures)));}catch(Exception e){throw new IllegalStateException(e);}
        }).screenshot("rotation_grid").closeScreen()
        .check("closing releases every offscreen target",c->c.<List<SceneRenderer>>get("fboInstances").stream().allMatch(r->r.getFbo()==null||r.getFbo().frameBufferId<0))
        .openScreen("rotated scenes in actual JEI",c->{
            var doc=c.<CategoryDocument>get("doc");var recipe=c.<CategoryDocument.Recipe>get("recipe");var type=new mezz.jei.api.recipe.RecipeType<CategoryDocument.Recipe>(net.minecraft.resources.ResourceLocation.parse("vjce:rotation_qa"),CategoryDocument.Recipe.class);
            var category=new DisplayCategory<>(doc,type,r->r);var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());var layout=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,recipe,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$createExtras();
            return new Screen(net.minecraft.network.chat.Component.literal("Rotated scenes in JEI")){
                @Override public void tick(){layout.tick();}
                @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);layout.setPosition(40,60);layout.drawRecipe(g,0,0);g.drawString(font,"Actual JEI: 0 / 30 / 45 / 90 / 135 / 180 degrees",40,30,0xffffffff,false);}
            };
        }).screenshot("rotation_actual_jei").closeScreen();
        s.openModularUI("clipped translucent scene",c->{
            var root=new UIElement().layout(l->{l.width(300);l.height(180);});
            var panel=new UIElement().layout(l->{l.width(180);l.height(130);}).style(st->st.opacity(.65f));panel.setOverflowVisible(false);
            var scene=fixture();place(scene,100,15,120,90);scene.transform(t->t.rotation(45));panel.addChild(scene);root.addChild(panel);c.put("clipped",scene);
            root.addChild(new com.lowdragmc.lowdraglib2.gui.ui.elements.Label().setText("GUI after nested scene"));return new ModularUI(UI.of(root),c.player());
        }).awaitModularUI().check("nested clipping and opacity do not break rendering",c->c.<BlockSceneElement>get("clipped").error.isEmpty()&&c.<BlockSceneElement>get("clipped").getRenderer() instanceof SceneRenderer r&&r.getFbo()!=null)
        .step("save nested clip pixel metadata",c->{
            var panel=c.<BlockSceneElement>get("clipped").getParent();double scale=c.mc().getWindow().getGuiScale();
            var bounds=List.of(panel.getPositionX()*scale,panel.getPositionY()*scale,(panel.getPositionX()+panel.getSizeWidth())*scale,(panel.getPositionY()+panel.getSizeHeight())*scale);
            try{java.nio.file.Files.writeString(c.outDir().resolve("nested-scene-pixels.json"),ProjectStore.JSON.toJson(Map.of("bounds",bounds,"imagePattern","screenshots/vjce_scene_rotation/*_rotation_clipped_opacity.png")));}catch(Exception e){throw new IllegalStateException(e);}
        }).screenshot("rotation_clipped_opacity")
        .check("visual layer sampler restored after scene",c->((com.pureneko.vjce.mixin.ShaderSamplerAccess)com.lowdragmc.lowdraglib2.client.shader.LDLibShaders.getVisualLayerShader()).vjce$samplers().get("DiffuseSampler")==null).closeScreen();
        gesture(s);
    }
    private static BlockSceneElement fixture(){
        var scene=new BlockSceneElement();scene.clock.playing=false;scene.autoFrame=false;scene.cameraRange=2.1f;
        try{scene.blocks=TagParser.parseTag("{blocks:[{x:0,y:0,z:0,state:{Name:\"minecraft:gold_block\"}},{x:1,y:0,z:0,state:{Name:\"minecraft:redstone_block\"}},{x:0,y:1,z:0,state:{Name:\"minecraft:lapis_block\"}}]}");}catch(Exception e){throw new IllegalStateException(e);}return scene;
    }
    private static void place(UIElement e,int x,int y,int w,int h){e.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(x);l.top(y);l.width(w);l.height(h);});}
    private static List<BlockSceneElement> gridScenes(TestContext c){return c.<UIElement>get("grid").select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).toList();}
    private static RecipeLayoutView view(TestContext c){return c.<VjceProject>get("p").layoutView;}
    private static BlockSceneElement element(TestContext c){return view(c).getCurrentUI().rootElement.selectId("gesture_scene",BlockSceneElement.class).findFirst().orElseThrow();}
    private static void gesture(ScenarioBuilder s){
        s.openModularUI("scene rotation gesture",c->{
            var p=new VjceProject(VjceSmokeScenario.example());p.document.width=240;p.document.height=150;var w=new EditorWindow(VjceEditor::new);w.getCurrentEditor().loadProject(p,null);c.put("p",p);
            var scene=fixture();scene.setId("gesture_scene");place(scene,55,40,100,70);p.layoutView.getCurrentUI().rootElement.addChild(scene);p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());return new ModularUI(UI.of(w),c.player());
        }).awaitModularUI().keyDown(Keys.LEFT_CONTROL)
        .step("aim scene",c->{var e=element(c);var p=e.getWorldMouse(e.getPositionX()+e.getSizeWidth()*.8f,e.getPositionY()+e.getSizeHeight()*.5f);c.put("start",p);c.input().moveTo(p.x,p.y);})
        .step("Ctrl click scene",c->{var p=c.<Vector2f>get("start");c.input().mouseDown(p.x,p.y,0);})
        .step("release Ctrl click",c->{var p=c.<Vector2f>get("start");c.input().mouseUp(p.x,p.y,0);}).keyUp(Keys.LEFT_CONTROL).keyDown(Keys.LEFT_ALT)
        .step("remember quarter-turn target",c->{var e=element(c);c.put("target",e.getWorldMouse(e.getPositionX()+e.getSizeWidth()*.5f,e.getPositionY()+e.getSizeHeight()*.5f+e.getSizeWidth()*.3f));var p=c.<Vector2f>get("start");c.input().mouseDown(p.x,p.y,0);})
        .step("rotate halfway",c->{var p=new Vector2f(c.<Vector2f>get("start")).lerp(c.<Vector2f>get("target"),.5f);c.input().dragTo(p.x,p.y,0);})
        .step("rotate quarter turn",c->{var p=c.<Vector2f>get("target");c.input().dragTo(p.x,p.y,0);})
        .step("release rotation",c->{var p=c.<Vector2f>get("target");c.input().mouseUp(p.x,p.y,0);}).keyUp(Keys.LEFT_ALT)
        .check("Alt rotates scene without resizing",c->Math.abs(element(c).getStyle().transform2D().rotation()-90)<.5&&Math.abs(element(c).getSizeWidth()-100)<.01&&Math.abs(element(c).getSizeHeight()-70)<.01)
        .screenshot("scene_alt_rotation")
        .step("undo scene rotation",c->view(c).historyStack.undo()).check("rotation undo restores angle",c->Math.abs(element(c).getStyle().transform2D().rotation())<.01)
        .step("redo scene rotation",c->view(c).historyStack.redo()).check("rotation redo restores angle",c->Math.abs(element(c).getStyle().transform2D().rotation()-90)<.5)
        .step("grouped scale and rotation",c->{var e=element(c);var root=view(c).getCurrentUI().rootElement;e.removeSelf();var group=new UIElement().setId("scene_parent");place(group,20,10,180,120);group.transform(t->t.rotation(20).scale(1.3f,.8f));root.addChild(group);group.addChild(e);view(c).hierarchy.loadUI(view(c).getCurrentUI());view(c).graphView.setScale(1.25f);view(c).graphView.setOffsetX(10);view(c).graphView.setOffsetY(-5);})
        .check("parent rotation and nonuniform scale retain scene size",c->element(c).error.isEmpty()&&Math.abs(element(c).getSizeWidth()-100)<.01&&Math.abs(element(c).getSizeHeight()-70)<.01)
        .screenshot("scene_parent_transform").closeScreen();
    }
}
