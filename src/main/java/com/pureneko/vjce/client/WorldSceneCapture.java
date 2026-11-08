package com.pureneko.vjce.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;
import java.util.*;

/** Read-only client snapshots. A failed capture never mutates the editor's working copy. */
final class WorldSceneCapture {
    static final int MAX_AXIS = 32;
    record Selection(BlockPos min, BlockPos max) {
        static Selection of(BlockPos a, BlockPos b) {
            return new Selection(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),
                    new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ())));
        }
        AABB bounds() { return new AABB(min.getX(),min.getY(),min.getZ(),(double)max.getX()+1,(double)max.getY()+1,(double)max.getZ()+1); }
        void validate() {
            if ((long)max.getX()-min.getX()+1 > MAX_AXIS || (long)max.getY()-min.getY()+1 > MAX_AXIS || (long)max.getZ()-min.getZ()+1 > MAX_AXIS)
                throw error("vjce.world.too_large", MAX_AXIS);
        }
    }
    static CompoundTag capture(ClientLevel level, Selection selection, boolean entities, CompoundTag previous) {
        selection.validate();
        var rows = new ListTag();
        for (var pos : BlockPos.betweenClosed(selection.min,selection.max)) {
            if (level.isOutsideBuildHeight(pos) || !loaded(level,pos)) throw error("vjce.world.unloaded",pos.toShortString());
            var state = level.getBlockState(pos); if (state.isAir()) continue;
            if (rows.size() == BlockSceneElement.MAX_BLOCKS) throw error("vjce.world.too_many_blocks",BlockSceneElement.MAX_BLOCKS);
            var row = new CompoundTag(); var local = pos.subtract(selection.min);
            row.putInt("x",local.getX());row.putInt("y",local.getY());row.putInt("z",local.getZ());
            row.put("state",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow());
            var blockEntity=level.getBlockEntity(pos);
            row.put("data",blockEntity==null?new CompoundTag():blockEntity.saveWithoutMetadata(level.registryAccess()).copy());
            if(blockEntity!=null)row.put("update",blockEntity.getUpdateTag(level.registryAccess()).copy());
            rows.add(row);
        }
        var result = previous.copy(); result.put("blocks",rows);
        var captured=new ListTag();
        if (entities) {
            var selected = selectedEntities(level,selection);
            if (selected.size()>BlockSceneElement.MAX_ENTITIES) throw error("vjce.world.too_many_entities",BlockSceneElement.MAX_ENTITIES);
            for(var entity:selected)captured.add(entity(entity,selection.min));
        }
        result.put("entities",captured);
        if (rows.isEmpty()&&BlockSceneElement.rows(result,"entities").isEmpty()) throw error("vjce.world.empty");
        return result;
    }
    static CompoundTag entity(Entity source, BlockPos origin) {
        var row=SceneEntities.defaults(BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString());
        row.putDouble("x",source.getX()-origin.getX());row.putDouble("y",source.getY()-origin.getY());row.putDouble("z",source.getZ()-origin.getZ());
        row.putFloat("yaw",source.getYRot());row.putFloat("pitch",source.getXRot());row.putString("pose",source.getPose().name());
        row.putFloat("scale",source instanceof LivingEntity living?living.getScale():1);
        var data=source.saveWithoutId(new CompoundTag()).copy();
        for(String key:List.of("id","UUID","UUIDMost","UUIDLeast","Pos","Motion","Rotation","Passengers","RootVehicle"))data.remove(key);
        translate(data,"TileX","TileY","TileZ",origin);translate(data,"SleepingX","SleepingY","SleepingZ",origin);
        row.put("data",data);return row;
    }
    static List<Entity> selectedEntities(ClientLevel level,Selection selection){
        var selected=new ArrayList<Entity>();var box=selection.bounds();
        level.getEntities(EntityTypeTest.forClass(Entity.class),box,e->!(e instanceof Player)&&!(e instanceof PartEntity<?>)&&!e.isRemoved()&&box.contains(e.position()),selected,BlockSceneElement.MAX_ENTITIES+1);
        return selected;
    }
    private static void translate(CompoundTag tag,String x,String y,String z,BlockPos origin) {
        if(tag.contains(x,Tag.TAG_ANY_NUMERIC)&&tag.contains(y,Tag.TAG_ANY_NUMERIC)&&tag.contains(z,Tag.TAG_ANY_NUMERIC)){
            tag.putInt(x,tag.getInt(x)-origin.getX());tag.putInt(y,tag.getInt(y)-origin.getY());tag.putInt(z,tag.getInt(z)-origin.getZ());
        }
    }
    static boolean loaded(ClientLevel level,BlockPos pos){
        // ClientLevel.hasChunk/hasChunkAt returns true even for the empty fallback chunk.
        return level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null;
    }
    static IllegalArgumentException error(String key,Object...args){return new IllegalArgumentException(Component.translatable(key,args).getString());}
}
