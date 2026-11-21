package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Maps original JEI drawing coordinates into the edited element, including ancestor transforms. */
public final class ElementTransform {
    private final Matrix3x2f forward, inverse;
    private final float width, height;
    public ElementTransform(UIElement root, UIElement element, float sourceWidth, float sourceHeight) {
        width = Math.max(1, sourceWidth); height = Math.max(1, sourceHeight);
        forward = new Matrix3x2f().translate(-root.getContentX(), -root.getContentY());
        var chain = new ArrayList<UIElement>();
        for (var e = element; e != null; e = e.getParent()) chain.add(e);
        for (int i = chain.size() - 1; i >= 0; i--) chain.get(i).getStyle().transform2D().pushPose(forward, chain.get(i));
        forward.translate(element.getContentX(), element.getContentY())
                .scale(Math.max(.0001f, element.getContentWidth()) / width, Math.max(.0001f, element.getContentHeight()) / height);
        inverse = new Matrix3x2f(forward).invert();
    }
    public Matrix3x2f matrix() { return new Matrix3x2f(forward); }
    public void apply(GuiGraphicsExtractor graphics) { graphics.pose().mul(forward); }
    public Vector2f local(double x, double y) { return inverse.transformPosition((float) x, (float) y, new Vector2f()); }
    public Vector2f direction(double x, double y) { return inverse.transformDirection((float) x, (float) y, new Vector2f()); }
    public Vector2f world(float x, float y) { return forward.transformPosition(x, y, new Vector2f()); }
    public boolean contains(double x, double y) { var p = local(x, y); return p.x >= 0 && p.y >= 0 && p.x < width && p.y < height; }
    public ScreenRectangle area() {
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) {
            var p = world(width * x, height * y); minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
        }
        return new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX) - (int) Math.floor(minX), (int) Math.ceil(maxY) - (int) Math.floor(minY));
    }
}
