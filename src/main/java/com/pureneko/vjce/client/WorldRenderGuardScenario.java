package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Regression for the real 0.8.0 Mekanism empty-fluid crash, plus third-party failure isolation. */
@LDLRegisterClient(name="vjce_world_render",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class WorldRenderGuardScenario implements UIScenario {
    private static final BlockPos ORIGIN = new BlockPos(12,85,12);
    private static final Identifier THERMAL = Identifier.parse("mekanism:thermal_evaporation_controller");
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null)
        .server("prepare native block entity renderers",sc->{
            sc.player().getAbilities().flying=true;sc.player().onUpdateAbilities();sc.player().connection.teleport(12.5,88,18.5,0,0);
            for(int x=0;x<4;x++)sc.level().setBlockAndUpdate(ORIGIN.offset(x,-1,0),Blocks.STONE.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN,Blocks.OAK_SIGN.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN.offset(1,0,0),Blocks.OAK_SIGN.defaultBlockState());
            sc.level().setBlockAndUpdate(ORIGIN.offset(2,0,0),BuiltInRegistries.BLOCK.containsKey(THERMAL)?BuiltInRegistries.BLOCK.getValue(THERMAL).defaultBlockState():Blocks.ENCHANTING_TABLE.defaultBlockState());
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
            c.check("all blocks including skipped renderer are captured",imported.getListOrEmpty("blocks").size()==3);
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
            var original=(Map<BlockEntityType<?>,BlockEntityRenderer<?, ?>>)field.get(dispatcher);
            var replacements=new HashMap<>(original);var probe=new ThrowingRenderer((BlockEntityRenderer)(original.get(BlockEntityType.SIGN)));
            replacements.put(BlockEntityType.SIGN,probe);field.set(dispatcher,replacements);
            try {
                var scene=c.<BlockSceneElement>get("renderRecipeScene");var guard=SceneRenderGuard.get(scene.getRenderer());guard.retry();
                var renderer = (VjceSceneRenderer) scene.getRenderer();
                var projection = RenderSystem.getProjectionMatrixBuffer();
                var model = new org.joml.Matrix4f(RenderSystem.getModelViewStack());
                for (int i = 0; i < 3; i++) renderer.drawScene(0, 0, 180, 120, 90, 60);
                c.check("recipe scene quarantines the failed render-state extractor", scene.error.isEmpty()
                        && guard.failureCount() == 1 && probe.failures == 1 && probe.goodCalls >= 3);
                c.check("recipe scene restores camera after extraction error",
                        RenderSystem.getProjectionMatrixBuffer() == projection && RenderSystem.getModelViewStack().equals(model, .0001f));
            } finally {field.set(dispatcher,original);}
        } catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    @SuppressWarnings("unchecked") private static void nativeFailure(TestContext c) {
        if(!BuiltInRegistries.BLOCK.containsKey(THERMAL))return;
        var tile=c.mc().level.getBlockEntity(ORIGIN.offset(2,0,0));
        var renderer=(BlockEntityRenderer<BlockEntity, BlockEntityRenderState>)c.mc().getBlockEntityRenderDispatcher().getRenderer(tile);
        c.check("Mekanism native shouldRender rejects empty unformed tower",!renderer.shouldRender(tile,Vec3.atCenterOf(tile.getBlockPos())));
        var before = tile.saveWithoutMetadata(c.mc().level.registryAccess());
        var dispatcher = c.mc().getBlockEntityRenderDispatcher();
        dispatcher.prepare(Vec3.atCenterOf(tile.getBlockPos()));
        c.check("native dispatcher skips an ineligible block entity", dispatcher.tryExtractRenderState(tile, 0, null) == null);
        c.check("native extraction leaves source NBT intact", before.equals(tile.saveWithoutMetadata(c.mc().level.registryAccess())));
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void isolation(TestContext c) {
        var dispatcher=c.mc().getBlockEntityRenderDispatcher();
        try {
            var field=dispatcher.getClass().getDeclaredField("renderers");field.setAccessible(true);
            var original=(Map<BlockEntityType<?>,BlockEntityRenderer<?, ?>>)field.get(dispatcher);
            var replacements=new HashMap<>(original);
            var delegate=(BlockEntityRenderer<BlockEntity, BlockEntityRenderState>)(BlockEntityRenderer)original.get(BlockEntityType.SIGN);
            var probe=new ThrowingRenderer(delegate);replacements.put(BlockEntityType.SIGN,probe);field.set(dispatcher,replacements);
            try {
                var preview=dialog(c).preview;var renderer=preview.scene.<WorldSceneRenderer>getRenderer();var guard=SceneRenderGuard.get(renderer);
                for(boolean cached:List.of(true,false)) {
                    preview.scene.useCacheBuffer(cached);preview.frame(ORIGIN,ORIGIN.offset(2,0,0));guard.retry();probe.reset();
                    c.mc().renderBuffers().bufferSource().endBatch();
                    for(int i=0;i<3;i++)renderer.render(new PoseStack(),0,0,180,120,0,0);
                    c.check("one failing renderer quarantined, cached="+cached,guard.failureCount()==1&&probe.failures==1&&probe.goodCalls>=3);
                    preview.frame(ORIGIN,ORIGIN.offset(2,0,0));probe.reset();renderer.render(new PoseStack(),0,0,180,120,0,0);
                    c.check("reframing retries failed renderers, cached="+cached,probe.failures==1&&guard.failureCount()==1);
                }
                var unrelated=new com.lowdragmc.lowdraglib2.client.scene.ImmediateWorldSceneRenderer(c.mc().level);
                try{c.check("non-VJCE LDLib renderers are not opted in",SceneRenderGuard.get(unrelated)==null);}finally{unrelated.releaseResource();}
            } finally { field.set(dispatcher,original); }
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static WorldSelectionDialog dialog(TestContext c){return c.<Dialog>get("renderParent").getModularUI().ui.rootElement.selectId("vjce_world_dialog",WorldSelectionDialog.class).findFirst().orElseThrow();}
    private static final class ThrowingRenderer implements BlockEntityRenderer<BlockEntity, BlockEntityRenderState> {
        private final BlockEntityRenderer<BlockEntity, BlockEntityRenderState> delegate;
        private BlockEntity target;
        private int failures, goodCalls;

        ThrowingRenderer(BlockEntityRenderer<BlockEntity, BlockEntityRenderState> delegate) {
            this.delegate = delegate;
        }

        void reset() {
            failures = goodCalls = 0;
            target = null;
        }

        @Override public boolean shouldRender(BlockEntity tile, Vec3 camera) { return true; }
        @Override public BlockEntityRenderState createRenderState() { return delegate.createRenderState(); }

        @Override
        public void extractRenderState(BlockEntity tile, BlockEntityRenderState state, float partial, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay overlay) {
            if (target == null) target = tile;
            if (tile == target) {
                failures++;
                throw new IllegalStateException("intentional VJCE block entity render-state regression");
            }
            goodCalls++;
            delegate.extractRenderState(tile, state, partial, camera, overlay);
        }

        @Override
        public void submit(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector storage, CameraRenderState camera) {
            delegate.submit(state, pose, storage, camera);
        }
    }
}
