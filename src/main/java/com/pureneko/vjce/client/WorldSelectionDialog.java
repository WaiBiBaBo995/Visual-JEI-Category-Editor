package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.*;

/** Two-corner read-only world selection inside the editor. Only a validated snapshot leaves this dialog. */
final class WorldSelectionDialog extends Dialog {
    final ClientLevel source;
    final WorldView preview;
    private BlockPos from, to;
    private boolean nextFrom = true;
    private final Map<String,TextField> coordinates = new LinkedHashMap<>();
    private final int[] sceneRange = {5,5,5};
    final Toggle includeEntities = new Toggle();
    private final Label status = new Label();
    private final Label renderWarning = new Label();

    static void open(UIElement parent, Supplier<CompoundTag> working, Consumer<CompoundTag> apply) {
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        if(parent instanceof Dialog modal)modal.setAutoClose(false);
        var dialog=new WorldSelectionDialog(mc.level,mc.player.getOnPos(),working,apply);
        dialog.setOnClose(()->{if(parent instanceof Dialog modal)modal.setAutoClose(true);if(parent.getParent()!=null)parent.focus();});
        dialog.show(parent.getModularUI());
    }
    private WorldSelectionDialog(ClientLevel level,BlockPos center,Supplier<CompoundTag> working,Consumer<CompoundTag> apply) {
        source=level;from=center.offset(-1,0,-1);to=center.offset(1,2,1);
        preview=new WorldView();
        setId("vjce_world_dialog");setTitle("vjce.world.title");
        overlay.layout(l->{l.widthPercent(98);l.heightPercent(97);});
        contentContainer.layout(l->{l.flex(1);l.minHeight(0);});
        var split=new SplitView.Horizontal(){@Override protected void onLayoutChanged(){super.onLayoutChanged();if(second.isDisplayed()&&getSizeWidth()>0){float max=Math.max(50,Math.min(88,100-160/getSizeWidth()*100));setMaxPercentage(max);if(getPercentage()>max)setPercentage(max);}}};
        split.setId("vjce_world_split");split.setPercentage(75);split.setMinPercentage(50);split.setMaxPercentage(88);split.setBorderSize(5);split.layout(l->{l.widthPercent(100);l.flex(1);l.minHeight(0);});
        var scroller=new ScrollerView();scroller.layout(l->{l.widthPercent(100);l.flex(1);l.minHeight(0);});split.second.addChild(scroller);var settings=scroller.getContentHost();
        var previousRatio=new float[]{75};
        addContent(new Button().setText("vjce.world.toggle_settings").setOnClick(e->{boolean visible=split.second.isDisplayed();if(visible)previousRatio[0]=split.getPercentage();split.second.setDisplay(!visible);split.setMaxPercentage(visible?100:88);split.setPercentage(visible?100:previousRatio[0]);}).setId("vjce_world_toggle_settings").layout(l->{l.height(18);l.minHeight(18);l.flexShrink(0);}));
        settings.addChild(new Label().setText("vjce.world.help").textStyle(s->s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true)).layout(l->l.widthPercent(100)));
        for(String side:List.of("from","to")){
            settings.addChild(new Label().setText("vjce.world."+side));
            for(int axis=0;axis<3;axis++){
                var row=Forms.row();row.layout(l->{l.minHeight(20);l.flexShrink(0);});
                final int a=axis;String key=side+"_"+"xyz".charAt(axis);
                row.addChild(new Label().setText(""+"xyz".charAt(axis)).layout(l->{l.width(12);l.flexShrink(0);}));
                var input=new HoverNumberField(a==1?source.getMinBuildHeight():-30000000,a==1?source.getMaxBuildHeight()-1:29999999);
                input.setTextResponder(value->{try{int n=Integer.parseInt(value);var old=side.equals("from")?from:to;
                    var pos=new BlockPos(a==0?n:old.getX(),a==1?n:old.getY(),a==2?n:old.getZ());if(side.equals("from"))from=pos;else to=pos;updateStatus();
                }catch(NumberFormatException ignored){}});
                input.setId("vjce_world_"+key);coordinates.put(key,input);row.addChild(input);settings.addChild(row);
            }
        }
        settings.addChild(new Label().setText("vjce.world.scene_range"));
        for(int axis=0;axis<3;axis++){
            var ranges=Forms.row();ranges.layout(l->{l.flexShrink(0);l.minHeight(20);});
            final int a=axis;
            ranges.addChild(new Label().setText(""+"xyz".charAt(axis)).layout(l->{l.width(12);l.flexShrink(0);}));
            var input=new HoverNumberField(0,50);input.setText("5",false);input.setId("vjce_world_range_"+"xyz".charAt(axis));
            input.setTextResponder(value->{try{sceneRange[a]=Integer.parseInt(value);nearPlayer();}catch(NumberFormatException ignored){}});
            ranges.addChild(input);settings.addChild(ranges);
        }
        includeEntities.setId("vjce_world_entities");includeEntities.setText("vjce.world.entities");includeEntities.setOn(false,false);
        includeEntities.layout(l->{l.flex(1);l.minWidth(150);l.height(20);});
        includeEntities.setOnToggleChanged(on->updateStatus());
        includeEntities.toggleLabel.addEventListener(UIEvents.CLICK,e->{if(e.button==0)includeEntities.setOn(!includeEntities.isOn(),true);});
        var options=new UIElement().layout(l->{l.widthPercent(100);l.flexShrink(0);}).addChildren(includeEntities,new Button().setText("vjce.world.near_player").setOnClick(e->nearPlayer()).setId("vjce_world_near_player"),
                new Button().setText("vjce.world.frame").setOnClick(e->{try{var selection=selection();selection.validate();preview.frame(selection.min(),selection.max());}catch(RuntimeException error){status.setText(error.getMessage());}}).setId("vjce_world_frame"));
        settings.addChild(options);status.layout(l->{l.widthPercent(100);l.minHeight(12);});status.textStyle(s->s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));settings.addChild(status);
        renderWarning.setId("vjce_world_render_warning");renderWarning.layout(l->{l.widthPercent(100);l.minWidth(0);});
        renderWarning.textStyle(s->s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        renderWarning.setDisplay(false);settings.addChild(renderWarning);
        preview.setId("vjce_world_preview");preview.layout(l->{l.widthPercent(100);l.flex(1);l.minHeight(90);});split.first.addChild(preview);addContent(split);
        addButton(new Button().setText("vjce.world.import").setOnClick(e->{
            try{
                if(Minecraft.getInstance().level!=source)throw WorldSceneCapture.error("vjce.world.changed");
                var data=WorldSceneCapture.capture(source,selection(),includeEntities.isOn(),working.get());
                apply.accept(data);close();
            }catch(RuntimeException error){status.setText(error.getMessage()==null?error.toString():error.getMessage());}
        }).addClass("__confirm-button__").setId("vjce_world_import"));
        addButton(new Button().setText("vjce.close").setOnClick(e->close()).setId("vjce_world_cancel"));
        syncCoordinates();frameAround(center);EditorForms.ore(this);
    }
    WorldSceneCapture.Selection selection(){return WorldSceneCapture.Selection.of(from,to);}
    private void nearPlayer(){var player=Minecraft.getInstance().player;if(player!=null)frameAround(player.getOnPos());}
    private void frameAround(BlockPos center){preview.frame(new BlockPos(center.getX()-sceneRange[0],Math.max(source.getMinBuildHeight(),center.getY()-sceneRange[1]),center.getZ()-sceneRange[2]),
            new BlockPos(center.getX()+sceneRange[0],Math.min(source.getMaxBuildHeight()-1,center.getY()+sceneRange[1]),center.getZ()+sceneRange[2]));}
    private void pick(BlockPos pos){if(nextFrom)from=pos.immutable();else to=pos.immutable();nextFrom=!nextFrom;syncCoordinates();}
    private void syncCoordinates(){
        for(var side:List.of("from","to")){var p=side.equals("from")?from:to;int[] values={p.getX(),p.getY(),p.getZ()};for(int a=0;a<3;a++)coordinates.get(side+"_"+"xyz".charAt(a)).setText(Integer.toString(values[a]),false);}
        updateStatus();
    }
    private void updateStatus(){
        var s=selection();try{s.validate();String text=Component.translatable("vjce.world.selection",s.max().getX()-s.min().getX()+1,s.max().getY()-s.min().getY()+1,s.max().getZ()-s.min().getZ()+1).getString();if(includeEntities.isOn())text+=" "+Component.translatable("vjce.world.entity_count",WorldSceneCapture.selectedEntities(source,s).size()).getString();status.setText(text);}
        catch(RuntimeException error){status.setText(error.getMessage());}
    }
    final class WorldView extends com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor {
        private final SceneRenderGuard renderGuard;
        private int lastFailures = -1;
        net.minecraft.world.phys.AABB framed;
        private org.joml.Matrix4f viewProjection;
        private final int[] viewport=new int[4];
        private double projectionScale;
        private int projectionHeight;
        WorldView(){
            disableTransformGizmo();scene.useCacheBuffer(true).syncCompile(true);scene.createScene(source);scene.setTickWorld(false);scene.setRenderFacing(false);scene.setRenderSelect(false);scene.useOrtho(true);
            renderGuard=SceneRenderGuard.attach(scene.getRenderer());
            ((Selector<Boolean>)topBar.getChildren().getFirst()).setSelected(true,false);
            addEventListener(UIEvents.MOUSE_DOWN,e->{
                if(e.target==scene&&e.button==0&&e.isShiftDown()){
                    var hit=scene.getLastHoverPosFace();if(hit!=null)pick(hit.pos());e.stopImmediatePropagation();
                }
            },true);
        }
        void frame(BlockPos a,BlockPos b){
            renderGuard.retry();
            var region=WorldSceneCapture.Selection.of(a,b);var positions=new LinkedHashSet<BlockPos>();
            for(var pos:BlockPos.betweenClosed(region.min(),region.max()))if(!source.isOutsideBuildHeight(pos)&&WorldSceneCapture.loaded(source,pos)&&!source.getBlockState(pos).isAir())positions.add(pos.immutable());
            scene.setRenderedCore(positions,null,false);
            scene.setCenter(region.bounds().getCenter().toVector3f());scene.setCameraYawAndPitch(-135,30);scene.setZoom(1);
            framed=region.bounds();fit();
        }
        private void fit(){if(framed!=null){double radius=Math.sqrt(framed.getXsize()*framed.getXsize()+framed.getYsize()*framed.getYsize()+framed.getZsize()*framed.getZsize())/2+1;scene.setOrthoRange((float)(radius*Math.max(1,scene.getContentWidth()/Math.max(1,scene.getContentHeight()))));}}
        @Override public void screenTick(){super.screenTick();fit();int failures=renderGuard.failureCount();if(failures!=lastFailures){lastFailures=failures;renderWarning.setText(Component.translatable("vjce.world.render_warning",failures));renderWarning.setDisplay(failures>0);}}
        @Override protected void onRemoved(){SceneRenderGuard.detach(scene.getRenderer());super.onRemoved();}
        @Override public Optional<org.joml.Vector2f> project(Vector3f pos){
            if(viewProjection==null)return Optional.empty();
            var clip=viewProjection.transform(new org.joml.Vector4f(pos,1));
            if(Math.abs(clip.w)<.000001)return Optional.empty();
            float x=viewport[0]+(clip.x/clip.w+1)*viewport[2]/2;
            float y=viewport[1]+(clip.y/clip.w+1)*viewport[3]/2;
            return Optional.of(new org.joml.Vector2f((float)(x/projectionScale),(float)((projectionHeight-y)/projectionScale)));
        }
        @Override protected void renderAfterWorld(MultiBufferSource buffer,float partial){
            viewProjection=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
            var surface=com.lowdragmc.lowdraglib2.gui.ui.rendering.UISurface.current();projectionScale=surface.guiScale();projectionHeight=surface.framebufferHeight();
            super.renderAfterWorld(buffer,partial);
            try{
                var selected=selection();selected.validate();
                LevelRenderer.renderLineBox(new PoseStack(),buffer.getBuffer(RenderType.lines()),selected.bounds(),1,1,1,1);
                if(includeEntities.isOn())for(var entity:WorldSceneCapture.selectedEntities(source,selected))LevelRenderer.renderLineBox(new PoseStack(),buffer.getBuffer(RenderType.lines()),entity.getBoundingBox(),.3f,1,.3f,1);
                if(buffer instanceof MultiBufferSource.BufferSource buffers)buffers.endBatch(RenderType.lines());
            }catch(IllegalArgumentException ignored){}
        }
    }
}
