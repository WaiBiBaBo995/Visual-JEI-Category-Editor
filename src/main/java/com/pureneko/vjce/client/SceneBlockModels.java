package com.pureneko.vjce.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import java.util.*;
/** Item-style lighting with each quad's native tint, model data and each block's transform. */
final class SceneBlockModels implements AutoCloseable {
    private final SceneRenderer renderer;
    SceneBlockModels(SceneRenderer renderer){this.renderer=renderer;}
    void render(){
        var mc=Minecraft.getInstance();var world=renderer.world;var buffers=mc.renderBuffers().bufferSource();
        var positions=new LinkedHashSet<BlockPos>();renderer.renderedBlocksMap.keySet().forEach(positions::addAll);
        for(var pos:positions){var state=world.getBlockState(pos);
            var fluid=state.getFluidState();
            if(!fluid.isEmpty()){
                var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());renderer.transform(pos,pose);
                var consumer=buffers.getBuffer(ItemBlockRenderTypes.getRenderLayer(fluid));
                var matrix=pose.last().pose();var normal=pose.last().normal();
                var wrapper=new VertexConsumer(){
                    public VertexConsumer vertex(double x,double y,double z){var p=matrix.transformPosition(new org.joml.Vector3f((float)x-(pos.getX()&15),(float)y-(pos.getY()&15),(float)z-(pos.getZ()&15)));consumer.vertex(p.x,p.y,p.z);return this;}
                    public VertexConsumer color(int r,int g,int b,int a){consumer.color(r,g,b,a);return this;}
                    public VertexConsumer uv(float u,float v){consumer.uv(u,v);return this;}
                    public VertexConsumer overlayCoords(int u,int v){consumer.overlayCoords(u,v);return this;}
                    public VertexConsumer uv2(int u,int v){consumer.uv2(LightTexture.FULL_BRIGHT);return this;}
                    public VertexConsumer normal(float x,float y,float z){var n=normal.transform(new org.joml.Vector3f(x,y,z));consumer.normal(n.x,n.y,n.z);return this;}
                    public void endVertex(){consumer.endVertex();}
                    public void defaultColor(int r,int g,int b,int a){consumer.defaultColor(r,g,b,a);}
                    public void unsetDefaultColor(){consumer.unsetDefaultColor();}
                };
                mc.getBlockRenderer().renderLiquid(pos,world,wrapper,state,fluid);
            }
            if(state.getRenderShape()!=RenderShape.MODEL)continue;
            var model=mc.getBlockRenderer().getBlockModel(state);
            var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());renderer.transform(pos,pose);
            {
                var layer = ItemBlockRenderTypes.getChunkRenderType(state);
                var target=layer==RenderType.translucent()?Sheets.translucentCullBlockSheet():Sheets.cutoutBlockSheet();var consumer=buffers.getBuffer(target);
                for(int face=0;face<7;face++)for(var quad:model.getQuads(state,face==6?null:Direction.values()[face],RandomSource.create(42))){
                    int color=quad.isTinted()?mc.getBlockColors().getColor(state,world,pos,quad.getTintIndex()):-1;
                    consumer.putBulkData(pose.last(),quad,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                }
            }
        }
        buffers.endBatch();
    }
    @Override public void close(){}
}
