package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.gui.texture.*;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Native LDLib global resource providers: create, inspect, rename, duplicate and drag. */
final class SceneAssetResource extends Resource<CompoundTag> {
    static final SceneAssetResource BLOCKS=new SceneAssetResource(false),ENTITIES=new SceneAssetResource(true);
    final boolean entity;
    private SceneAssetResource(boolean entity){this.entity=entity;setDefaultUIWidth(45);}
    @Override public String getName(){return entity?"vjce_entity":"vjce_block";}
    @Override public IGuiTexture getIcon(){return Icons.MODEL;}
    @Override public Tag serializeResource(CompoundTag value,HolderLookup.Provider provider){return value.copy();}
    @Override public CompoundTag deserializeResource(Tag tag,HolderLookup.Provider provider){return tag instanceof CompoundTag data?data.copy():null;}
    CompoundTag defaults(){
        CompoundTag value;
        if(entity)value=SceneEntities.defaults("minecraft:pig");
        else{value=new CompoundTag();var state=new CompoundTag();state.putString("Name","minecraft:stone");value.put("state",state);value.put("data",new CompoundTag());}
        value.putString("assetId",UUID.randomUUID().toString());return value;
    }
    @Override public ResourceProviderContainer<CompoundTag> createResourceProviderContainer(IResourceProvider<CompoundTag> provider){
        var type=this;
        var inspectedPath=new java.util.concurrent.atomic.AtomicReference<IResourcePath>();
        var container=new ResourceProviderContainer<CompoundTag>(provider){
            @Override protected UIElement createResourceUI(IResourcePath path){return super.createResourceUI(path).setId(cellId(path));}
            @Override protected void onResourceMoved(IResourcePath from,IResourcePath to){if(from.equals(inspectedPath.get()))inspectedPath.set(to);SceneAssets.moved(type,from,to);}
            @Override protected CompoundTag detachedCopy(IResourcePath key){var copy=super.detachedCopy(key);if(copy!=null)copy.putString("assetId",UUID.randomUUID().toString());return copy;}
        };
        if (entity) container.setAddDefault(this::defaults);
        else if (provider.supportAdd()) {
            container.setOnCreateMenu((c, menu) -> menu.branch(Icons.ADD_FILE, "ldlib.gui.editor.menu.add_resource", add -> {
                add.leaf("vjce.asset.default_block", () -> c.addNewResource(defaults()));
                add.leaf("vjce.asset.from_jei", () -> SceneBlockImports.fromJei(c));
                add.leaf("vjce.asset.from_inventory", () -> SceneBlockImports.fromInventory(c));
            }));
        }
        container.setOnDragProvider(path->{var value=provider.getResource(path);if(value!=null)SceneAssets.remember(type,path,value);return new SceneAssets.Drop(type,path);});
        container.setUiSupplier(path->{
            var value=provider.getResource(path);if(value==null)return new UIElement();
            var scene=new BlockSceneElement();var data=new CompoundTag();var rows=new ListTag();var row=value.copy();row.putInt("x",0);row.putInt("y",0);row.putInt("z",0);rows.add(row);
            if (entity) row.putFloat("yaw", SceneEntities.frontQuarterYaw(scene.cameraYaw));
            data.put(entity?"entities":"blocks",rows);scene.blocks=data;scene.clock.playing=false;scene.animateBlockEntities=false;
            scene.layout(l->{l.widthPercent(100);l.heightPercent(100);});return scene;
        });
        container.setOnEdit((c,path)->{
            inspectedPath.set(path);
            var value=provider.getResource(path);if(value==null)return;
            var edit=new StructuredTagEditor().setCompoundTagOnly();edit.setId("vjce_asset_data");
            var shown=value.copy();shown.remove("assetId");for(String key:List.of("x","y","z","yaw","pitch","roll","scale"))shown.remove(key);
            edit.setValue(shown,false);edit.layout(l->{l.widthPercent(100);l.height(270);});var error=new Label();
            var choose=new SearchComponent<String>(new SearchComponent.ISearchUI<>(){
                public String resultText(String id){return id;}
                public void onResultSelected(String id){var data=((CompoundTag)edit.getValue()).copy();if(entity)data.putString("id",id);else{var state=new CompoundTag();state.putString("Name",id);data.put("state",state);}data.put("data",new CompoundTag());data.remove("update");edit.setValue(data,true);}
                public void search(String query,com.lowdragmc.lowdraglib2.utils.search.IResultHandler<String> out){
                    var ids=entity?BuiltInRegistries.ENTITY_TYPE.keySet():BuiltInRegistries.BLOCK.keySet();
                    for(var id:ids)if(!id.toString().equals("minecraft:air")&&!id.toString().equals("minecraft:player")&&id.toString().contains(query.toLowerCase(Locale.ROOT)))out.accept(id.toString());
                }
            });choose.setId("vjce_asset_search");choose.layout(l->{l.widthPercent(100);l.height(20);l.flexShrink(0);});
            edit.setTagResponder(tag->{
                try{var data=((CompoundTag)tag).copy();validate(data);data.putString("assetId",value.getString("assetId"));
                    provider.addResource(inspectedPath.get(),data);SceneAssets.changed();c.reloadSpecificResource(inspectedPath.get());error.setText("");
                }catch(RuntimeException invalid){error.setText(invalid.getMessage());}
            });
            var form=new UIElement().layout(l->l.widthPercent(100)).addChildren(new Label().setText("vjce.asset.help"),choose,edit,error);
            c.getEditor().inspectorView.inspect(IConfigurable.create(group->{var config=new Configurator(getDisplayName().getString());config.addChild(form);group.addConfigurator(config);}));
        });
        return container;
    }
    String cellId(IResourcePath path){return "vjce_asset_"+getName()+"_"+path.getPathWithType().hashCode();}
    private void validate(CompoundTag data){
        if(entity){var id=ResourceLocation.tryParse(data.getString("id"));if(id==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id)||id.toString().equals("minecraft:player"))throw new IllegalArgumentException("Invalid entity ID");}
        else if(BlockState.CODEC.parse(NbtOps.INSTANCE,data.getCompound("state")).getOrThrow().isAir())throw new IllegalArgumentException("Scene block cannot be air");
    }
}
