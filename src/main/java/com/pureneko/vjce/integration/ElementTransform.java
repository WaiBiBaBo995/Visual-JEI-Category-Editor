package com.pureneko.vjce.integration;

import com.pureneko.vjce.client.canvas.CanvasElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Maps original JEI drawing coordinates into the edited element, including ancestor transforms. */
public final class ElementTransform {
    private final Matrix4f forward, inverse;
    private final float width, height;
    public ElementTransform(CanvasElement root, CanvasElement element, float sourceWidth, float sourceHeight) {
        width = Math.max(1, sourceWidth); height = Math.max(1, sourceHeight);
        forward = new Matrix4f(root.worldMatrix()).invert().mul(element.worldMatrix())
                .scale(Math.max(.0001f,element.width)/width,Math.max(.0001f,element.height)/height,1);
        inverse = new Matrix4f(forward).invert();
    }
    public Matrix4f matrix() { return new Matrix4f(forward); }
    public void apply(GuiGraphics graphics) { graphics.pose().mulPoseMatrix(forward); }
    public Vector2f local(double x, double y) { var p = inverse.transformPosition(new Vector3f((float) x, (float) y, 0)); return new Vector2f(p.x, p.y); }
    public Vector2f direction(double x, double y) { var p = inverse.transformDirection(new Vector3f((float) x, (float) y, 0)); return new Vector2f(p.x, p.y); }
    public Vector2f world(float x, float y) { var p = forward.transformPosition(new Vector3f(x, y, 0)); return new Vector2f(p.x, p.y); }
    public boolean contains(double x, double y) { var p = local(x, y); return p.x >= 0 && p.y >= 0 && p.x < width && p.y < height; }
    public ScreenRectangle area() {
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
        for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) {
            var p = world(width * x, height * y); minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
        }
        return new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX) - (int) Math.floor(minX), (int) Math.ceil(maxY) - (int) Math.floor(minY));
    }
}
