package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.ProjectStore;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

/** Resource identity survives editor renames, including projects that are not currently open. */
final class SceneAssets {
    record Drop(SceneAssetResource type,IResourcePath path) {}
    private static final Map<String,String> paths=new LinkedHashMap<>();
    private static boolean loaded;
    private static long revision,lastPoll;
    private static Path file(){return LDLib2.getAssetsDir().toPath().resolve("vjce/scene-paths.json");}
    private static void load(){
        if(loaded)return;loaded=true;
        try{if(Files.exists(file()))ProjectStore.JSON.fromJson(Files.readString(file()),com.google.gson.JsonObject.class).entrySet().forEach(e->paths.put(e.getKey(),e.getValue().getAsString()));}
        catch(Exception e){VisualJeiCategoryEditor.LOG.warn("Cannot read scene resource paths",e);}
    }
    private static void save(){try{Files.createDirectories(file().getParent());Files.writeString(file(),ProjectStore.JSON.toJson(paths));}catch(Exception e){VisualJeiCategoryEditor.LOG.warn("Cannot save scene resource paths",e);}}
    static void changed(){revision++;}
    static long revision(){
        long now=System.nanoTime();if(now-lastPoll>500_000_000L){lastPoll=now;
            for(var type:List.of(SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES))
                for(var provider:type.getResourceInstance().listWritableProviders())if(provider.checkAndUpdateResourceProvider())revision++;
        }return revision;
    }
    static void remember(SceneAssetResource type,IResourcePath path,CompoundTag value){
        load();String id=value.getString("assetId");if(id.isBlank())return;
        var key=type.getName()+":"+id;var next=path.getPathWithType();
        if(!next.equals(paths.put(key,next)))save();
    }
    static void moved(SceneAssetResource type,IResourcePath from,IResourcePath to){
        load();String prefix=type.getName()+":";
        paths.replaceAll((key,path)->key.startsWith(prefix)&&path.equals(from.getPathWithType())?to.getPathWithType():path);
        var value=type.getResourceInstance().getResource(to);if(value!=null)remember(type,to,value);
        save();changed();
    }
    static CompoundTag value(SceneAssetResource type,CompoundTag row){
        load();var instance=type.getResourceInstance();String id=row.getString("resourceId");
        String path=paths.getOrDefault(type.getName()+":"+id,row.getString("resource"));
        var value=instance.getResource(IResourcePath.parse(path));
        if(value==null&&!id.isBlank())for(var entry:instance.listAllResources())if(id.equals(entry.getValue().getString("assetId"))){remember(type,entry.getKey(),entry.getValue());return entry.getValue();}
        return value;
    }
    static String displayName(SceneAssetResource type,CompoundTag row){
        load();var path=IResourcePath.parse(paths.getOrDefault(type.getName()+":"+row.getString("resourceId"),row.getString("resource")));
        if(path==null)return row.getString("resource");String name=path.getResourceName();return name.endsWith("."+type.getName())?name.substring(0,name.length()-type.getName().length()-1):name;
    }
    static CompoundTag instance(Drop drop){
        var value=drop.type.getResourceInstance().getResource(drop.path);if(value==null)throw new IllegalArgumentException("Missing resource: "+drop.path.getResourceName());
        remember(drop.type,drop.path,value);var row=new CompoundTag();
        row.putString("resource",drop.path.getPathWithType());row.putString("resourceId",value.getString("assetId"));
        row.putDouble("x",0);row.putDouble("y",0);row.putDouble("z",0);
        row.putFloat("yaw",0);row.putFloat("pitch",0);row.putFloat("roll",0);row.putFloat("scale",1);
        return row;
    }
    static CompoundTag resolve(CompoundTag original){
        if(original==null)return null;
        boolean referenced=false;for(String key:List.of("blocks","entities"))for(var tag:BlockSceneElement.rows(original,key))if(((CompoundTag)tag).contains("resource")){referenced=true;break;}
        if(!referenced)return original;
        revision();
        var result=original.copy();
        for(var type:List.of(SceneAssetResource.BLOCKS,SceneAssetResource.ENTITIES)){
            var resolved=new ListTag();
            for(var tag:BlockSceneElement.rows(original,type.entity?"entities":"blocks")){
                var row=(CompoundTag)tag;if(!row.contains("resource")){resolved.add(row.copy());continue;}
                var value=value(type,row);CompoundTag effective;
                if(value==null){
                    effective=type.entity?SceneEntities.defaults("minecraft:block_display"):new CompoundTag();
                    var state=new CompoundTag();state.putString("Name","minecraft:red_stained_glass");
                    if(type.entity)effective.getCompound("data").put("block_state",state);else{effective.put("state",state);effective.put("data",new CompoundTag());}
                    effective.putString("missing",displayName(type,row));
                }else effective=value.copy();
                for(String key:List.of("x","y","z","yaw","pitch","roll","scale","pose","resource","resourceId"))if(row.contains(key))effective.put(key,row.get(key).copy());
                resolved.add(effective);
            }
            result.put(type.entity?"entities":"blocks",resolved);
        }
        return result;
    }
}
