package com.pureneko.vjce.client;
import com.pureneko.vjce.client.canvas.*;
import java.util.*;
final class RecipeCanvasSelection {
    static List<CanvasElement> hits(CanvasUI ui,float x,float y,boolean deep){var result=new ArrayList<CanvasElement>();visit(ui.rootElement,x,y,result,deep,true);return result;}
    private static void visit(CanvasElement e,float x,float y,List<CanvasElement> out,boolean deep,boolean root){if(!e.visible||e.opacity<=0||e.clip&&!e.isMouseOverElement(x,y))return;var children=e.getChildren();for(int i=children.size()-1;i>=0;i--)visit(children.get(i),x,y,out,deep,false);if(!root&&e.isMouseOverElement(x,y)){if(!deep&&e.type.equals("group")){out.removeIf(c->{for(var p=c.getParent();p!=null;p=p.getParent())if(p==e)return true;return false;});}out.add(e);}}
}
