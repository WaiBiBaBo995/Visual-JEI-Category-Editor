package com.pureneko.vjce.client;

import com.pureneko.vjce.client.canvas.CanvasElement;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

final class CanvasGrouping {
    static List<CanvasElement> topLevel(Collection<CanvasElement> elements) {
        return elements.stream().filter(e -> {
            for (var p=e.getParent();p!=null;p=p.getParent()) if(elements.contains(p)) return false;
            return true;
        }).toList();
    }

    static void group(RecipeLayoutView view) {
        var selected=topLevel(view.selection);
        if(selected.size()<2)return;
        var parent=selected.get(0).getParent();
        if(parent==null||selected.stream().anyMatch(e->e.getParent()!=parent))return;
        var items=parent.getChildren().stream().filter(selected::contains).toList();
        int insertion=parent.getChildren().indexOf(items.get(0));
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(var e:items) for(var corner:List.of(new Vector3f(),new Vector3f(e.width,0,0),new Vector3f(0,e.height,0),new Vector3f(e.width,e.height,0))) {
            e.localMatrix().transformPosition(corner);
            minX=Math.min(minX,corner.x);minY=Math.min(minY,corner.y);
            maxX=Math.max(maxX,corner.x);maxY=Math.max(maxY,corner.y);
        }
        var group=new CanvasElement("group");
        group.id="group_"+Long.toUnsignedString(System.nanoTime(),36);
        group.x=minX;group.y=minY;group.width=Math.max(1,maxX-minX);group.height=Math.max(1,maxY-minY);
        parent.insertChild(insertion,group);
        for(var e:items){e.x-=minX;e.y-=minY;group.addChild(e);}
        view.focusElement(group);view.markAsDirty();
    }

    private record Placement(CanvasElement element,CanvasElement parent,int index,float x,float y,float angle,float sx,float sy,float shear) {
        static Placement of(CanvasElement e,CanvasElement parent,int index,Matrix4f matrix) {
            float a=matrix.m00(),b=matrix.m01(),c=matrix.m10(),d=matrix.m11();
            float sx=(float)Math.hypot(a,b),sy=(a*d-b*c)/sx;
            float shear=(a*c+b*d)/(sx*sx);
            if(!Float.isFinite(shear)||!Float.isFinite(sx)||!Float.isFinite(sy)||Math.abs(sx)<.0001f||Math.abs(sy)<.0001f)
                throw new IllegalArgumentException("Invalid group transform");
            var center=matrix.transformPosition(new Vector3f(e.width/2,e.height/2,0));
            return new Placement(e,parent,index,center.x-e.width/2,center.y-e.height/2,(float)Math.toDegrees(Math.atan2(b,a)),sx,sy,shear);
        }
        void apply(){element.x=x;element.y=y;element.rotation=angle;element.scaleX=sx;element.scaleY=sy;element.shearX=shear;parent.insertChild(index,element);}
    }
    static void ungroup(RecipeLayoutView view) {
        var groups=topLevel(view.selection).stream().filter(e->e.type.equals("group")&&e.getParent()!=null).toList();
        if(groups.isEmpty())return;
        // Validate every decomposition before changing the tree.
        for(var group:groups)for(var e:group.getChildren())Placement.of(e,group.getParent(),0,group.localMatrix().mul(e.localMatrix()));
        view.selection.clear();
        for(var group:groups){
            var parent=group.getParent();int index=parent.getChildren().indexOf(group);
            for(var e:List.copyOf(group.getChildren())){Placement.of(e,parent,index++,group.localMatrix().mul(e.localMatrix())).apply();view.selection.add(e);}
            group.detach();
        }
        view.inspect();view.markAsDirty();
    }
}
