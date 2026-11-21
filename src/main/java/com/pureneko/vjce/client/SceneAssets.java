package com.pureneko.vjce.client;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.*;
import java.io.File;
import java.util.*;
final class SceneAssets {
    record Drop(SceneAssetResource type,Either<String,File> path){}
    private static long revision,lastPoll;
    static void changed(){revision++;}
    static long revision(){long now=System.nanoTime();if(now-lastPoll>500_000_000L){lastPoll=now;for(var type:List.of(SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES))if(type.loadAndUpdateStaticResource())revision++;}return revision;}
    static CompoundTag value(SceneAssetResource type,CompoundTag row){String id=row.getString("resourceId");var entries=type.allResources().toList();for(var entry:entries)if(!id.isBlank()&&id.equals(entry.getValue().getString("assetId")))return entry.getValue();String path=row.getString("resource");for(var entry:entries)if(path.equals(path(entry.getKey())))return entry.getValue();return null;}
    static String path(Either<String,File> key){return key.map(s->"builtin:"+s,f->ClientBootstrap.assets().toAbsolutePath().normalize().relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\','/'));}
    static String displayName(SceneAssetResource type,CompoundTag row){String path=row.getString("resource");return path.substring(Math.max(path.lastIndexOf('/'),path.lastIndexOf('\\'))+1);}
    static CompoundTag instance(Drop drop){var data=drop.type.getResource(drop.path);if(data==null)throw new IllegalArgumentException("Missing scene resource");var row=new CompoundTag();row.putString("resource",path(drop.path));row.putString("resourceId",data.getString("assetId"));row.putFloat("scale",1);return row;}
    static CompoundTag resolve(CompoundTag source){revision();var result=source.copy();for(var type:List.of(SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES)){String key=type.entity?"entities":"blocks";var list=new ListTag();for(var tag:BlockSceneElement.rows(source,key)){var row=(CompoundTag)tag;if(!row.contains("resource")){list.add(row.copy());continue;}var asset=value(type,row);if(asset==null){asset=type.defaults();if(type.entity)asset.putString("id","minecraft:armor_stand");else{var state=new CompoundTag();state.putString("Name","minecraft:barrier");asset.put("state",state);asset.put("data",new CompoundTag());}asset.putString("missing",displayName(type,row));}var resolved=asset.copy();for(String k:List.of("x","y","z","yaw","pitch","roll","scale","pose","resource","resourceId"))if(row.contains(k))resolved.put(k,row.get(k).copy());list.add(resolved);}result.put(key,list);}return result;}
}
