package com.pureneko.vjce.client.canvas;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.mojang.math.Axis;
import com.pureneko.vjce.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Persistent presentation tree. LDLib widgets host this tree; JEI uses exactly the same renderer. */
public class CanvasElement {
    public String type = "group", id = "", text = "", texture = "";
    public float x, y, width = 18, height = 18, rotation, scaleX = 1, scaleY = 1, shearX, opacity = 1, progress;
    public int color = 0xffffffff, background;
    public boolean visible = true, clip, hitTest = true;
    private CanvasElement parent;
    private final List<CanvasElement> children = new ArrayList<>();
    private final List<Runnable> tickListeners = new ArrayList<>();
    private final Map<String,Runnable> namedTickListeners = new LinkedHashMap<>();
    private boolean hovering;

    public CanvasElement() {}
    public CanvasElement(String type) { this.type = type; }
    public CanvasElement setId(String id) { this.id = id; return this; }
    public String getId() { return id; }
    public CanvasElement getParent() { return parent; }
    public List<CanvasElement> getChildren() { return Collections.unmodifiableList(children); }
    public CanvasElement addChild(CanvasElement child) {
        return insertChild(children.size(),child);
    }
    public CanvasElement insertChild(int index,CanvasElement child) {
        if (child == this || child.select("*").anyMatch(e -> e == this)) throw new IllegalArgumentException("Canvas cycle");
        child.removeSelf(); children.add(Math.min(Math.max(0,index),children.size()),child); child.parent = this; return this;
    }
    public CanvasElement addChildren(CanvasElement... elements) { for (var e : elements) addChild(e); return this; }
    public void detach() { if (parent != null) parent.children.remove(this); parent = null; }
    public void removeSelf() { detach(); }
    public void dispose() { for (var child : List.copyOf(children)) child.dispose(); onRemoved(); }
    protected void onRemoved() {}
    public Stream<CanvasElement> select(String ignored) { return Stream.concat(Stream.of(this), children.stream().flatMap(c -> c.select("*"))); }
    public Stream<CanvasElement> selectId(String id) { return select("*").filter(e -> e.id.equals(id)); }
    public <T extends CanvasElement> Stream<T> selectId(String id, Class<T> type) { return selectId(id).filter(type::isInstance).map(type::cast); }
    public float getContentX() { return x + (parent == null ? 0 : parent.getContentX()); }
    public float getContentY() { return y + (parent == null ? 0 : parent.getContentY()); }
    public float getContentWidth() { return width; }
    public float getContentHeight() { return height; }
    public boolean isDisplayed() { return visible; }
    public boolean isVisible() { return visible; }
    public boolean isHover() { return hovering; }
    public void setDisplay(boolean display) { visible = display; }
    public void setAllowHitTest(boolean allow) { hitTest = allow; }
    public void setOverflowVisible(boolean overflow) { clip = !overflow; }
    public void onTick(Runnable listener) { tickListeners.add(listener); }
    public void onTick(String key,Runnable listener){namedTickListeners.put(key,listener);}
    public void screenTick() { for(var listener:List.copyOf(namedTickListeners.values()))listener.run();for (var listener : List.copyOf(tickListeners)) listener.run(); for (var c : List.copyOf(children)) c.screenTick(); }
    public CanvasElement layout(Consumer<Placement> layout) { layout.accept(new Placement()); return this; }
    public final class Placement {
        public void left(float value) { x = value; }
        public void top(float value) { y = value; }
        public void width(float value) { width = Math.max(.01f, value); }
        public void height(float value) { height = Math.max(.01f, value); }
        public void leftPercent(float v) { x = v * (parent == null ? 1 : parent.width) / 100; }
        public void topPercent(float v) { y = v * (parent == null ? 1 : parent.height) / 100; }
        public void widthPercent(float v) { width = v * (parent == null ? 1 : parent.width) / 100; }
        public void heightPercent(float v) { height = v * (parent == null ? 1 : parent.height) / 100; }
    }
    public Matrix4f localMatrix() {
        return new Matrix4f().translate(x + width / 2, y + height / 2, 0)
                .rotateZ((float) Math.toRadians(rotation)).scale(scaleX, scaleY, 1).mul(new Matrix4f().m10(shearX)).translate(-width / 2, -height / 2, 0);
    }
    public Matrix4f worldMatrix() { return parent == null ? localMatrix() : parent.worldMatrix().mul(localMatrix()); }
    public boolean isMouseOverElement(double mx, double my) {
        if (!visible || !hitTest) return false;
        for(var ancestor=parent;ancestor!=null;ancestor=ancestor.parent){
            if(!ancestor.visible||ancestor.opacity<=0)return false;
            if(ancestor.clip){var point=ancestor.worldMatrix().invert().transformPosition(new Vector3f((float)mx,(float)my,0));if(point.x<0||point.y<0||point.x>=ancestor.width||point.y>=ancestor.height)return false;}
        }
        var local = worldMatrix().invert().transformPosition(new Vector3f((float) mx, (float) my, 0));
        return local.x >= 0 && local.y >= 0 && local.x < width && local.y < height;
    }
    public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
        if (!visible || opacity <= 0) return;
        var opacityLayer=CanvasOpacity.begin(graphics,opacity);
        graphics.flush(); graphics.pose().pushPose();
        var m = localMatrix(); graphics.pose().mulPoseMatrix(m);
        var mouse = new Matrix4f(m).invert().transformPosition(new Vector3f((float) mouseX, (float) mouseY, 0));
        hovering = mouse.x >= 0 && mouse.y >= 0 && mouse.x < width && mouse.y < height;
        boolean clipped = false;
        try {
            if (clip) {
                var pose = graphics.pose().last().pose();
                float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
                for (var v : List.of(new Vector3f(), new Vector3f(width, 0, 0), new Vector3f(0, height, 0), new Vector3f(width, height, 0))) {
                    pose.transformPosition(v); minX = Math.min(minX,v.x); minY = Math.min(minY,v.y); maxX = Math.max(maxX,v.x); maxY = Math.max(maxY,v.y);
                }
                graphics.enableScissor((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX),(int)Math.ceil(maxY)); clipped = true;
            }
            drawBackgroundAdditional(new CanvasContext(graphics, mouse.x, mouse.y, partialTick));
            for (var child : List.copyOf(children)) child.render(graphics, mouse.x, mouse.y, partialTick);
            graphics.flush();
        } finally { if (clipped) graphics.disableScissor(); graphics.pose().popPose(); if(opacityLayer!=null)opacityLayer.close(); }
    }
    public void drawBackgroundAdditional(CanvasContext c) {
        var g = c.graphics;
        if (background != 0) g.fill(0,0,Math.round(width),Math.round(height),background);
        switch (type) {
            case "text" -> g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
            case "progress" -> { g.fill(0,0,Math.round(width),Math.round(height),0xff30343c); g.fill(0,0,Math.round(width * Math.max(0,Math.min(1,progress))),Math.round(height),color); }
            case "texture" -> {
                var location = ResourceLocation.tryParse(texture);
                if (location != null) g.blit(location,0,0,0,0,Math.round(width),Math.round(height),Math.round(width),Math.round(height));
            }
            default -> {}
        }
    }
    public CompoundTag serialize() {
        var t = new CompoundTag(); t.putString("type",type); t.putString("id",id); t.putString("text",text); t.putString("texture",texture);
        t.putFloat("x",x); t.putFloat("y",y); t.putFloat("width",width); t.putFloat("height",height); t.putFloat("rotation",rotation);
        t.putFloat("scaleX",scaleX); t.putFloat("scaleY",scaleY); t.putFloat("shearX",shearX); t.putFloat("opacity",opacity); t.putFloat("progress",progress);
        t.putInt("color",color); t.putInt("background",background); t.putBoolean("visible",visible); t.putBoolean("clip",clip); t.putBoolean("hitTest",hitTest);
        var data = new CompoundTag();
        try {
            for (var f : getClass().getFields()) if (f.isAnnotationPresent(Persisted.class) && !Modifier.isStatic(f.getModifiers())) {
                Object v = f.get(this); if (v instanceof Tag tag) data.put(f.getName(),tag.copy());
                else if (v instanceof Boolean b) data.putBoolean(f.getName(),b);
                else if (v instanceof Number n) data.putDouble(f.getName(),n.doubleValue());
                else if (v != null) data.putString(f.getName(),v.toString());
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot save canvas",error); }
        t.put("data",data); var list = new ListTag(); children.forEach(c -> list.add(c.serialize())); t.put("children",list); return t;
    }
    @SuppressWarnings({"unchecked","rawtypes"}) public static CanvasElement deserialize(CompoundTag t) { return deserialize(t,0,new int[]{0}); }
    @SuppressWarnings({"unchecked","rawtypes"}) private static CanvasElement deserialize(CompoundTag t,int depth,int[] count) {
        if(depth>128||++count[0]>4096)throw new IllegalArgumentException("Canvas hierarchy exceeds limits");
        CanvasElement e = switch(t.getString("type")) {
            case "texture" -> new TextureElement(); case "slot" -> new IngredientSlot(); case "native" -> new NativePartElement();
            case "scene" -> new BlockSceneElement(); case "create" -> new CreateAnimationElement(); default -> new CanvasElement(t.getString("type"));
        };
        e.id=t.getString("id"); e.text=t.getString("text"); e.texture=t.getString("texture"); e.x=t.getFloat("x"); e.y=t.getFloat("y");
        e.width=t.getFloat("width"); e.height=t.getFloat("height"); e.rotation=t.getFloat("rotation"); e.scaleX=t.getFloat("scaleX"); e.scaleY=t.getFloat("scaleY");
        e.shearX=t.getFloat("shearX");
        e.opacity=t.getFloat("opacity"); e.progress=t.getFloat("progress"); e.color=t.getInt("color"); e.background=t.getInt("background");
        e.visible=t.getBoolean("visible"); e.clip=t.getBoolean("clip"); e.hitTest=t.getBoolean("hitTest");
        if (!Float.isFinite(e.width)||!Float.isFinite(e.height)||e.width<=0||e.height<=0||e.width>8192||e.height>8192
                || !Float.isFinite(e.x)||!Float.isFinite(e.y)||!Float.isFinite(e.rotation)||!Float.isFinite(e.scaleX)||!Float.isFinite(e.scaleY)
                || !Float.isFinite(e.shearX)||!Float.isFinite(e.opacity)||!Float.isFinite(e.progress)||e.opacity<0||e.opacity>1
                || Math.abs(e.scaleX)<.0001f||Math.abs(e.scaleY)<.0001f) throw new IllegalArgumentException("Invalid canvas transform");
        var data=t.getCompound("data");
        try {
            for (var f:e.getClass().getFields()) if(f.isAnnotationPresent(Persisted.class)&&data.contains(f.getName())) {
                String k=f.getName(); Class<?> ft=f.getType();
                if(ft==CompoundTag.class)f.set(e,data.getCompound(k).copy()); else if(ft==String.class)f.set(e,data.getString(k));
                else if(ft==boolean.class)f.setBoolean(e,data.getBoolean(k)); else if(ft==int.class)f.setInt(e,(int)data.getDouble(k));
                else if(ft==float.class)f.setFloat(e,(float)data.getDouble(k)); else if(ft==double.class)f.setDouble(e,data.getDouble(k));
                else if(ft.isEnum())f.set(e,Enum.valueOf((Class)ft,data.getString(k)));
            }
        } catch(ReflectiveOperationException error){throw new IllegalArgumentException("Cannot load canvas",error);}
        if(e instanceof TextureElement texture)texture.load(t);
        var children=t.getList("children",Tag.TAG_COMPOUND); if(children.size()>4096)throw new IllegalArgumentException("Too many canvas children");
        for(var child:children)e.addChild(deserialize((CompoundTag)child,depth+1,count)); return e;
    }
}
