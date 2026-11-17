package com.pureneko.vjce.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pureneko.vjce.mixin.ShaderLightsAccess;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Create's GUI directions, converted from GUI Y-down into this scene's world/view coordinates. */
final class SceneLighting implements AutoCloseable {
    private final Vector3f previous0,previous1;
    SceneLighting() {
        var lights=ShaderLightsAccess.vjce$directions();
        previous0=lights[0];previous1=lights[1];
        var inverse=new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        RenderSystem.setShaderLights(inverse.transformDirection(direction(12.5f,-45)),inverse.transformDirection(direction(-20,-50)));
    }
    static Vector3f direction(float yaw,float pitch) {
        var light=new Vector3f(0,0,1).rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(-pitch));
        light.y=-light.y;return light;
    }
    @Override public void close(){RenderSystem.setShaderLights(previous0,previous1);}
}
