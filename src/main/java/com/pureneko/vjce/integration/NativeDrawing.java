package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.pureneko.vjce.client.NativePartElement;
import com.pureneko.vjce.data.CategoryDocument;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Live source drawing, captured at stable call sites rather than flattening an animated page to a bitmap. */
public final class NativeDrawing {
    public record Piece(String key,String label,float x,float y,float width,float height,boolean component) {}
    private static final ThreadLocal<Frame> ACTIVE=new ThreadLocal<>();
    public final Set<String> unstable=new HashSet<>();
    public final Map<String,Piece> pieces=new LinkedHashMap<>();
    public final Map<String,net.minecraft.network.chat.Component> tooltips=new HashMap<>();
    public String componentKey(Object object){return components.get(object);}
    private final Map<Object,String> components=new IdentityHashMap<>();
    private final Map<String,String> parents=new HashMap<>();
    private final Map<UIElement,Set<String>> seen=new WeakHashMap<>();
    public Object componentObject(String key){return components.entrySet().stream().filter(e->e.getValue().equals(key)).map(Map.Entry::getKey).findFirst().orElse(null);}
    private final Set<UIElement> bound=Collections.newSetFromMap(new WeakHashMap<>());
    public void component(Object object,String key,String label,float x,float y,float w,float h,String parent) {
        if(parent!=null)parents.put(key,parent);
        components.put(object,key);pieces.putIfAbsent(key,new Piece(key,label,x,y,Math.max(1,w),Math.max(1,h),true));
    }
    public void bind(UI ui,CategoryDocument document,boolean runtime) {
        var root=ui.rootElement;
        root.select("*").filter(NativePartElement.class::isInstance).map(NativePartElement.class::cast).filter(e->e.part.startsWith("draw_")).forEach(e->pieces.putIfAbsent(e.part,new Piece(e.part,e.sourceLabel,e.sourceX,e.sourceY,e.sourceWidth,e.sourceHeight,e.part.startsWith("draw_component_"))));
        seen.computeIfAbsent(root,k->document.layout.isBlank()?new HashSet<>():new HashSet<>(document.nativeOriginElements));
        if(bound.add(root))root.addEventListener(UIEvents.TICK,e->mount(root,document,runtime));
        mount(root,document,runtime);
    }
    public void populate(UIElement root){mount(root,null);}
    public boolean mount(UIElement root,CategoryDocument document) {
        return mount(root,document,false);
    }
    private boolean mount(UIElement root,CategoryDocument document,boolean runtime) {
        boolean changed=false;var known=document==null?new HashSet<String>():seen.computeIfAbsent(root,k->document.layout.isBlank()?new HashSet<>():new HashSet<>(document.nativeOriginElements));
        for(var p:List.copyOf(pieces.values())) {
            if(root.selectId(p.key).findAny().isPresent()){known.add(p.key);continue;}
            if(known.contains(p.key))continue;
            var element=new NativePartElement();element.part=p.key;element.setId(p.key);element.sourceLabel=p.label;
            element.sourceX=p.x;element.sourceY=p.y;element.sourceWidth=p.width;element.sourceHeight=p.height;
            element.layout(l->{l.positionType(TaffyPosition.ABSOLUTE);l.left(p.x);l.top(p.y);l.width(p.width);l.height(p.height);});
            var parentKey=parents.get(p.key);var host=parentKey==null?null:NativePage.part(root,parentKey);var parentPiece=parentKey==null?null:pieces.get(parentKey);
            if(host!=null&&parentPiece!=null){element.layout(l->{l.leftPercent((p.x-parentPiece.x)*100/parentPiece.width);l.topPercent((p.y-parentPiece.y)*100/parentPiece.height);l.widthPercent(p.width*100/parentPiece.width);l.heightPercent(p.height*100/parentPiece.height);});host.addChild(element);}else root.addChild(element);
            element.setOverflowVisible(true);known.add(p.key);if(!runtime&&document!=null&&!document.nativeOriginElements.contains(p.key))document.nativeOriginElements.add(p.key);changed=true;
        }
        return changed;
    }
    public Scope open(UIElement root,GuiGraphics g,NativePartElement nativeLayer,double mouseX,double mouseY) {
        var old=ACTIVE.get();var frame=new Frame(this,root,g,nativeLayer,mouseX,mouseY);ACTIVE.set(frame);return ()->{frame.close();if(old==null)ACTIVE.remove();else ACTIVE.set(old);};
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    private record Token(boolean pushed,NativePartElement previous,Matrix4f basis) {}
    private static final class Frame {
        final NativeDrawing drawing;final UIElement root;final GuiGraphics g;final Matrix4f base,inverse,world;Matrix4f basis;
        final Vector2f mouseRoot;
        final Map<String,Integer> counts=new HashMap<>();final Deque<Token> tokens=new ArrayDeque<>();NativePartElement current;int depth;
        Frame(NativeDrawing drawing,UIElement root,GuiGraphics g,NativePartElement layer,double mouseX,double mouseY) {
            this.drawing=drawing;this.root=root;this.g=g;base=new Matrix4f(g.pose().last().pose());inverse=new Matrix4f(base).invert();basis=base;
            mouseRoot=new ElementTransform(root,layer,Math.max(1,layer.sourceWidth),Math.max(1,layer.sourceHeight)).world((float)mouseX,(float)mouseY);
            world=new Matrix4f(base).mul(new ElementTransform(root,layer,Math.max(1,layer.sourceWidth),Math.max(1,layer.sourceHeight)).matrix().invert());
        }
        void close(){while(!tokens.isEmpty())end();}
        void begin(String key,String label,float x1,float y1,float x2,float y2,boolean component) {
            if(depth++>0&&!component) {tokens.push(new Token(false,current,basis));return;}
            Matrix4f relative=new Matrix4f(basis).invert().mul(g.pose().last().pose());
            var a=relative.transformPosition(new Vector3f(x1,y1,0));var b=relative.transformPosition(new Vector3f(x2,y1,0));var c=relative.transformPosition(new Vector3f(x2,y2,0));var d=relative.transformPosition(new Vector3f(x1,y2,0));
            float x=Math.min(Math.min(a.x,b.x),Math.min(c.x,d.x)),y=Math.min(Math.min(a.y,b.y),Math.min(c.y,d.y));
            float w=Math.max(1,Math.max(Math.max(a.x,b.x),Math.max(c.x,d.x))-x),h=Math.max(1,Math.max(Math.max(a.y,b.y),Math.max(c.y,d.y))-y);
            var piece=drawing.pieces.computeIfAbsent(key,k->new Piece(k,label,x,y,w,h,component));
            if(!component && (Math.abs(piece.x-x)>.51f || Math.abs(piece.y-y)>.51f))drawing.unstable.add(key);
            var element=NativePage.part(root,key);
            boolean hidden=element==null ? drawing.seen.getOrDefault(root,Set.of()).contains(key)
                    : !NativePage.visible(element)||!element.useSourceDrawing;
            if(drawing.unstable.contains(key)) element=null;
            tokens.push(new Token(element!=null||hidden,current,basis));current=element;
            if(element!=null||hidden) {
                g.flush();g.pose().pushPose();
                if(hidden)g.pose().scale(0,0,1);
                else {basis=new Matrix4f(world).mul(new ElementTransform(root,element,piece.width,piece.height).matrix()).translate(-piece.x,-piece.y,0);g.pose().last().pose().set(new Matrix4f(basis).mul(relative));}
            }
        }
        void end(){if(tokens.isEmpty())return;var token=tokens.pop();if(token.pushed){g.flush();g.pose().popPose();}current=token.previous;basis=token.basis;depth--;}
    }
    public static void begin(GuiGraphics g,String kind,float x1,float y1,float x2,float y2) {
        var f=ACTIVE.get();if(f==null)return;
        if(f.depth>0){f.depth++;f.tokens.push(new Token(false,f.current,f.basis));return;}
        String site=StackWalker.getInstance().walk(stream->stream.takeWhile(s->!s.getClassName().equals("com.pureneko.vjce.integration.NativePage")).filter(s->!s.getClassName().equals(NativeDrawing.class.getName())&&!s.getClassName().startsWith("com.pureneko.vjce.mixin.")&&!s.getClassName().equals("net.minecraft.client.gui.GuiGraphics")&&!s.getClassName().startsWith("org.spongepowered.")).limit(7).map(s->s.getClassName()+"."+s.getMethodName()+":"+s.getLineNumber()).reduce("",(a,b)->a+"|"+b));
        String id=kind+"_"+Long.toUnsignedString(hash(site),36);int index=f.counts.merge(id,1,Integer::sum)-1;
        f.begin("draw_"+id+"_"+index,kind+" · "+site.split("\\|")[1],x1,y1,x2,y2,false);
    }
    private static long hash(String value){long h=0xcbf29ce484222325L;for(char c:value.toCharArray()){h^=c;h*=0x100000001b3L;}return h;}
    public static void end(){var f=ACTIVE.get();if(f!=null)f.end();}
    public static boolean active(){return ACTIVE.get()!=null;}
    public static void component(Object object,GuiGraphics g,float x1,float y1,float x2,float y2,String label) {
        var f=ACTIVE.get();if(f==null)return;
        String key=f.drawing.components.get(object);
        if(key==null){String id=object.getClass().getName();int index=f.counts.merge("component:"+id,1,Integer::sum)-1;key="draw_component_"+Long.toUnsignedString(hash(id),36)+"_"+index;}
        f.begin(key,label,x1,y1,x2,y2,true);
    }
    public Vector2f sourcePoint(UIElement root,double x,double y,double fallbackX,double fallbackY) {
        var candidates=new ArrayList<>(pieces.values());Collections.reverse(candidates);
        for(var p:candidates)if(p.key.startsWith("draw_component_mek_")) {
            var e=NativePage.part(root,p.key);if(e==null||!NativePage.visible(e)||!e.useSourceDrawing)continue;
            var t=new ElementTransform(root,e,p.width,p.height);
            if(t.contains(x,y)){var point=t.local(x,y);return point.add(p.x,p.y);}
        }
        for(var p:candidates)if(p.key.startsWith("draw_component_mek_")&&x>=p.x&&y>=p.y&&x<p.x+p.width&&y<p.y+p.height)return new Vector2f(-100000,-100000);
        return new Vector2f((float)fallbackX,(float)fallbackY);
    }
    public static Vector2f componentMouse(double x,double y) {
        var f=ACTIVE.get();if(f==null||f.current==null)return new Vector2f((float)x,(float)y);
        var p=f.drawing.pieces.get(f.current.part);if(p==null||!p.key.startsWith("draw_component_mek_"))return new Vector2f((float)x,(float)y);
        var t=new ElementTransform(f.root,f.current,p.width,p.height);
        if(!t.contains(f.mouseRoot.x,f.mouseRoot.y))return new Vector2f(-100000,-100000);
        return t.local(f.mouseRoot.x,f.mouseRoot.y).add(p.x,p.y);
    }
    public static void tooltip(net.minecraft.network.chat.Component message) {
        var f=ACTIVE.get();if(f!=null&&f.current!=null){if(message==null)f.drawing.tooltips.remove(f.current.part);else f.drawing.tooltips.put(f.current.part,message);}
    }
    public static String text(String source){var f=ACTIVE.get();return f==null||f.current==null||f.current.replacementText.isEmpty()?source:f.current.replacementText;}
    public static net.minecraft.util.FormattedCharSequence text(net.minecraft.util.FormattedCharSequence source){var f=ACTIVE.get();return f==null||f.current==null||f.current.replacementText.isEmpty()?source:net.minecraft.network.chat.Component.literal(f.current.replacementText).getVisualOrderText();}
    public static int color(int source){var f=ACTIVE.get();return f==null||f.current==null||!f.current.overrideTextColor?source:f.current.textColor;}
    public static ResourceLocation texture(ResourceLocation original) {
        var f=ACTIVE.get();if(f==null||f.current==null||f.current.replacementTexture.isBlank())return original;
        var id=ResourceLocation.tryParse(f.current.replacementTexture);return id==null?original:id;
    }
}
