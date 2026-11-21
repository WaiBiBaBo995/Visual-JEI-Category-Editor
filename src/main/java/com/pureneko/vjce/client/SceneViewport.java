package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Interactive scene camera and object selection. Working data belongs to the scene tab. */
final class SceneViewport extends Widget {
    private final SceneEditingView owner;
    private final BlockSceneElement scene;
    private boolean cameraDrag,pan;
    private char operation,axis;
    private double startX,startY,lastX,lastY;
    private CompoundTag before;
    private String kind;
    private int index;
    SceneViewport(SceneEditingView owner,int w,int h) {
        super(0,0,w,h);this.owner=owner;scene=owner.preview;
    }
    @Override public void drawInBackground(GuiGraphics g,int mx,int my,float pt) {
        var p=getPosition();lastX=mx-p.x;lastY=my-p.y;
        scene.width=getSize().width;scene.height=getSize().height;
        scene.ensureScene();
        if(scene.renderer!=null)scene.renderer.overlay=buffers->{
            var row=owner.selectedRow();if(row==null)return;
            AABB bounds=owner.selectedKind().equals("blocks")?SceneTransforms.bounds(new net.minecraft.core.BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")),row):new AABB(row.getDouble("x")-.4,row.getDouble("y"),row.getDouble("z")-.4,row.getDouble("x")+.4,row.getDouble("y")+1.8,row.getDouble("z")+.4);
            LevelRenderer.renderLineBox(new PoseStack(),buffers.getBuffer(RenderType.lines()),bounds,1,1,.2f,1);
        };
        g.fill(p.x,p.y,p.x+getSize().width,p.y+getSize().height,Forms.BG);
        g.pose().pushPose();try{g.pose().translate(p.x,p.y,0);scene.render(g,lastX,lastY,pt);}finally{g.flush();g.pose().popPose();}
        g.drawWordWrap(net.minecraft.client.Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("vjce.scene.viewport_help"),p.x+3,p.y+3,getSize().width-6,0xffdddddd);
    }
    Vec3[] ray(double x,double y) {
        if(scene.renderer==null||scene.renderer.viewProjection==null)return null;
        var inverse=new Matrix4f(scene.renderer.viewProjection).invert();
        float nx=(float)(2*x/getSize().width-1),ny=(float)(1-2*y/getSize().height);
        var near=inverse.transformProject(new Vector3f(nx,ny,-1));
        var far=inverse.transformProject(new Vector3f(nx,ny,1));
        return new Vec3[]{new Vec3(near),new Vec3(far)};
    }
    Vec3 dropPoint(boolean entity){
        var ray=ray(lastX,lastY);if(ray==null)return null;
        double nearest=Double.POSITIVE_INFINITY;Vec3 result=null;
        for(var tag:BlockSceneElement.rows(SceneAssets.resolve(scene.blocks),"blocks")){
            var row=(CompoundTag)tag;var pos=new net.minecraft.core.BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z"));
            var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());SceneTransforms.block(pose,row);
            var matrix=pose.last().pose();var inverse=new Matrix4f(matrix).invert();
            var near=new Vec3(inverse.transformPosition(ray[0].toVector3f()));var far=new Vec3(inverse.transformPosition(ray[1].toVector3f()));
            var hit=new AABB(0,0,0,1,1,1).clip(near,far);if(hit.isEmpty())continue;
            var point=hit.get();var world=new Vec3(matrix.transformPosition(point.toVector3f()));double distance=world.distanceToSqr(ray[0]);if(distance>=nearest)continue;
            nearest=distance;
            if(entity)result=new Vec3(world.x,SceneTransforms.bounds(pos,row).maxY,world.z);
            else{
                double[] distances={Math.abs(point.y),Math.abs(point.y-1),Math.abs(point.z),Math.abs(point.z-1),Math.abs(point.x),Math.abs(point.x-1)};
                int face=0;for(int i=1;i<6;i++)if(distances[i]<distances[face])face=i;
                var normal=net.minecraft.core.Direction.values()[face].step();matrix.transformDirection(normal);
                var direction=net.minecraft.core.Direction.getNearest(normal.x,normal.y,normal.z);result=Vec3.atLowerCornerOf(pos.relative(direction));
            }
        }
        if(result!=null)return result;
        double dy=ray[1].y-ray[0].y;if(Math.abs(dy)<.000001)return null;
        double t=-ray[0].y/dy;if(t<0||t>1)return null;return ray[0].lerp(ray[1],t);
    }
    void pick(double x,double y) {
        var ray=ray(x,y);if(ray==null)return;
        double nearest=Double.POSITIVE_INFINITY;String found="blocks";int selected=-1;
        var resolved=SceneAssets.resolve(scene.blocks);
        for(String kind:List.of("blocks","entities")){
            var rows=BlockSceneElement.rows(resolved,kind);
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i);AABB bounds;
                if(kind.equals("blocks"))bounds=SceneTransforms.bounds(new net.minecraft.core.BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")),row);
                else if(i<scene.sceneEntities().size())bounds=scene.sceneEntities().get(i).getBoundingBox();else continue;
                java.util.Optional<Vec3> hit;
                if(kind.equals("blocks")){
                    var transform=new PoseStack();transform.translate(row.getInt("x"),row.getInt("y"),row.getInt("z"));SceneTransforms.block(transform,row);
                    var matrix=transform.last().pose();var inverse=new Matrix4f(matrix).invert();
                    var near=new Vec3(inverse.transformPosition(ray[0].toVector3f()));var far=new Vec3(inverse.transformPosition(ray[1].toVector3f()));
                    hit=new AABB(0,0,0,1,1,1).clip(near,far).map(point->new Vec3(matrix.transformPosition(point.toVector3f())));
                }else hit=bounds.clip(ray[0],ray[1]);
                if(hit.isPresent()){double distance=hit.get().distanceToSqr(ray[0]);if(distance<nearest){nearest=distance;found=kind;selected=i;}}
            }
        }
        owner.select(found,selected);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(!isMouseOverElement(mx,my))return false;
        var p=getPosition();startX=mx-p.x;startY=my-p.y;
        if(operation!=0){finish();return true;}
        if(button==0)pick(startX,startY);
        else{cameraDrag=true;pan=button==2||Screen.hasShiftDown();}
        return true;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(operation!=0){update(mx-getPosition().x,my-getPosition().y);return true;}
        if(!cameraDrag)return false;
        if(pan){double yaw=Math.toRadians(scene.cameraYaw);float unit=scene.cameraRange*2/getSize().height;scene.cameraOffsetX-=(float)(Math.cos(yaw)*dx*unit);scene.cameraOffsetZ+=(float)(Math.sin(yaw)*dx*unit);scene.cameraOffsetY+=(float)(dy*unit);}
        else{scene.cameraYaw-=dx*.6f;scene.cameraPitch=Math.max(-89,Math.min(89,scene.cameraPitch-(float)dy*.6f));}
        return true;
    }
    @Override public boolean mouseMoved(double mx,double my) {
        lastX=mx-getPosition().x;lastY=my-getPosition().y;
        if(operation!=0){update(lastX,lastY);return true;}
        return false;
    }
    @Override public boolean mouseReleased(double mx,double my,int button){boolean result=cameraDrag;cameraDrag=false;return result;}
    @Override public boolean mouseWheelMove(double mx,double my,double delta){if(!isMouseOverElement(mx,my))return false;scene.autoFrame=false;scene.cameraRange=(float)Math.max(.2,Math.min(128,scene.cameraRange*Math.pow(1.12,-delta)));return true;}
    boolean key(int key){
        if(key==256&&operation!=0){cancel();return true;}
        if(key==257&&operation!=0){finish();return true;}
        if(operation!=0&&(key==88||key==89||key==90)){axis=(char)(key+32);return true;}
        if((key==71||key==82||key==83)&&owner.selectedRow()!=null){cancel();operation=(char)(key+32);axis=0;before=owner.selectedRow().copy();kind=owner.selectedKind();index=owner.selectedIndex();startX=lastX;startY=lastY;return true;}
        return false;
    }
    private void update(double x,double y){
        if(before==null)return;var rows=BlockSceneElement.rows(scene.blocks,kind);if(index>=rows.size())return;
        var row=before.copy();double dx=x-startX,dy=y-startY;
        if(operation=='g'){
            double unit=scene.cameraRange*2/getSize().height,yaw=Math.toRadians(scene.cameraYaw);
            double px=(Math.cos(yaw)*dx+Math.sin(yaw)*dy)*unit,pz=(-Math.sin(yaw)*dx+Math.cos(yaw)*dy)*unit;
            if(axis=='y'){px=0;pz=0;row.putDouble("y",before.getDouble("y")-dy*unit);}else{
                if(axis!='z')row.putDouble("x",before.getDouble("x")+px);
                if(axis!='x')row.putDouble("z",before.getDouble("z")+pz);
            }
            if(kind.equals("blocks")||Screen.hasShiftDown())for(String k:List.of("x","y","z")){double value=Math.round(row.getDouble(k));if(kind.equals("blocks"))row.putInt(k,(int)value);else row.putDouble(k,value);}
        }else if(operation=='r'){
            String field=axis=='x'?"pitch":axis=='z'?"roll":"yaw";double value=before.getDouble(field)+dx;
            if(Screen.hasShiftDown())value=Math.round(value/15)*15;row.putDouble(field,value);
        }else{double scale=(before.contains("scale")?before.getDouble("scale"):1)*Math.exp(dx/100);row.putDouble("scale",Math.max(.05,Math.min(16,scale)));}
        rows.set(index,row);scene.release();
    }
    void cancel(){if(before!=null){var rows=BlockSceneElement.rows(scene.blocks,kind);if(index<rows.size())rows.set(index,before.copy());scene.release();}before=null;operation=axis=0;}
    void finish(){before=null;operation=axis=0;owner.refresh();}
}
