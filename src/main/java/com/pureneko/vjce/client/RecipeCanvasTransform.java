package com.pureneko.vjce.client;

import com.pureneko.vjce.client.canvas.CanvasElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Layout gestures use local coordinates; handles and guides use final screen coordinates. */
final class RecipeCanvasTransform {
    final RecipeLayoutView view;
    private record Before(CanvasElement element,float x,float y,float width,float height,float rotation,
                          float sx,float sy,Matrix4f inverse,Matrix4f localInverse,Matrix4f matrix) {}
    private final List<Before> before=new ArrayList<>();
    CanvasElement editing;
    private Vector2f start;
    private int handleX,handleY;
    private boolean resizing,rotating,changed;
    private float lastAngle,accumulated;
    private int documentWidth,documentHeight,snapX,snapY;
    boolean pan;
    private Float guideX,guideY;
    RecipeCanvasTransform(RecipeLayoutView view){this.view=view;}
    void picked(CanvasElement element,Vector2f point,boolean ctrl,boolean shift){
        if(ctrl)editing=element;else if(editing!=element)editing=null;
        if(!ctrl&&shift&&element!=view.getCurrentUI().rootElement)begin(point,false,false);
    }
    boolean beginHandle(Vector2f point,boolean rotate){
        if(editing==null||view.selection.size()!=1||!view.selection.contains(editing)||!editing.visible){editing=null;return false;}
        int[] handle=hitHandle(editing,point);
        boolean inside=editing.isMouseOverElement(point.x,point.y);
        if(rotate&&(inside||handle!=null)&&editing!=view.getCurrentUI().rootElement){begin(point,true,false);return true;}
        if(handle!=null){begin(point,false,true);handleX=handle[0];handleY=handle[1];return true;}
        if(!inside)editing=null;
        return false;
    }
    private int[] hitHandle(CanvasElement element,Vector2f point){
        var mouse=screen(point);float radius=(float)(6/Minecraft.getInstance().getWindow().getGuiScale());
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if((x!=0||y!=0)&&handlePoint(element,x,y).distance(mouse)<=radius)return new int[]{x,y};
        var corners=new Vector2f[]{handlePoint(element,-1,-1),handlePoint(element,1,-1),handlePoint(element,1,1),handlePoint(element,-1,1)};
        int[][] handles={{0,-1},{1,0},{0,1},{-1,0}};
        for(int i=0;i<4;i++)if(segmentDistance(mouse,corners[i],corners[(i+1)%4])<=radius)return handles[i];
        return null;
    }
    private static float segmentDistance(Vector2f p,Vector2f a,Vector2f b){var d=new Vector2f(b).sub(a);float t=Math.max(0,Math.min(1,new Vector2f(p).sub(a).dot(d)/Math.max(.0001f,d.lengthSquared())));return new Vector2f(a).fma(t,d).distance(p);}
    void begin(Vector2f point,boolean rotate,boolean resize){
        cancel();start=new Vector2f(point);rotating=rotate;resizing=resize;
        documentWidth=view.project.document.width;documentHeight=view.project.document.height;
        for(var e:CanvasGrouping.topLevel(view.selection)){
            if(e==view.getCurrentUI().rootElement&&!resize)continue;
            var parent=e.getParent();before.add(new Before(e,e.x,e.y,e.width,e.height,e.rotation,e.scaleX,e.scaleY,parent==null?new Matrix4f():parent.worldMatrix().invert(),e.worldMatrix().invert(),e.localMatrix()));
        }
        if(rotate&&!before.isEmpty()){var b=before.get(0);var a=parentPoint(b,point);lastAngle=(float)Math.atan2(a.y-b.y-b.height/2,a.x-b.x-b.width/2);accumulated=0;}
    }
    boolean update(Vector2f point,boolean shift,boolean ctrl){return update(point,shift,ctrl,net.minecraft.client.gui.screens.Screen.hasAltDown());}
    boolean update(Vector2f point,boolean shift,boolean ctrl,boolean alt){
        if(before.isEmpty())return false;
        if(point.distanceSquared(start)<.01f&&!changed)return true;
        changed=true;guideX=guideY=null;
        for(var b:before){
            var e=b.element;var a=parentPoint(b,start);var c=parentPoint(b,point);
            if(rotating){
                float angle=(float)Math.atan2(c.y-b.y-b.height/2,c.x-b.x-b.width/2),delta=angle-lastAngle;
                while(delta>Math.PI)delta-=Math.PI*2;while(delta< -Math.PI)delta+=Math.PI*2;
                accumulated+=(float)Math.toDegrees(delta);lastAngle=angle;e.rotation=b.rotation+accumulated;
                if(shift)e.rotation=Math.round(e.rotation/15)*15;
            }else if(resizing){
                var la=b.localInverse.transformPosition(new Vector3f(start.x,start.y,0));var lc=b.localInverse.transformPosition(new Vector3f(point.x,point.y,0));
                float w=Math.max(1,b.width+handleX*(lc.x-la.x)),h=Math.max(1,b.height+handleY*(lc.y-la.y));
                boolean proportional=ctrl&&handleX!=0&&handleY!=0;
                if(proportional){float ratio=Math.abs(w/b.width-1)>=Math.abs(h/b.height-1)?w/b.width:h/b.height;ratio=Math.max(Math.max(1/b.width,1/b.height),ratio);w=b.width*ratio;h=b.height*ratio;}
                resizeAt(b,w,h);
                if(!alt)snapResize(b,proportional);
            }else{e.x=b.x+c.x-a.x;e.y=b.y+c.y-a.y;}
        }
        if(!rotating&&!resizing&&!alt){var delta=snap(bounds(before.stream().map(Before::element).toList()),null);for(var b:before){var d=b.inverse.transformDirection(new Vector3f(delta.x,delta.y,0));b.element.x+=d.x;b.element.y+=d.y;}}
        return true;
    }
    private Vector3f parentPoint(Before b,Vector2f p){return b.inverse.transformPosition(new Vector3f(p.x,p.y,0));}
    private void resizeAt(Before b,float w,float h){
        var e=b.element;float ax=(1-handleX)/2f,ay=(1-handleY)/2f;
        var anchor=b.matrix.transformPosition(new Vector3f(b.width*ax,b.height*ay,0));
        e.width=Math.max(1,Math.min(8192,w));e.height=Math.max(1,Math.min(8192,h));e.x=0;e.y=0;
        var current=e.localMatrix().transformPosition(new Vector3f(e.width*ax,e.height*ay,0));e.x=anchor.x-current.x;e.y=anchor.y-current.y;
        if(e==view.getCurrentUI().rootElement){e.x=b.x;e.y=b.y;e.width=view.project.document.width=Math.max(18,Math.min(2048,Math.round(e.width)));e.height=view.project.document.height=Math.max(18,Math.min(2048,Math.round(e.height)));}
    }
    private void snapResize(Before b,boolean proportional){
        var e=b.element;float w=e.width,h=e.height,eps=.25f;var base=bounds(List.of(e));
        resizeAt(b,w+eps,h);var bx=bounds(List.of(e));resizeAt(b,w,h+eps);var by=bounds(List.of(e));resizeAt(b,w,h);
        boolean[] movable=new boolean[6];for(int i=0;i<6;i++)movable[i]=(handleX!=0&&Math.abs(bx[i]-base[i])>.001f)||(handleY!=0&&Math.abs(by[i]-base[i])>.001f);
        var correction=snap(base,movable);float dw=0,dh=0;
        if(proportional){float x=(bx[snapX]-base[snapX])/eps*w+(by[snapX]-base[snapX])/eps*h,y=(bx[snapY+3]-base[snapY+3])/eps*w+(by[snapY+3]-base[snapY+3])/eps*h;float ratio=guideX!=null&&Math.abs(x)>.001f?correction.x/x:guideY!=null&&Math.abs(y)>.001f?correction.y/y:0;dw=w*ratio;dh=h*ratio;}
        else{float a=handleX==0?0:(bx[snapX]-base[snapX])/eps,bb=handleY==0?0:(by[snapX]-base[snapX])/eps,c=handleX==0?0:(bx[snapY+3]-base[snapY+3])/eps,d=handleY==0?0:(by[snapY+3]-base[snapY+3])/eps,det=a*d-bb*c;
            if(guideX!=null&&guideY!=null&&Math.abs(det)>.001f){dw=(correction.x*d-bb*correction.y)/det;dh=(a*correction.y-correction.x*c)/det;}
            else{float norm=guideX!=null?a*a+bb*bb:c*c+d*d;if(norm>.001f){dw=(guideX!=null?a*correction.x:c*correction.y)/norm;dh=(guideX!=null?bb*correction.x:d*correction.y)/norm;}}
        }
        resizeAt(b,Math.max(1,w+dw),Math.max(1,h+dh));
    }
    private float[] bounds(Collection<CanvasElement> elements){
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(var e:elements)for(var p:List.of(new Vector3f(),new Vector3f(e.width,0,0),new Vector3f(0,e.height,0),new Vector3f(e.width,e.height,0))){e.worldMatrix().transformPosition(p);minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
        return new float[]{minX,(minX+maxX)/2,maxX,minY,(minY+maxY)/2,maxY};
    }
    private Vector2f snap(float[] box,boolean[] movable){
        float threshold=(float)(6/Minecraft.getInstance().getWindow().getGuiScale())/view.zoom,bestX=threshold,bestY=threshold,dx=0,dy=0;
        var moving=before.stream().map(Before::element).toList();
        for(var candidate:view.getCurrentUI().rootElement.select("*").toList()){
            if(!candidate.visible||candidate instanceof NativePartElement part&&!part.useSourceDrawing)continue;boolean excluded=false;
            for(var e=candidate;e!=null;e=e.getParent())if(moving.contains(e)){excluded=true;break;}if(excluded)continue;
            var target=bounds(List.of(candidate));
            for(int i=0;i<3;i++)for(int j=0;j<3;j++){
                float x=target[j]-box[i],y=target[j+3]-box[i+3];
                if((movable==null||movable[i])&&Math.abs(x)<bestX){bestX=Math.abs(x);dx=x;guideX=target[j];snapX=i;}
                if((movable==null||movable[i+3])&&Math.abs(y)<bestY){bestY=Math.abs(y);dy=y;guideY=target[j+3];snapY=i;}
            }
        }
        return new Vector2f(dx,dy);
    }
    boolean finish(){if(before.isEmpty())return false;boolean commit=changed;resetGesture();if(commit){view.markAsDirty();view.inspect();}return true;}
    private void resetGesture(){guideX=guideY=null;before.clear();start=null;handleX=handleY=0;changed=false;resizing=rotating=false;}
    void cancel(){if(!before.isEmpty()){for(var b:before){var e=b.element;e.x=b.x;e.y=b.y;e.width=b.width;e.height=b.height;e.rotation=b.rotation;e.scaleX=b.sx;e.scaleY=b.sy;}view.project.document.width=documentWidth;view.project.document.height=documentHeight;}resetGesture();pan=false;}
    boolean escape(){if(!before.isEmpty()){cancel();return true;}if(editing!=null){editing=null;return true;}return false;}
    void clear(){cancel();editing=null;}
    boolean isEditing(){return editing!=null;}
    Vector2f screen(Vector2f p){var pos=view.stage.getPosition();return new Vector2f(pos.x+view.panX+p.x*view.zoom,pos.y+view.panY+p.y*view.zoom);}
    Vector2f handlePoint(CanvasElement e,int x,int y){var v=e.worldMatrix().transformPosition(new Vector3f(e.width*(x+1)/2,e.height*(y+1)/2,0));return screen(new Vector2f(v.x,v.y));}
    void outline(GuiGraphics g,CanvasElement e,int color){var a=handlePoint(e,-1,-1);var b=handlePoint(e,1,-1);var c=handlePoint(e,1,1);var d=handlePoint(e,-1,1);line(g,a,b,color);line(g,b,c,color);line(g,c,d,color);line(g,d,a,color);}
    private void line(GuiGraphics g,Vector2f a,Vector2f b,int color){g.pose().pushPose();g.pose().translate(a.x,a.y,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.atan2(b.y-a.y,b.x-a.x)));g.fill(0,0,Math.max(1,Math.round(a.distance(b))),1,color);g.pose().popPose();}
    void draw(GuiGraphics g){
        if(view.simulation)return;
        if(editing!=null&&(view.selection.size()!=1||!view.selection.contains(editing)||!editing.visible))editing=null;
        for(var e:view.selection)outline(g,e,e==editing?0xff55ff55:0xffe9e456);
        if(editing!=null)for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if(x!=0||y!=0){var p=handlePoint(editing,x,y);g.fill(Math.round(p.x)-2,Math.round(p.y)-2,Math.round(p.x)+2,Math.round(p.y)+2,0xff55ff55);}
        if(guideX!=null){var p=screen(new Vector2f(guideX,0));line(g,new Vector2f(p.x,view.stage.getPosition().y),new Vector2f(p.x,view.stage.getPosition().y+view.stage.getSize().height),0xffff66ff);}
        if(guideY!=null){var p=screen(new Vector2f(0,guideY));line(g,new Vector2f(view.stage.getPosition().x,p.y),new Vector2f(view.stage.getPosition().x+view.stage.getSize().width,p.y),0xffff66ff);}
    }
}
