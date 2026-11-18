package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.utils.Size;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import java.lang.Math;
import java.util.*;
import java.util.function.*;

/** MBD2-style free camera and two-corner selection, hosted by a full-screen capture dialog. */
final class WorldSelectionDialog extends DialogWidget {
    private final ClientLevel source;
    final WorldView view;
    final Forms.Sidebar sidebar;
    final Forms.Form form;
    final ConfiguratorGroup controls=new ConfiguratorGroup("vjce.world.title",false);
    private final LabelWidget status;
    BlockPos from,to;
    boolean nextFrom=true,includeEntities=true;
    private boolean released;
    int sceneRadius=5;
    static void open(WidgetGroup origin,Supplier<CompoundTag> working,Consumer<CompoundTag> apply){
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.player!=null)new WorldSelectionDialog(origin,working,apply);
    }
    private WorldSelectionDialog(WidgetGroup origin,Supplier<CompoundTag> working,Consumer<CompoundTag> apply){
        super(origin.getGui().mainGroup,true);setBackground(new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(Forms.BG));source=Minecraft.getInstance().level;
        int w=getSize().width,h=getSize().height,panel=Math.min(330,Math.max(245,w/3));
        from=Minecraft.getInstance().player.getOnPos();to=from;
        view=new WorldView(3,24,w-panel-9,h-49);addWidget(view);
        form=new Forms.Form(0,0,panel-2,h-68);
        sidebar=new Forms.Sidebar(w-panel-3,24,panel,h-49,"vjce.scene.configure",form);addWidget(sidebar);
        sidebar.onResize=this::layoutContent;
        status=new LabelWidget(3,h-20,"");addWidget(status);
        controls.setCanCollapse(false);
        controls.addConfigurators(new NumberConfigurator("vjce.world.scene_range",()->sceneRadius,n->{sceneRadius=n.intValue();reloadScene();},5,true).setRange(1,50).setWheel(1));
        var area=new ConfiguratorGroup("vjce.world.area",false);
        area.addConfigurators(position("vjce.world.from",true),position("vjce.world.to",false));
        var hint=new TextBoxWidget(0,0,panel-25,List.of("vjce.world.help")).setFontColor(-1);
        area.addConfigurators(new WrapperConfigurator(hint));controls.addConfigurators(area);
        controls.addConfigurators(new BooleanConfigurator("vjce.world.entities",()->includeEntities,v->{includeEntities=v;updateStatus();},true,true));
        controls.addConfigurators(new ButtonConfigurator("vjce.world.near_player",this::nearPlayer),new ButtonConfigurator("vjce.world.frame",()->{try{var s=selection();s.validate();view.lookAt(s.min(),s.max());}catch(RuntimeException e){status.setText(e.getMessage());}}));
        controls.addConfigurators(new ButtonConfigurator("vjce.world.import",()->{try{if(Minecraft.getInstance().level!=source)throw WorldSceneCapture.error("vjce.world.changed");apply.accept(WorldSceneCapture.capture(source,selection(),includeEntities,working.get()));close();}catch(RuntimeException e){status.setText(e.getMessage());}}),new ButtonConfigurator("vjce.close",this::close));
        controls.setConfiguratorContainer(()->{controls.computeHeight();form.computeMax();});controls.init(form.width());controls.computeHeight();form.addWidget(controls);
        addWidget(Forms.button(3,2,160,"vjce.world.toggle_settings",()->sidebar.setCollapsed(!sidebar.collapsed)));
        nearPlayer();updateStatus();origin.getGui().registerCloseListener(this::release);
    }
    private void layoutContent(){int w=getSize().width,h=getSize().height;sidebar.setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position(w-sidebar.occupied()-3,24));view.setSize(new Size(Math.max(20,w-sidebar.occupied()-9),Math.max(20,h-49)));}
    void resizeDialog(int w,int h){setSize(new Size(w,h));sidebar.resizePanel(sidebar.expandedWidth,h-49);status.setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position(3,h-20));layoutContent();}
    @Override public void drawInBackground(GuiGraphics g,int mx,int my,float pt){sidebar.advanceAnimation(System.nanoTime());super.drawInBackground(g,mx,my,pt);}
    private Vector3iConfigurator position(String key,boolean first){
        Supplier<Vector3i> value=()->{var p=first?from:to;return new Vector3i(p.getX(),p.getY(),p.getZ());};
        return new Vector3iConfigurator(key,value,v->{if(first)from=new BlockPos(v.x,v.y,v.z);else to=new BlockPos(v.x,v.y,v.z);updateStatus();},value.get(),true).setRange(-30000000,30000000).setWheel(1);
    }
    private WorldSceneCapture.Selection selection(){return WorldSceneCapture.Selection.of(from,to);}
    private void nearPlayer(){reloadScene();var p=Minecraft.getInstance().player.getOnPos();view.lookAt(p.offset(-sceneRadius,-sceneRadius,-sceneRadius),p.offset(sceneRadius,sceneRadius,sceneRadius));}
    private void reloadScene(){var p=Minecraft.getInstance().player.getOnPos();view.load(p.offset(-sceneRadius,-sceneRadius,-sceneRadius),p.offset(sceneRadius,sceneRadius,sceneRadius));}
    void pick(BlockPos pos){if(nextFrom)from=pos.immutable();else to=pos.immutable();nextFrom=!nextFrom;updateStatus();}
    private void updateStatus(){try{var s=selection();s.validate();String text=Component.translatable("vjce.world.selection",s.max().getX()-s.min().getX()+1,s.max().getY()-s.min().getY()+1,s.max().getZ()-s.min().getZ()+1).getString();if(includeEntities)text+=" "+Component.translatable("vjce.world.entity_count",WorldSceneCapture.selectedEntities(source,s).size()).getString();status.setText(text);}catch(RuntimeException e){status.setText(e.getMessage());}}
    @Override public void close(){release();super.close();}
    private void release(){if(!released){released=true;view.renderer.releaseResource();}}
    final class WorldView extends Widget {
        final SceneRenderer renderer=new SceneRenderer(source);
        final Set<BlockPos> rendered=new HashSet<>();
        final Vector3f eye=new Vector3f(),center=new Vector3f();
        float moveSpeed=.1f;
        boolean cameraMoving;
        BlockHitResult pressed;
        private long lastFrame=System.nanoTime();
        WorldView(int x,int y,int w,int h){super(x,y,w,h);renderer.useOrtho(false);renderer.overlay=buffers->{
            var s=selection();LevelRenderer.renderLineBox(new PoseStack(),buffers.getBuffer(RenderType.lines()),s.bounds(),1,1,1,1);
            DebugRenderer.renderFilledBox(new PoseStack(),buffers,new AABB(from).inflate(.002),0,1,0,.2f);
            DebugRenderer.renderFilledBox(new PoseStack(),buffers,new AABB(to).inflate(.002),1,0,0,.2f);
            if(includeEntities)for(var entity:WorldSceneCapture.selectedEntities(source,s))LevelRenderer.renderLineBox(new PoseStack(),buffers.getBuffer(RenderType.lines()),entity.getBoundingBox(),.3f,1,.3f,1);
        };}
        void load(BlockPos a,BlockPos b){
            rendered.clear();var s=WorldSceneCapture.Selection.of(a,b);
            for(var p:BlockPos.betweenClosed(s.min(),s.max()))if(WorldSceneCapture.loaded(source,p)&&!source.isOutsideBuildHeight(p)&&!source.getBlockState(p).isAir())rendered.add(p.immutable());
            renderer.renderedBlocksMap.clear();renderer.addRenderedBlocks(rendered,null);SceneRenderGuard.get(renderer).retry();renderer.previewEntities=source.getEntities(null,s.bounds()).stream().limit(128).toList();
        }
        void lookAt(BlockPos a,BlockPos b){var bounds=WorldSceneCapture.Selection.of(a,b).bounds();center.set(bounds.getCenter().toVector3f());float range=(float)Math.max(4,Math.max(bounds.getYsize(),Math.max(bounds.getXsize(),bounds.getZsize())));eye.set(center).add(range,.8f*range,range);}
        private boolean down(int key){return com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),key);}
        private void moveCamera(float dt){
            if(!cameraMoving)return;float speed=moveSpeed*dt*20*(Screen.hasShiftDown()?5:1);
            var forward=new Vector3f(center).sub(eye).normalize();var right=new Vector3f(forward).cross(0,1,0).normalize();var delta=new Vector3f();
            if(down(GLFW.GLFW_KEY_W))delta.add(forward);if(down(GLFW.GLFW_KEY_S))delta.sub(forward);if(down(GLFW.GLFW_KEY_D))delta.add(right);if(down(GLFW.GLFW_KEY_A))delta.sub(right);if(down(GLFW.GLFW_KEY_E))delta.y++;if(down(GLFW.GLFW_KEY_Q))delta.y--;
            delta.mul(speed);eye.add(delta);center.add(delta);
        }
        @Override public void drawInBackground(GuiGraphics g,int mx,int my,float pt){
            if(released)return;long now=System.nanoTime();moveCamera(Math.min(.1f,(now-lastFrame)/1e9f));lastFrame=now;
            var p=getPosition();var size=getSize();g.fill(p.x,p.y,p.x+size.width,p.y+size.height,0xff111111);
            renderer.setCameraLookAt(eye,center,new Vector3f(0,1,0));
            int rw=Math.min(1600,Math.max(64,size.width*2)),rh=Math.min(1200,Math.max(64,size.height*2));if(renderer.getResolutionWidth()!=rw||renderer.getResolutionHeight()!=rh)renderer.setFBOSize(rw,rh);
            g.flush();renderer.render(g.pose(),p.x,p.y,size.width,size.height,mx,my);g.flush();
        }
        BlockHitResult hit(double mx,double my){
            if(renderer.viewProjection==null||!isMouseOverElement(mx,my))return null;
            float x=(float)((mx-getPosition().x)*2/getSize().width-1),y=(float)(1-(my-getPosition().y)*2/getSize().height);var inverse=new Matrix4f(renderer.viewProjection).invert();
            var a=new Vec3(inverse.transformProject(new Vector3f(x,y,-1)));var b=new Vec3(inverse.transformProject(new Vector3f(x,y,1)));
            var hit=source.clip(new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,Minecraft.getInstance().player));return hit.getType()!=HitResult.Type.MISS&&rendered.contains(hit.getBlockPos())?hit:null;
        }
        @Override public boolean mouseClicked(double mx,double my,int button){if(!isMouseOverElement(mx,my))return false;if(button==0)pressed=hit(mx,my);if(button==1)cameraMoving=true;return true;}
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
            if(!cameraMoving||button!=1)return false;var dir=new Vector3f(center).sub(eye);var right=new Vector3f(dir).cross(0,1,0).normalize();dir.rotateAxis((float)Math.toRadians(-dy),right.x,right.y,right.z).rotateY((float)Math.toRadians(-dx));if(Math.abs(new Vector3f(dir).normalize().y)<.999f)center.set(eye).add(dir);return true;
        }
        @Override public boolean mouseReleased(double mx,double my,int button){return release(mx,my,button,Screen.hasShiftDown());}
        boolean release(double mx,double my,int button,boolean shift){boolean consumed=cameraMoving||pressed!=null;if(button==1)cameraMoving=false;if(button==0){var hover=hit(mx,my);if(shift&&pressed!=null&&hover!=null&&pressed.getBlockPos().equals(hover.getBlockPos())&&pressed.getDirection()==hover.getDirection())pick(hover.getBlockPos());pressed=null;}return consumed;}
        @Override public boolean mouseWheelMove(double mx,double my,double delta){if(!isMouseOverElement(mx,my))return false;if(cameraMoving)moveSpeed=Math.max(.02f,Math.min(10,moveSpeed+(delta>0?.01f:-.01f)));else{var d=new Vector3f(center).sub(eye).normalize().mul((float)delta*.5f);eye.add(d);center.add(d);}return true;}
    }
}
