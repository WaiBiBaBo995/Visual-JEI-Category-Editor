package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.ProjectStore;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import com.pureneko.vjce.mixin.ShaderLightsAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.joml.Vector3f;

@LDLRegisterClient(name="vjce_lighting",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneLightingScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s){
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("Create lighting comparison",c->{
            boolean create=net.neoforged.fml.ModList.get().isLoaded("create");c.put("lightingCreate",create);
            var root=new UIElement().layout(l->{l.width(640);l.height(300);}).style(st->st.backgroundTexture(ColorPattern.GRAY.rectTexture()));
            var scene=new BlockSceneElement();scene.autoFrame=false;scene.cameraRange=1.5f;scene.cameraYaw=-135;scene.cameraPitch=25;scene.clock.playing=false;
            try{scene.blocks=TagParser.parseTag("{blocks:[{x:0,y:0,z:0,state:{Name:'minecraft:diamond_block'}}]}");}catch(Exception e){throw new IllegalStateException(e);}
            place(scene,40,40,160,160);root.addChild(scene);c.put("lightingScene",scene);
            var reference=new UIElement(){@Override public void drawBackgroundAdditional(GUIContext context){
                if(create)renderCreate(context,getContentX()+getContentWidth()/2,getContentY()+getContentHeight()/2);
            }};place(reference,320,40,160,160);root.addChild(reference);c.put("lightingReference",reference);
            var a=new com.lowdragmc.lowdraglib2.gui.ui.elements.Label().setText("VJCE world camera");place(a,40,220,200,12);root.addChild(a);
            var b=new com.lowdragmc.lowdraglib2.gui.ui.elements.Label().setText(create?"Actual Create GuiGameElement":"Create absent: native renderer is independent");place(b,320,220,300,12);root.addChild(b);
            return new ModularUI(UI.of(root),c.player());
        }).awaitModularUI()
        .check("new model path renders without requiring Create",c->c.<BlockSceneElement>get("lightingScene").error.isEmpty()&&c.<BlockSceneElement>get("lightingScene").getRenderer()!=null)
        .step("draw restores caller custom lighting",c->{
            var scene=c.<BlockSceneElement>get("lightingScene");var r=(SceneRenderer)scene.getRenderer();var old=ShaderLightsAccess.vjce$directions();var a=old[0];var b=old[1];
            var custom0=new Vector3f(.2f,.3f,.9f).normalize();var custom1=new Vector3f(-.5f,.7f,.4f).normalize();
            try{RenderSystem.setShaderLights(custom0,custom1);r.drawScene(0,0,160,160,0,0);var after=ShaderLightsAccess.vjce$directions();c.check("shader lights restored exactly",after[0].equals(custom0,.00001f)&&after[1].equals(custom1,.00001f));}
            finally{RenderSystem.setShaderLights(a,b);}
        })
        .step("export lighting comparison coordinates",c->{
            var scene=ElementBounds.of(c.<BlockSceneElement>get("lightingScene")).center();var reference=ElementBounds.of(c.<UIElement>get("lightingReference")).center();double scale=c.mc().getWindow().getGuiScale();
            try{java.nio.file.Files.writeString(c.outDir().resolve("scene-lighting-pixels.json"),ProjectStore.JSON.toJson(Map.of("create",c.<Boolean>get("lightingCreate"),"imagePattern","screenshots/vjce_lighting/*_create_lighting_comparison.png","vjce",List.of(scene.x*scale,scene.y*scale),"reference",List.of(reference.x*scale,reference.y*scale),"radius",70*scale)));}catch(Exception e){throw new IllegalStateException(e);}
        }).screenshot("create_lighting_comparison").closeScreen();
    }
    private static void place(UIElement e,int x,int y,int w,int h){e.layout(l->{l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);l.left(x);l.top(y);l.width(w);l.height(h);});}
    private static void renderCreate(GUIContext context,float x,float y){
        try{
            Class<?> element=Class.forName("net.createmod.catnip.gui.element.GuiGameElement");Object builder=element.getMethod("of",net.minecraft.world.level.block.state.BlockState.class).invoke(null,Blocks.DIAMOND_BLOCK.defaultBlockState());Class<?> type=builder.getClass();
            Object lights=Class.forName("com.simibubi.create.compat.jei.category.animations.AnimatedKinetics").getField("DEFAULT_LIGHTING").get(null);
            type.getMethod("lighting",Class.forName("net.createmod.catnip.gui.ILightingSettings")).invoke(builder,lights);
            type.getMethod("at",float.class,float.class,float.class).invoke(builder,x,y,100f);
            type.getMethod("atLocal",double.class,double.class,double.class).invoke(builder,-.5,.5,-.5);
            type.getMethod("withRotationOffset",Vec3.class).invoke(builder,new Vec3(.5,.5,.5));
            type.getMethod("rotate",double.class,double.class,double.class).invoke(builder,25d,135d,0d);
            type.getMethod("scale",double.class).invoke(builder,160d/3);
            type.getMethod("render",net.minecraft.client.gui.GuiGraphics.class).invoke(builder,context.graphics);
        }catch(Exception e){throw new IllegalStateException("Create reference rendering failed",e);}
    }
}
