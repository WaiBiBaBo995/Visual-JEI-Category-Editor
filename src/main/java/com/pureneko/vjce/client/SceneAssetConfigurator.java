package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.io.File;
import java.util.*;
import java.util.function.*;

/** Structured resource definitions; per-instance placement remains in the scene editor. */
final class SceneAssetConfigurator implements IConfigurable {
    final SceneAssetResource resource;
    final Either<String,File> key;
    final ResourcePanel panel;
    final CompoundTag data;
    final BlockSceneElement preview=new BlockSceneElement();
    final ConfiguratorGroup properties=new ConfiguratorGroup("ldlib.gui.editor.blockstate.properties",false);
    final LabelWidget error=new LabelWidget(0,0,"").setTextColor(0xffff6666);
    SceneAssetConfigurator(SceneAssetResource resource,Either<String,File> key,ResourcePanel panel){
        this.resource=resource;this.key=key;this.panel=panel;data=resource.getResource(key).copy();preview.width=110;preview.height=95;preview.clock.playing=false;refreshPreview();
    }
    static void open(SceneAssetResource resource,Either<String,File> key,ResourcePanel panel){
        if(resource.getResource(key)!=null)panel.getEditor().getConfigPanel().openConfigurator(ConfigPanel.Tab.RESOURCE,new SceneAssetConfigurator(resource,key,panel));
    }
    private void refreshPreview(){var list=new ListTag();list.add(data.copy());preview.blocks=new CompoundTag();preview.blocks.put(resource.entity?"entities":"blocks",list);preview.release();}
    void commit(){refreshPreview();preview.ensureScene();if(!preview.error.isBlank()){error.setText(preview.error);return;}resource.addResource(key,data.copy());SceneAssets.changed();error.setText("");}
    @Override public void buildConfigurator(ConfiguratorGroup root){
        root.addConfigurators(new WrapperConfigurator("ldlib.gui.editor.group.preview",new SceneAssetResource.CanvasPreview(0,0,110,95,preview)));
        if(resource.entity)entity(root);else block(root);
        var advanced=new ConfiguratorGroup("vjce.asset.advanced",true);
        advanced.addConfigurators(new StringConfigurator("NBT",()->data.getCompound("data").toString(),s->{try{var tag=TagParser.parseTag(s);data.put("data",tag);data.remove("update");commit();}catch(Exception e){error.setText(e.getMessage());}},"{}",true));
        root.addConfigurators(advanced,new WrapperConfigurator(error));
    }
    BlockState state(){return BlockState.CODEC.parse(NbtOps.INSTANCE,data.getCompound("state")).result().orElse(Blocks.STONE.defaultBlockState());}
    void setState(BlockState state){data.put("state",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow(false,m->{}));commit();}
    private void block(ConfiguratorGroup root){
        root.addConfigurators(new BlockConfigurator("id",()->state().getBlock(),block->{boolean same=block==state().getBlock();if(!same){data.put("data",new CompoundTag());data.remove("update");}setState(block.defaultBlockState());rebuildProperties();},Blocks.STONE,true));
        rebuildProperties();root.addConfigurators(properties);
        root.addConfigurators(new ButtonConfigurator("vjce.asset.from_jei",()->IngredientPicker.openBlocks(panel,SceneBlockImports::isBlock,stack->replaceBlock(SceneBlockImports.snapshot(stack,Minecraft.getInstance().level.registryAccess())))),new ButtonConfigurator("vjce.asset.from_inventory",()->SceneBlockImports.fromInventory(panel,this::replaceBlock)));
    }
    private void replaceBlock(CompoundTag replacement){data.put("state",replacement.getCompound("state").copy());data.put("data",replacement.getCompound("data").copy());data.remove("update");commit();rebuildProperties();}
    private void rebuildProperties(){properties.removeAllConfigurators();for(var property:state().getProperties())property(property);properties.computeLayout();}
    private <T extends Comparable<T>> void property(Property<T> property){properties.addConfigurators(new SelectorConfigurator<T>(property.getName(),()->state().getValue(property),value->setState(state().setValue(property,value)),state().getValue(property),true,List.copyOf(property.getPossibleValues()),property::getName));}
    private EntityType<?> type(){var id=net.minecraft.resources.ResourceLocation.tryParse(data.getString("id"));return id==null?EntityType.PIG:BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.PIG);}
    private void entity(ConfiguratorGroup root){
        root.addConfigurators(new EntityTypeConfigurator("id",this::type,type->{var defaults=SceneEntities.defaults(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());data.putString("id",defaults.getString("id"));data.put("data",defaults.getCompound("data"));data.putString("pose",Pose.STANDING.name());commit();}));
        root.addConfigurators(new SelectorConfigurator<Pose>("vjce.field.pose",()->pose(),value->{data.putString("pose",value.name());commit();},pose(),true,List.of(Pose.values()),p->Forms.enumLabel(p)));
    }
    private Pose pose(){try{return Pose.valueOf(data.getString("pose"));}catch(IllegalArgumentException ignored){return Pose.STANDING;}}
    static final class EntityTypeConfigurator extends ValueConfigurator<EntityType<?>> implements SearchComponentWidget.IWidgetSearch<EntityType<?>> {
        private SearchComponentWidget<EntityType<?>> search;
        EntityTypeConfigurator(String name,Supplier<EntityType<?>> value,Consumer<EntityType<?>> update){super(name,value,update,EntityType.PIG,true);}
        @Override public void init(int width){super.init(width);addWidget(new ImageWidget(leftWidth,2,width-leftWidth-rightWidth-3,10,new ColorRectTexture(Forms.INPUT).setRadius(5)));search=new SearchComponentWidget<>(leftWidth+3,2,width-leftWidth-rightWidth-6,10,this);search.setShowUp(true);search.setCapacity(5);search.textFieldWidget.setBordered(false);search.setCurrent(value);addWidget(search);}
        @Override protected void onValueUpdate(EntityType<?> type){if(type==null||type==value)return;super.onValueUpdate(type);if(search!=null)search.setCurrent(type);}
        @Override public String resultDisplay(EntityType<?> type){return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();}
        @Override public void selectResult(EntityType<?> type){onValueUpdate(type);updateValue();}
        @Override public void search(String word,Consumer<EntityType<?>> found){String query=word.toLowerCase(Locale.ROOT);for(var type:BuiltInRegistries.ENTITY_TYPE){if(Thread.currentThread().isInterrupted())return;if(type==EntityType.PLAYER)continue;if(resultDisplay(type).contains(query)||Component.translatable(type.getDescriptionId()).getString().toLowerCase(Locale.ROOT).contains(query))found.accept(type);}}
    }
}
