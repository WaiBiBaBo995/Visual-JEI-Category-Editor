package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import com.pureneko.vjce.mixin.ShaderLightsAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Regression for the real 0.8.0 Mekanism empty-fluid crash, plus third-party failure isolation. */
@LDLRegisterClient(name="vjce_world_render",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class WorldRenderGuardScenario implements UIScenario {
    private static final BlockPos ORIGIN = new BlockPos(12,85,12);
    private static final ResourceLocation THERMAL = ResourceLocation.parse("mekanism:thermal_evaporation_controller");
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null)
        .server("prepare native block entity renderers",sc->{
            sc.player().getAbilities().flying=true;sc.player().onUpdateAbilities();sc.player().connection.teleport(12.5,88,18.5,0,0);
            for(int x=0;x<4;x++)sc.level().setBlockAndUpdate(ORIGIN.offset(x,-1,0),Blocks.STONE.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN,Blocks.OAK_SIGN.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN.offset(1,0,0),Blocks.OAK_SIGN.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN.offset(2,0,0),BuiltInRegistries.BLOCK.containsKey(THERMAL)?BuiltInRegistries.BLOCK.get(THERMAL).defaultBlockState():Blocks.ENCHANTING_TABLE.defaultBlockState());
        }).waitUntil("native block entities synchronized",c->c.mc().level.getBlockEntity(ORIGIN)!=null&&c.mc().level.getBlockEntity(ORIGIN.offset(1,0,0))!=null&&c.mc().level.getBlockEntity(ORIGIN.offset(2,0,0))!=null)
        .step("verify actual Mekanism crash precondition",WorldRenderGuardScenario::nativeFailure)
        .openModularUI("world selection renderer regression",c->new ModularUI(UI.of(new UIElement().layout(l->{l.width(700);l.height(420);})),c.player())).awaitModularUI()
        .step("open actual world selection dialog",c->{
            var parent=new Dialog().setTitle("VJCE renderer regression");parent.show(((com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen)c.mc().screen).getModularUI());
            c.put("renderParent",parent);WorldSelectionDialog.open(parent,CompoundTag::new,data->c.put("renderImported",data));
            dialog(c).preview.frame(ORIGIN.offset(-1,-1,-1),ORIGIN.offset(3,2,1));
        }).check("cached world preview accepts native ineligible renderer",c->SceneRenderGuard.get(dialog(c).preview.scene.getRenderer()).failureCount()==0)
        .step("cached and uncached native failure isolation",WorldRenderGuardScenario::isolation)
        .check("failure warning stays inside dialog",c->{var warning=dialog(c).selectId("vjce_world_render_warning").findFirst().orElseThrow();var a=ElementBounds.of(warning);var b=ElementBounds.of(dialog(c).contentContainer);return warning.isDisplayed()&&a.x()>=b.x()-1&&a.x()+a.width()<=b.x()+b.width()+1;})
        .screenshot("world_render_guard_warning")
        .step("preview failure does not prevent import",c->{
            var imported=WorldSceneCapture.capture(c.mc().level,WorldSceneCapture.Selection.of(ORIGIN,ORIGIN.offset(2,0,0)),false,new CompoundTag());
            c.check("all blocks including skipped renderer are captured",imported.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND).size()==3);
            c.put("renderCapture",imported);
            var p=ElementBounds.of(dialog(c).selectId("vjce_world_cancel").findFirst().orElseThrow()).center();c.put("renderClick",p);c.input().moveTo(p.x,p.y);
        }).step("press cancel after renderer failure",c->{var p=c.<org.joml.Vector2f>get("renderClick");c.input().mouseDown(p.x,p.y,0);})
        .step("release cancel after renderer failure",c->{var p=c.<org.joml.Vector2f>get("renderClick");c.input().mouseUp(p.x,p.y,0);})
        .check("dialog buttons remain usable",c->c.<Dialog>get("renderParent").getModularUI().ui.rootElement.selectId("vjce_world_dialog").findAny().isEmpty())
        .closeScreen()
        .openModularUI("captured Mekanism scene",c->{
            var scene=new BlockSceneElement();scene.blocks=c.get("renderCapture");scene.clock.playing=false;scene.layout(l->{l.width(220);l.height(160);});c.put("renderRecipeScene",scene);
            return new ModularUI(UI.of(new UIElement().layout(l->{l.width(240);l.height(180);}).addChild(scene)),c.player());
        }).awaitModularUI()
        .check("recipe scene accepts actual empty multiblock",c->{var scene=c.<BlockSceneElement>get("renderRecipeScene");return scene.error.isEmpty()&&SceneRenderGuard.get(scene.getRenderer()).failureCount()==0;})
        .step("recipe scene isolates throwing block entity renderer",WorldRenderGuardScenario::recipeIsolation)
        .screenshot("empty_multiblock_recipe_scene").closeScreen();
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void recipeIsolation(TestContext c) {
        var dispatcher=c.mc().getBlockEntityRenderDispatcher();
        try {
            var field=dispatcher.getClass().getDeclaredField("renderers");field.setAccessible(true);
            var original=(Map<BlockEntityType<?>,BlockEntityRenderer<?>>)field.get(dispatcher);
            var replacements=new HashMap<>(original);var probe=new ThrowingRenderer((BlockEntityRenderer)(original.get(BlockEntityType.SIGN)));
            replacements.put(BlockEntityType.SIGN,probe);field.set(dispatcher,replacements);
            try {
                var scene=c.<BlockSceneElement>get("renderRecipeScene");var guard=SceneRenderGuard.get(scene.getRenderer());guard.retry();
                var graphics=new GuiGraphics(c.mc(),c.mc().renderBuffers().bufferSource());graphics.flush();
                var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var model=new org.joml.Matrix4f(RenderSystem.getModelViewStack());
                int target=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
                for(int i=0;i<3;i++)scene.drawBackgroundAdditional(GUIContext.of(scene.getModularUI(),graphics,0,0,0));
                c.check("recipe FBO keeps healthy renderers and isolates failure",scene.error.isEmpty()&&guard.failureCount()==1&&probe.failures==1&&probe.goodCalls>=3&&probe.restored);
                c.check("recipe FBO restores target and camera after BE exception",org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING)==target&&RenderSystem.getProjectionMatrix().equals(projection,.0001f)&&RenderSystem.getModelViewStack().equals(model,.0001f));
            } finally {field.set(dispatcher,original);}
        } catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    @SuppressWarnings("unchecked") private static void nativeFailure(TestContext c) {
        if(!BuiltInRegistries.BLOCK.containsKey(THERMAL))return;
        var tile=c.mc().level.getBlockEntity(ORIGIN.offset(2,0,0));
        var renderer=(BlockEntityRenderer<BlockEntity>)c.mc().getBlockEntityRenderDispatcher().getRenderer(tile);
        c.check("Mekanism native shouldRender rejects empty unformed tower",!renderer.shouldRender(tile,Vec3.atCenterOf(tile.getBlockPos())));
        var before=tile.saveWithoutMetadata(c.mc().level.registryAccess());boolean reproduced=false;
        var buffers=c.mc().renderBuffers().bufferSource();buffers.endBatch();
        try(var state=new SceneRenderState()) {
            try{renderer.render(tile,0,new PoseStack(),buffers,0xf000f0,0);}
            catch(IllegalArgumentException expected){reproduced=expected.getMessage().contains("Fluid may not be empty");}
            finally{state.restoreTarget();buffers.endBatch();}
        }
        c.check("original direct render reproduces exact reported fluid exception",reproduced);
        c.check("rendering does not change real multiblock NBT",before.equals(tile.saveWithoutMetadata(c.mc().level.registryAccess())));
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void isolation(TestContext c) {
        var dispatcher=c.mc().getBlockEntityRenderDispatcher();
        try {
            var field=dispatcher.getClass().getDeclaredField("renderers");field.setAccessible(true);
            var original=(Map<BlockEntityType<?>,BlockEntityRenderer<?>>)field.get(dispatcher);
            var replacements=new HashMap<>(original);
            var delegate=(BlockEntityRenderer<BlockEntity>)(BlockEntityRenderer)original.get(BlockEntityType.SIGN);
            var probe=new ThrowingRenderer(delegate);replacements.put(BlockEntityType.SIGN,probe);field.set(dispatcher,replacements);
            try {
                var preview=dialog(c).preview;var renderer=preview.scene.getRenderer();var guard=SceneRenderGuard.get(renderer);
                for(boolean cached:List.of(true,false)) {
                    preview.scene.useCacheBuffer(cached);preview.frame(ORIGIN,ORIGIN.offset(2,0,0));guard.retry();probe.reset();
                    c.mc().renderBuffers().bufferSource().endBatch();
                    for(int i=0;i<3;i++)renderer.render(new PoseStack(),0,0,180,120,0,0);
                    c.check("one failing renderer quarantined, cached="+cached,guard.failureCount()==1&&probe.failures==1&&probe.goodCalls>=3);
                    c.check("pose, shader color and lights isolated, cached="+cached,probe.restored);
                    preview.frame(ORIGIN,ORIGIN.offset(2,0,0));probe.reset();renderer.render(new PoseStack(),0,0,180,120,0,0);
                    c.check("reframing retries failed renderers, cached="+cached,probe.failures==1&&guard.failureCount()==1);
                }
                var unrelated=new com.lowdragmc.lowdraglib2.client.scene.ImmediateWorldSceneRenderer(c.mc().level);
                try{c.check("non-VJCE LDLib renderers are not opted in",SceneRenderGuard.get(unrelated)==null);}finally{unrelated.releaseResource();}
            } finally { field.set(dispatcher,original); }
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static WorldSelectionDialog dialog(TestContext c){return c.<Dialog>get("renderParent").getModularUI().ui.rootElement.selectId("vjce_world_dialog",WorldSelectionDialog.class).findFirst().orElseThrow();}
    private static final class ThrowingRenderer implements BlockEntityRenderer<BlockEntity> {
        private final BlockEntityRenderer<BlockEntity> delegate;
        private int failures,goodCalls;
        private boolean restored;
        private float[] color;
        private Vector3f light;
        ThrowingRenderer(BlockEntityRenderer<BlockEntity> delegate){this.delegate=delegate;reset();}
        void reset(){failures=goodCalls=0;restored=true;}
        @Override public boolean shouldRender(BlockEntity tile,Vec3 camera){return true;}
        @Override public void render(BlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
            if(failures==0){
                failures++;color=RenderSystem.getShaderColor().clone();this.light=new Vector3f(ShaderLightsAccess.vjce$directions()[0]);
                pose.pushPose();pose.translate(100000,100000,0);
                RenderSystem.setShaderColor(.13f,.27f,.39f,.5f);RenderSystem.setShaderLights(new Vector3f(1,0,0),new Vector3f(0,0,1));
                throw new IllegalStateException("intentional VJCE block entity renderer regression");
            }
            goodCalls++;restored&=Math.abs(pose.last().pose().m30())<10000&&Arrays.equals(color,RenderSystem.getShaderColor())&&this.light.equals(ShaderLightsAccess.vjce$directions()[0],.00001f);
            delegate.render(tile,partial,pose,buffers,light,overlay);
        }
    }
}
