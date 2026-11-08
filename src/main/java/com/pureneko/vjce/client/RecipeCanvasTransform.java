package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.configurator.SerializableRecordAction;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.event.*;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Property;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.pureneko.vjce.data.CategoryDocument;
import dev.vfyjxf.taffy.style.*;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.numeric.FloatOptional;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import java.lang.Math;
import java.util.*;

/** Canvas gestures own only inline layout/transform values, never replace the live widget tree. */
final class RecipeCanvasTransform {
    private final RecipeLayoutView view;
    private final CategoryDocument document;
    private UIElement editing;
    private UIElement host;
    private final UIEventListener outsideClick = this::outsideClick;
    private Gesture gesture;
    private final List<Vector2f[]> guides = new ArrayList<>();
    private final UIElement overlay = new UIElement() {
        @Override public void drawBackgroundAdditional(GUIContext context) { draw(context); }
    };
    private record Handle(int x, int y) {}
    private record Snapshot(Map<Property<?>, Object> layout, Transform2D transform, int width, int height) {}
    private static final class Gesture {
        UIElement element;
        Snapshot before;
        Transform2D transform;
        Matrix4f parentInverse, elementInverse;
        Vector2f start, localStart, center;
        float x, y, w, h, angle, lastAngle, accumulated;
        Handle handle;
        boolean rotate, changed;
    }
    RecipeCanvasTransform(RecipeLayoutView view, CategoryDocument document) {
        this.view = view; this.document = document;
        // A child input surface keeps GraphView's at-target pan handler from replacing a gesture.
        overlay.setId("vjce_transform_overlay");
        overlay.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(0); l.top(0); l.widthPercent(100); l.heightPercent(100); });
        view.graphView.addChild(overlay);
        overlay.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (!view.canvasInputAllowed()) return;
            var forwarded = UIEvent.create(UIEvents.MOUSE_WHEEL); forwarded.target = view.graphView;
            forwarded.x = event.x; forwarded.y = event.y; forwarded.deltaX = event.deltaX; forwarded.deltaY = event.deltaY;
            UIEventDispatcher.dispatchDirectEvent(forwarded, false); event.stopImmediatePropagation();
        });
        view.graphView.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::update, true);
        view.graphView.addEventListener(UIEvents.DRAG_END, event -> finish(false), true);
        view.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (view.canvasInputAllowed() && event.keyCode == GLFW.GLFW_KEY_ESCAPE && escape()) event.stopImmediatePropagation();
        }, true);
        view.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (!view.graphView.isAncestorOf(event.target) && event.target != view.graphView) editing = null;
        }, true);
    }
    boolean isEditing() { return editing != null; }
    private void outsideClick(UIEvent event) {
        if (event.target != view.graphView && !view.graphView.isAncestorOf(event.target)) editing = null;
    }
    void attach() {
        detach(); host = view.getModularUI().ui.rootElement;
        host.addEventListener(UIEvents.MOUSE_DOWN, outsideClick, true);
    }
    void detach() {
        if (host != null) host.removeEventListener(UIEvents.MOUSE_DOWN, outsideClick, true);
        host = null;
    }
    boolean escape() {
        if (gesture == null && editing == null) return false;
        if (gesture != null) cancel(); else editing = null;
        return true;
    }
    boolean hasGuides() { return !guides.isEmpty(); }
    Vector2f world(UIEvent event) {
        var p = view.getModularUI().getLastDrawPose().transformPosition(new Vector3f(event.x, event.y, 0), new Vector3f());
        return new Vector2f(p.x, p.y);
    }
    boolean intercept(UIEvent event, Vector2f mouse) {
        if (editing == null) return false;
        if (view.hierarchy.getSelectedOne().orElse(null) != editing || !editing.isDisplayed()) { editing = null; return false; }
        var handle = hitHandle(editing, mouse);
        boolean inside = editing.isMouseOverElement(mouse.x, mouse.y);
        if (event.isAltDown() && (inside || handle != null) && editing != root()) {
            begin(editing, mouse, null, true); return true;
        }
        if (handle != null) { begin(editing, mouse, handle, false); return true; }
        if (!inside) editing = null;
        return false;
    }
    void picked(UIElement element, UIEvent event, Vector2f mouse) {
        if (event.isCtrlDown()) editing = element;
        else if (editing != element) editing = null;
        if (!event.isCtrlDown() && event.isShiftDown() && element != root()) begin(element, mouse, null, false);
    }
    void clear() { cancel(); editing = null; guides.clear(); }
    private UIElement root() { return view.getCurrentUI().rootElement; }
    private void begin(UIElement element, Vector2f mouse, Handle handle, boolean rotate) {
        cancel();
        var g = new Gesture(); g.element = element; g.before = snapshot(element); g.transform = element.getStyle().transform2D().copy();
        var parent = element.getParent();
        g.parentInverse = new Matrix4f(parent == null ? element.getLocalToWorldPose() : parent.getLocalToWorldPose()).invert();
        g.elementInverse = new Matrix4f(element.getLocalToWorldPose()).invert();
        g.start = point(g.parentInverse, mouse); g.localStart = point(g.elementInverse, mouse);
        g.x = element.getPositionX(); g.y = element.getPositionY(); g.w = element.getSizeWidth(); g.h = element.getSizeHeight();
        g.center = point(g.parentInverse, point(new Matrix4f(element.getLocalToWorldPose()), new Vector2f(g.x + g.w / 2, g.y + g.h / 2)));
        g.angle = g.transform.rotation(); g.lastAngle = (float) Math.atan2(g.start.y - g.center.y, g.start.x - g.center.x);
        g.handle = handle; g.rotate = rotate; gesture = g;
        view.graphView.startDrag(g, null);
    }
    private void update(UIEvent event) {
        if (!view.canvasInputAllowed()) { clear(); return; }
        var g = gesture;
        if (g == null || event.dragHandler.draggingObject != g) return;
        var mouse = world(event); guides.clear();
        if (mouse.distanceSquared(point(new Matrix4f(g.parentInverse).invert(), g.start)) < 0.25f && !g.changed) return;
        g.changed = true;
        if (g.rotate) {
            var parentMouse = point(g.parentInverse, mouse);
            float a = (float) Math.atan2(parentMouse.y - g.center.y, parentMouse.x - g.center.x);
            float delta = a - g.lastAngle;
            while (delta > Math.PI) delta -= (float) (2 * Math.PI);
            while (delta < -Math.PI) delta += (float) (2 * Math.PI);
            g.accumulated += (float) Math.toDegrees(delta); g.lastAngle = a;
            float angle = g.angle + g.accumulated;
            if (UIElement.isShiftDown()) angle = Math.round(angle / 15) * 15;
            var t = g.transform.copy().pivot(0.5f, 0.5f).rotation(angle);
            // Changing a custom pivot must not move the element's visible centre.
            var oldCenter = transformed(g.transform, g.x, g.y, g.w, g.h, .5f, .5f);
            var newCenter = transformed(t, g.x, g.y, g.w, g.h, .5f, .5f);
            place(g, g.x + oldCenter.x - newCenter.x, g.y + oldCenter.y - newCenter.y, g.w, g.h);
            g.element.getStyle().transform2D(t);
        } else if (g.handle == null) {
            var delta = point(g.parentInverse, mouse).sub(g.start);
            float x = g.x + delta.x, y = g.y + delta.y;
            if (!UIElement.isAltDown()) {
                var box = gestureBounds(g, x, y, g.w, g.h);
                var correction = snap(g, box, null);
                var local = new Matrix4f(g.parentInverse).transformDirection(new Vector3f(correction.x, correction.y, 0));
                x += local.x; y += local.y;
            }
            place(g, x, y, g.w, g.h);
        } else {
            var delta = point(g.elementInverse, mouse).sub(g.localStart);
            int hx = g.handle.x, hy = g.handle.y;
            float w = Math.max(1, g.w + hx * delta.x), h = Math.max(1, g.h + hy * delta.y);
            boolean proportional = UIElement.isCtrlDown() && hx != 0 && hy != 0;
            if (proportional) {
                float ratio = Math.abs(w / g.w - 1) >= Math.abs(h / g.h - 1) ? w / g.w : h / g.h;
                ratio = Math.max(Math.max(1 / g.w, 1 / g.h), ratio); w = g.w * ratio; h = g.h * ratio;
            }
            float ax = (1 - hx) / 2f, ay = (1 - hy) / 2f;
            var anchor = transformed(g.transform, g.x, g.y, g.w, g.h, ax, ay);
            var current = transformed(g.transform, 0, 0, w, h, ax, ay);
            float x = anchor.x - current.x, y = anchor.y - current.y;
            if (g.element == root()) { x = g.x; y = g.y; }
            if (!UIElement.isAltDown()) {
                // Solve the nearest guide along the dimensions allowed by this handle.
                var box = gestureBounds(g, x, y, w, h);
                float eps = .25f;
                var wx = resizeBox(g, anchor, ax, ay, w + eps, h);
                var hyBox = resizeBox(g, anchor, ax, ay, w, h + eps);
                float[] base = features(box), bx = features(wx), by = features(hyBox);
                var movable = new boolean[6];
                for (int i = 0; i < 6; i++) movable[i] = (hx != 0 && Math.abs(bx[i] - base[i]) > .001f) || (hy != 0 && Math.abs(by[i] - base[i]) > .001f);
                var correction = snap(g, box, movable);
                float dw = 0, dh = 0;
                if (proportional) {
                    float sx = (bx[snapX] - base[snapX]) / eps * w + (by[snapX] - base[snapX]) / eps * h;
                    float sy = (bx[3 + snapY] - base[3 + snapY]) / eps * w + (by[3 + snapY] - base[3 + snapY]) / eps * h;
                    float ratio = correction.x != 0 && Math.abs(sx) > .001f ? correction.x / sx : correction.y != 0 && Math.abs(sy) > .001f ? correction.y / sy : 0;
                    dw = w * ratio; dh = h * ratio;
                } else {
                    float a = hx == 0 ? 0 : (bx[snapX] - base[snapX]) / eps, b = hy == 0 ? 0 : (by[snapX] - base[snapX]) / eps;
                    float c = hx == 0 ? 0 : (bx[3 + snapY] - base[3 + snapY]) / eps, d = hy == 0 ? 0 : (by[3 + snapY] - base[3 + snapY]) / eps;
                    if (correction.x != 0 || correction.y != 0) {
                        float det = a * d - b * c;
                        if (Math.abs(det) > .001f && guideX && guideY) { dw = (correction.x * d - b * correction.y) / det; dh = (a * correction.y - correction.x * c) / det; }
                        else {
                            float norm = guideX ? a * a + b * b : c * c + d * d;
                            if (norm > .001f) { dw = (guideX ? a * correction.x : c * correction.y) / norm; dh = (guideX ? b * correction.x : d * correction.y) / norm; }
                        }
                    }
                }
                w = Math.max(1, w + dw); h = Math.max(1, h + dh);
                current = transformed(g.transform, 0, 0, w, h, ax, ay); x = anchor.x - current.x; y = anchor.y - current.y;
                if (g.element == root()) { x = g.x; y = g.y; }
            }
            place(g, x, y, w, h);
        }
        event.stopImmediatePropagation();
    }
    private Vector4f resizeBox(Gesture g, Vector2f anchor, float ax, float ay, float w, float h) {
        var p = transformed(g.transform, 0, 0, w, h, ax, ay);
        return gestureBounds(g, g.element == root() ? g.x : anchor.x - p.x, g.element == root() ? g.y : anchor.y - p.y, w, h);
    }
    private static Vector2f transformed(Transform2D t, float x, float y, float w, float h, float ax, float ay) {
        float px = t.pivot().x * w, py = t.pivot().y * h;
        float dx = (ax * w - px) * t.scale().x, dy = (ay * h - py) * t.scale().y;
        double a = Math.toRadians(t.rotation());
        return new Vector2f(x + t.translate().resolveX(w) + px + (float) (Math.cos(a) * dx - Math.sin(a) * dy),
                y + t.translate().resolveY(h) + py + (float) (Math.sin(a) * dx + Math.cos(a) * dy));
    }
    private void place(Gesture g, float x, float y, float w, float h) {
        var l = g.element.getLayout();
        if (g.element != root()) {
            l.positionType(TaffyPosition.ABSOLUTE); l.marginAll(0);
            l.marginLeft(0); l.marginRight(0); l.marginTop(0); l.marginBottom(0);
            l.setInline(LayoutProperties.RIGHT, LengthPercentageAuto.AUTO); l.setInline(LayoutProperties.BOTTOM, LengthPercentageAuto.AUTO);
            var p = g.element.getParent();
            l.left(x - p.getPositionX() - p.getTaffyLayout().border().left);
            l.top(y - p.getPositionY() - p.getTaffyLayout().border().top);
        }
        l.setInline(LayoutProperties.MIN_WIDTH, TaffyDimension.ZERO); l.setInline(LayoutProperties.MIN_HEIGHT, TaffyDimension.ZERO);
        l.setInline(LayoutProperties.MAX_WIDTH, TaffyDimension.auto()); l.setInline(LayoutProperties.MAX_HEIGHT, TaffyDimension.auto());
        l.setInline(LayoutProperties.ASPECT_RATE, FloatOptional.of());
        l.width(w); l.height(h);
        if (g.element == root()) { document.width = Math.clamp(Math.round(w), 18, 2048); document.height = Math.clamp(Math.round(h), 18, 2048); l.width(document.width); l.height(document.height); }
        root().clearPoseCache();
    }
    private int snapX, snapY;
    private boolean guideX, guideY;
    private Vector2f snap(Gesture g, Vector4f box, boolean[] movable) {
        float threshold = (float) (6 / net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float dx = threshold + 1, dy = threshold + 1, tx = 0, ty = 0;
        var candidates = new ArrayList<UIElement>();
        if (g.element.getParent() != null) candidates.addAll(g.element.getParent().getChildren());
        if (g.element != root()) candidates.add(root());
        float[] f = features(box); snapX = snapY = 0; guideX = guideY = false;
        for (var other : candidates) {
            if (other == g.element || !com.pureneko.vjce.integration.NativePage.visible(other) || other.isInternalUI()) continue;
            if (other instanceof NativePartElement part && !part.useSourceDrawing) continue;
            var target = bounds(other.getLocalToWorldPose(), other.getPositionX(), other.getPositionY(), other.getSizeWidth(), other.getSizeHeight());
            float[] t = features(target);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                if ((movable == null || movable[i]) && Math.abs(t[j] - f[i]) < Math.abs(dx)) { dx = t[j] - f[i]; tx = t[j]; snapX = i; }
                if ((movable == null || movable[3 + i]) && Math.abs(t[3 + j] - f[3 + i]) < Math.abs(dy)) { dy = t[3 + j] - f[3 + i]; ty = t[3 + j]; snapY = i; }
            }
        }
        guideX = Math.abs(dx) <= threshold; guideY = Math.abs(dy) <= threshold;
        if (guideX) guides.add(new Vector2f[]{new Vector2f(tx, box.y - 16), new Vector2f(tx, box.w + 16)});
        if (guideY) guides.add(new Vector2f[]{new Vector2f(box.x - 16, ty), new Vector2f(box.z + 16, ty)});
        return new Vector2f(guideX ? dx : 0, guideY ? dy : 0);
    }
    private static float[] features(Vector4f b) { return new float[]{b.x, (b.x + b.z) / 2, b.z, b.y, (b.y + b.w) / 2, b.w}; }
    private Vector4f gestureBounds(Gesture g, float x, float y, float w, float h) {
        var matrix = new Matrix4f(g.parentInverse).invert();
        var b = new Vector4f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (int ay = 0; ay <= 1; ay++) for (int ax = 0; ax <= 1; ax++) {
            var p = point(matrix, transformed(g.transform, x, y, w, h, ax, ay));
            b.x = Math.min(b.x, p.x); b.y = Math.min(b.y, p.y); b.z = Math.max(b.z, p.x); b.w = Math.max(b.w, p.y);
        }
        return b;
    }
    private static Vector4f bounds(Matrix4f matrix, float x, float y, float w, float h) {
        var b = new Vector4f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (var p : List.of(new Vector2f(x, y), new Vector2f(x + w, y), new Vector2f(x, y + h), new Vector2f(x + w, y + h))) {
            p = point(matrix, p); b.x = Math.min(b.x, p.x); b.y = Math.min(b.y, p.y); b.z = Math.max(b.z, p.x); b.w = Math.max(b.w, p.y);
        }
        return b;
    }
    private static Vector2f point(Matrix4f matrix, Vector2f p) { var v = matrix.transformPosition(new Vector3f(p.x, p.y, 0)); return new Vector2f(v.x, v.y); }
    private Handle hitHandle(UIElement e, Vector2f mouse) {
        float radius = (float) (6 / net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            if (handlePoint(e, x, y).distance(mouse) <= radius) return new Handle(x, y);
        }
        var local = e.getLocalMouse(mouse.x, mouse.y);
        var scale = e.getLocalToWorldPose().getScale(new Vector3f());
        float px = local.x - e.getPositionX(), py = local.y - e.getPositionY();
        boolean horizontal = px >= 0 && px <= e.getSizeWidth(), vertical = py >= 0 && py <= e.getSizeHeight();
        if (horizontal && Math.abs(py) * scale.y < radius) return new Handle(0, -1);
        if (horizontal && Math.abs(py - e.getSizeHeight()) * scale.y < radius) return new Handle(0, 1);
        if (vertical && Math.abs(px) * scale.x < radius) return new Handle(-1, 0);
        if (vertical && Math.abs(px - e.getSizeWidth()) * scale.x < radius) return new Handle(1, 0);
        return null;
    }
    static Vector2f handlePoint(UIElement e, int x, int y) { return e.getWorldMouse(e.getPositionX() + e.getSizeWidth() * (x + 1) / 2, e.getPositionY() + e.getSizeHeight() * (y + 1) / 2); }
    private Snapshot snapshot(UIElement e) {
        var values = new LinkedHashMap<Property<?>, Object>();
        for (var p : e.getLayout().getPropertiesList()) values.put(p, e.getLayout().getInline(p));
        var t = e.getStyle().getInline(PropertyRegistry.TRANSFORM_2D);
        return new Snapshot(values, t == null ? null : t.copy(), document.width, document.height);
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private void restore(UIElement e, Snapshot s) {
        s.layout.forEach((p, v) -> e.getLayout().setInline((Property) p, v));
        e.getStyle().setInline(PropertyRegistry.TRANSFORM_2D, s.transform == null ? null : s.transform.copy());
        document.width = s.width; document.height = s.height; root().clearPoseCache();
        refreshInspector(e);
    }
    private void finish(boolean cancelled) {
        var g = gesture; gesture = null; guides.clear();
        if (g == null) return;
        if (cancelled) restore(g.element, g.before);
        else if (g.changed) {
            var after = snapshot(g.element);
            if (!g.before.equals(after)) {
                // Use LDLib's own-state recorder so subsequent inspector snapshots can undo to this
                // gesture. External children and the live widget identities are retained in place.
                var action = SerializableRecordAction.of(g.element, UIElement::snapshotOwnState,
                        (e, tag) -> e.restoreOwnState((net.minecraft.nbt.CompoundTag) tag));
                action.setOnExecute(e -> restore(e, after)).setOnUndo(e -> restore(e, g.before));
                view.historyStack.pushHistory(Component.translatable(g.rotate ? "vjce.canvas.rotate" : g.handle == null ? "vjce.canvas.move" : "vjce.canvas.resize"), action, new Object(), false);
                view.markAsDirty(); refreshInspector(g.element);
            }
        }
    }
    private void refreshInspector(UIElement e) {
        var history = view.inspector.getHistoryStack();
        view.inspector.setHistoryStack(null);
        try { view.inspector.inspect(e); } finally { view.inspector.setHistoryStack(history); }
    }
    void cancel() {
        if (gesture == null) return;
        finish(true);
        if (view.getModularUI() != null) view.getModularUI().getDragHandler().stopDrag();
    }
    private void draw(GUIContext context) {
        if (view.isSimulationRunning() || view.getCurrentUI() == null) return;
        if (CanvasModalGuard.blocked(view.getModularUI())) {
            clear();
            var drag = view.getModularUI().getDragHandler();
            if (drag.dragSource == view.graphView) drag.stopDrag();
            view.modularUIPreview.setShowSelectionBox(false);
            return;
        }
        if (editing != null && (view.hierarchy.getSelectedOne().orElse(null) != editing || !com.pureneko.vjce.integration.NativePage.visible(editing))) editing = null;
        view.modularUIPreview.setShowSelectionBox(editing == null);
        if (editing != null) {
            var corners = List.of(handlePoint(editing, -1, -1), handlePoint(editing, 1, -1), handlePoint(editing, 1, 1), handlePoint(editing, -1, 1), handlePoint(editing, -1, -1));
            line(context, corners, 0xff55ff55);
            for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
                if (x == 0 && y == 0) continue;
                var p = handlePoint(editing, x, y); p = overlay.getLocalMouse(p.x, p.y);
                DrawerHelper.drawSolidRect(context.graphics, p.x - 2, p.y - 2, 4, 4, 0xff55ff55);
            }
        }
        for (var guide : guides) drawPixelGuide(context, guide[0], guide[1]);
    }
    private void drawPixelGuide(GUIContext context, Vector2f a, Vector2f b) {
        // Guide endpoints already use final GUI screen coordinates, independent of graph zoom.
        // Draw in that space: exactly one framebuffer pixel, aligned to the pixel grid.
        var graphics = context.graphics;
        float scale = (float) context.surface.guiScale(), pixel = 1 / scale;
        graphics.flush(); graphics.pose().pushPose();
        try {
            graphics.pose().last().pose().identity();
            if (Math.abs(a.x - b.x) < .01f) DrawerHelper.drawSolidRect(graphics,
                    Math.round(a.x * scale) / scale, Math.min(a.y, b.y), pixel, Math.abs(b.y - a.y), 0xffff66ff);
            else DrawerHelper.drawSolidRect(graphics, Math.min(a.x, b.x), Math.round(a.y * scale) / scale, Math.abs(b.x - a.x), pixel, 0xffff66ff);
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    private void line(GUIContext context, List<Vector2f> world, int color) {
        DrawerHelper.drawLines(context.graphics, world.stream().map(p -> overlay.getLocalMouse(p.x, p.y)).toList(), color, color, 1);
    }
}
