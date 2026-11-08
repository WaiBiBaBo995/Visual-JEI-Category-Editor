package com.pureneko.vjce.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.*;

/** Full-bright entity-sheet models, matching Create's GUI model path instead of terrain AO.
 * Ordinary opaque vanilla geometry is uploaded once. BER/model-data and translucent models remain live.
 */
final class SceneBlockModels implements AutoCloseable {
    private static final Direction[] FACES={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,null};
    private final SceneRenderer renderer;
    private VertexBuffer opaque;
    private List<BlockPos> dynamic;
    private Object modelsRevision;
    SceneBlockModels(SceneRenderer renderer){this.renderer=renderer;}
    void render(){
        var mc=Minecraft.getInstance();
        if(dynamic==null||modelsRevision!=mc.getModelManager().getMissingModel())build();
        if(opaque!=null){
            var type=Sheets.cutoutBlockSheet();type.setupRenderState();
            try{opaque.bind();opaque.drawWithShader(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),RenderSystem.getShader());}
            finally{VertexBuffer.unbind();type.clearRenderState();}
        }
        var buffers=mc.renderBuffers().bufferSource();
        for(var pos:dynamic)render(pos,buffers);
        buffers.endBatch();
    }
    private void build(){
        close();var mc=Minecraft.getInstance();modelsRevision=mc.getModelManager().getMissingModel();dynamic=new ArrayList<>();
        var positions=new LinkedHashSet<BlockPos>();for(var entry:renderer.renderedBlocksMap.values())positions.addAll(entry.snapshot());
        try(var bytes=new ByteBufferBuilder(262144)){
            var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            for(var pos:positions){
                var state=renderer.world.getBlockState(pos);if(state.getRenderShape()!=RenderShape.MODEL)continue;
                var model=mc.getBlockRenderer().getBlockModel(state);
                boolean cached=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")&&!state.hasBlockEntity()
                        &&!model.getRenderTypes(state,RandomSource.create(42),ModelData.EMPTY).contains(RenderType.translucent());
                if(cached)render(pos,type->builder);else dynamic.add(pos);
            }
            var mesh=builder.build();
            if(mesh!=null){opaque=new VertexBuffer(VertexBuffer.Usage.STATIC);opaque.bind();try{opaque.upload(mesh);}finally{VertexBuffer.unbind();}}
        }catch(RuntimeException failure){close();throw failure;}
    }
    private void render(BlockPos pos,MultiBufferSource buffers){
        var mc=Minecraft.getInstance();var world=renderer.world;var state=world.getBlockState(pos);
        if(state.getRenderShape()!=RenderShape.MODEL)return;
        var dispatcher=mc.getBlockRenderer();var model=dispatcher.getBlockModel(state);
        var manager=world.getModelDataManager();var initial=manager==null?ModelData.EMPTY:manager.getAt(pos);
        if(manager==null){var tile=world.getBlockEntity(pos);if(tile!=null)initial=tile.getModelData();}
        var data=model.getModelData(world,pos,state,initial==null?ModelData.EMPTY:initial);
        var pose=new PoseStack();pose.translate(pos.getX(),pos.getY(),pos.getZ());
        renderer.transform(pos,pose);
        for(var layer:model.getRenderTypes(state,RandomSource.create(42),data)){
            var type=layer==RenderType.translucent()?Sheets.translucentCullBlockSheet():Sheets.cutoutBlockSheet();
            var consumer=buffers.getBuffer(type);var random=RandomSource.create(42);
            for(var face:FACES){
                random.setSeed(42);
                for(var quad:model.getQuads(state,face,random,data,layer)){
                    // Item-style renderModel accepts one tint for every quad. World blocks may use
                    // several indices (Mekanism tank tiers use a separate frame tint).
                    int color=quad.isTinted()?mc.getBlockColors().getColor(state,world,pos,quad.getTintIndex()):-1;
                    consumer.putBulkData(pose.last(),quad,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1,
                            LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                }
            }
        }
    }
    @Override public void close(){if(opaque!=null){opaque.close();opaque=null;}dynamic=null;modelsRevision=null;}
}
