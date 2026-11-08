package com.pureneko.vjce.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import org.joml.*;

final class SceneTransforms {
    static float scale(CompoundTag row){return row.contains("scale")?row.getFloat("scale"):1;}
    static void block(PoseStack pose,CompoundTag row){
        float scale=scale(row),yaw=row.getFloat("yaw"),pitch=row.getFloat("pitch"),roll=row.getFloat("roll");
        if(!Float.isFinite(scale)||scale<.05f||scale>16||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(roll))throw new IllegalArgumentException("Invalid scene transform");
        if(scale==1&&yaw==0&&pitch==0&&roll==0)return;
        pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.mulPose(Axis.XP.rotationDegrees(pitch));pose.mulPose(Axis.ZP.rotationDegrees(roll));pose.scale(scale,scale,scale);pose.translate(-.5,-.5,-.5);
    }
    static AABB bounds(BlockPos pos,CompoundTag row){
        var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());block(pose,row);var matrix=pose.last().pose();
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++){var p=matrix.transformPosition(new Vector3f(x,y,z));min.min(p);max.max(p);}
        return new AABB(min.x,min.y,min.z,max.x,max.y,max.z);
    }
}
