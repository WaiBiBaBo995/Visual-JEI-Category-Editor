package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.*;
import com.pureneko.vjce.mixin.EntityDispatcherAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import java.util.*;
/** LDLib1 FBO with VJCE-owned models, isolated block-entity rendering, and local entity time. */
public final class SceneRenderer extends FBOWorldSceneRenderer {
    private final Map<BlockPos,CompoundTag> transforms=new HashMap<>();
    private final SceneBlockModels models=new SceneBlockModels(this);
    SceneEntities entities;
    List<net.minecraft.world.entity.Entity> previewEntities=List.of();
    private boolean playing;
    private float partial;
    private boolean guiScissor,cameraActive;
    private final int[] guiScissorBox=new int[4];
    org.joml.Matrix4f viewProjection;
    java.util.function.Consumer<net.minecraft.client.renderer.MultiBufferSource> overlay;
    SceneRenderer(Level world){super(world,128,128);
        // LDLib1 creates these only when a particle manager is installed.
        camera=new net.minecraft.client.Camera();cameraEntity=new com.lowdragmc.lowdraglib.client.scene.CameraEntity(world);
        SceneRenderGuard.attach(this);}
    void setBlockTransforms(ListTag rows){for(var tag:rows){var row=(CompoundTag)tag;transforms.put(new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")),row.copy());}}
    public void transform(BlockPos pos,PoseStack pose){var row=transforms.get(pos);if(row!=null)SceneTransforms.block(pose,row);}
    void entityTime(boolean playing,double budget,double speed){this.playing=playing;if(playing)partial=(float)Math.max(0,Math.min(1,budget+Minecraft.getInstance().getFrameTime()*Math.max(0,Math.min(20,speed))));}
    public float entityPartialTick(float ignored){return partial;}
    void verticalOrtho(float range,float displayWidth,float displayHeight,float depth){
        // LDLib1 divides its Y extent by the framebuffer aspect in setupCamera.
        setCameraOrtho(range*displayWidth/Math.max(1,displayHeight),range*getResolutionWidth()/getResolutionHeight(),depth);
    }
    @Override public void setFBOSize(int width,int height){try(var target=RenderTargetScope.capture()){super.setFBOSize(width,height);}}
    @Override public void render(PoseStack pose,float x,float y,float width,float height,float mouseX,float mouseY){
        var mc=Minecraft.getInstance();var dispatcher=mc.getEntityRenderDispatcher();var a=(EntityDispatcherAccess)dispatcher;
        var oldLevel=a.vjce$level();var oldCamera=dispatcher.camera;var oldCrosshair=dispatcher.crosshairPickEntity;var oldOrientation=a.vjce$orientation();boolean shadows=a.vjce$shadow(),hitboxes=a.vjce$hitBoxes();
        try(var state=new SceneRenderState()){
            guiScissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX,guiScissorBox);
            dispatcher.setRenderShadow(false);dispatcher.setRenderHitBoxes(false);
            try{super.render(pose,x,y,width,height,mouseX,mouseY);}finally{if(cameraActive)resetCamera();}
        }finally{a.vjce$level(oldLevel);dispatcher.camera=oldCamera;dispatcher.crosshairPickEntity=oldCrosshair;a.vjce$orientation(oldOrientation);dispatcher.setRenderShadow(shadows);dispatcher.setRenderHitBoxes(hitboxes);}
    }
    @Override protected void setupCamera(com.lowdragmc.lowdraglib.utils.PositionedRect viewport){
        // GUI scissors use the window's coordinates, not the scene framebuffer's.
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();super.setupCamera(viewport);cameraActive=true;
    }
    @Override protected void resetCamera(){
        super.resetCamera();cameraActive=false;
        if(guiScissor)com.mojang.blaze3d.systems.RenderSystem.enableScissor(guiScissorBox[0],guiScissorBox[1],guiScissorBox[2],guiScissorBox[3]);
    }
    @Override protected void drawWorld(){
        var mc=Minecraft.getInstance();mc.getEntityRenderDispatcher().prepare(world,camera,null);var buffers=mc.renderBuffers().bufferSource();
        viewProjection=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        try(var light=new SceneLighting()){
            models.render();
            var positions=new LinkedHashSet<BlockPos>();renderedBlocksMap.keySet().forEach(positions::addAll);
            for(var pos:positions){var tile=world.getBlockEntity(pos);if(tile==null)continue;var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(tile);if(renderer==null||!tile.getType().isValid(tile.getBlockState()))continue;
                var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());transform(pos,pose);
                SceneRenderGuard.get(this).render(this,renderer,tile,pose,buffers,isolated->renderer.render(tile,partial,isolated,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY));
            }
            for(var entity:world instanceof TrackedDummyWorld tracked?tracked.getAllEntities():previewEntities){
                var pose=new PoseStack();if(entities!=null)entities.applyEntity(world,entity,pose,partial);
                mc.getEntityRenderDispatcher().render(entity,entity.getX(),entity.getY(),entity.getZ(),entity.getYRot(),partial,pose,buffers,LightTexture.FULL_BRIGHT);
            }
            if(overlay!=null)overlay.accept(buffers);
        }finally{buffers.endBatch();}
    }
    public void releaseResource(){SceneRenderGuard.detach(this);try{models.close();deleteCacheBuffer();}finally{releaseFBO();}}
}
